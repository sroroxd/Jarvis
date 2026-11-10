// USB-Ersatz für den Handy-Agenten. Keine API-Schlüssel und kein Netzwerk-Listener.
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { timingSafeEqual } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { StringDecoder } from 'node:string_decoder';

const gleich = (a, b) => typeof a === 'string' && typeof b === 'string'
  && Buffer.byteLength(a) === Buffer.byteLength(b)
  && timingSafeEqual(Buffer.from(a), Buffer.from(b));

export function codexAntwort(text, { cwd, binary = 'codex', timeout = 120000, signal, spawnProcess = spawn } = {}) {
  return new Promise((done) => {
    const args = ['exec', '--ignore-user-config', '--ephemeral', '--skip-git-repo-check',
      '--sandbox', 'read-only', '--json', '-c', 'model_reasoning_effort=low', '-'];
    const env = { ...process.env };
    // Dieser Ersatzweg soll ausschließlich die bestätigte ChatGPT-Anmeldung nutzen.
    delete env.CODEX_API_KEY;
    delete env.OPENAI_API_KEY;
    const child = spawnProcess(binary, args, { cwd, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    const decoder = new StringDecoder('utf8');
    let buffer = '', answer = '', completed = false, error = '', size = 0, ended = false;
    const stop = () => {
      if (child.pid && process.platform === 'win32') {
        spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
      } else child.kill('SIGTERM');
    };
    const finish = (result) => {
      if (ended) return;
      ended = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      done(result);
    };
    const abort = () => { stop(); finish({ fehler: 'Auftrag abgebrochen.' }); };
    const timer = setTimeout(() => { stop(); finish({ fehler: 'Der PC-Agent hat das Zeitlimit erreicht.' }); }, timeout);
    function line(s) {
      let event;
      try { event = JSON.parse(s); } catch { return; }
      if (event.type === 'item.completed' && event.item?.type === 'agent_message') answer = event.item.text || '';
      if (event.type === 'turn.completed') completed = true;
      if (event.type === 'turn.failed' || event.type === 'error') error = 'Codex konnte die Anfrage nicht abschließen. Anmeldung oder Nutzungslimit am PC prüfen.';
    }
    child.stdout.on('data', data => {
      size += data.length;
      if (size > 2 * 1024 * 1024) { stop(); finish({ fehler: 'Antwort des PC-Agenten zu groß.' }); return; }
      buffer += decoder.write(data);
      let i;
      while ((i = buffer.indexOf('\n')) >= 0) { line(buffer.slice(0, i)); buffer = buffer.slice(i + 1); }
    });
    // Provider-/CLI-Fehler nicht ungefiltert an die App weiterreichen.
    child.stderr.resume();
    child.on('error', () => finish({ fehler: 'Codex lässt sich auf dem PC nicht starten.' }));
    child.on('close', code => {
      buffer += decoder.end();
      if (buffer.trim()) line(buffer);
      finish(code === 0 && completed && answer.trim() && !error
        ? { antwort: answer.trim(), modus: 'PC über USB, ChatGPT-Anmeldung, nur lesend' }
        : { fehler: error || 'Codex hat keine vollständige Antwort geliefert.' });
    });
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) { abort(); return; }
    child.stdin.on('error', () => {});
    child.stdin.end('Du bist Jarvis, Arts Assistent. Antworte auf Deutsch, knapp und ehrlich. '
      + 'Du läufst auf dem PC, verbunden mit der Handy-App über USB. Du kannst hier nur lesen. '
      + 'Behaupte keine ausgeführten Änderungen oder uneingeschränkten Handy-Zugriff. '
      + 'Benutze für reine Gesprächsfragen keine Werkzeuge.\n\nAuftrag von Art:\n' + text);
  });
}

export function agentServer({ nonce, execute = codexAntwort, cwd } = {}) {
  if (!nonce || nonce.length < 32) throw new Error('Einmalige lokale Einrichtung fehlt.');
  let token = '', busy = false;
  return createServer(async (req, res) => {
    const send = (status, object) => {
      if (!res.destroyed) res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }).end(JSON.stringify(object));
    };
    if (req.headers.origin) { send(403, { fehler: 'Browser-Zugriff gesperrt.' }); return; }
    if (req.method === 'GET' && req.url === '/status') {
      send(200, { bereit: !!token, version: 'pc-codex-1', beschaeftigt: busy, modus: 'PC über USB, nur lesend' }); return;
    }
    const bootstrap = req.url === '/token' && !token && gleich(req.headers.authorization, 'Bearer ' + nonce);
    if (!bootstrap && !gleich(req.headers.authorization, token ? 'Bearer ' + token : undefined)) {
      send(401, { fehler: 'Kopplung nicht bestätigt.' }); return;
    }
    if (req.method === 'GET' && req.url === '/fortschritt') {
      send(200, { beschaeftigt: busy, fortschritt: busy ? 'Der PC-Agent formuliert die Antwort.' : '' }); return;
    }
    if (req.method !== 'POST' || !['/token', '/auftrag', '/neu'].includes(req.url)) {
      send(404, { fehler: 'Unbekannter Aufruf.' }); return;
    }
    let raw = '', bytes = 0;
    try {
      for await (const part of req) {
        bytes += part.length;
        if (bytes > 65536) { send(413, { fehler: 'Auftrag zu groß.' }); return; }
        raw += part;
      }
      const body = JSON.parse(raw);
      if (req.url === '/token') {
        if (!bootstrap || typeof body.token !== 'string' || body.token.length < 24 || body.token.length > 512) {
          send(400, { fehler: 'Ungültige Kopplung.' }); return;
        }
        token = body.token;
        send(200, { gekoppelt: true }); return;
      }
      if (req.url === '/neu') { send(200, { bereit: true }); return; }
      if (busy) { send(409, { fehler: 'Der PC-Agent bearbeitet bereits einen Auftrag.' }); return; }
      if (typeof body.text !== 'string' || !body.text.trim()) { send(400, { fehler: 'Auftrag fehlt.' }); return; }
      busy = true;
      const controller = new AbortController();
      const closed = () => controller.abort();
      res.once('close', closed);
      try {
        const result = await execute(body.text.trim(), { cwd, signal: controller.signal });
        res.removeListener('close', closed);
        send(result.fehler ? 502 : 200, result);
      } finally { busy = false; res.removeListener('close', closed); }
    } catch { send(400, { fehler: 'Ungültige Anfrage oder unterbrochene Verbindung.' }); }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = agentServer({ nonce: process.env.JARVIS_PC_SETUP_NONCE, cwd: process.env.JARVIS_PC_WORKDIR });
  server.listen(18777, '127.0.0.1', () => console.log('Jarvis-PC-Agent: nur 127.0.0.1:18777, wartet auf USB-Kopplung.'));
}
