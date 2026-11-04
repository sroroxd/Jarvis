#!/usr/bin/env node
/**
 * Jarvis-Agent – das Rückgrat für lange Aufgaben.
 *
 * Läuft auf dem Handy in Termux (Ubuntu über proot-distro) und nimmt Aufträge
 * von der Jarvis-App an. Jeder Auftrag geht an Claude Code im Nicht-interaktiven
 * Modus („claude -p“), das Ergebnis kommt als Text zurück und wird in der App
 * vorgelesen.
 *
 * Warum so: Claude Code bringt Dateien, Suche, Befehle und Skill-Ordner schon
 * mit. Die App muss diese Fähigkeiten dadurch nicht einzeln nachbauen – und die
 * Aufträge zählen gegen Arts Abo statt gegen das API-Guthaben.
 *
 * Erreichbar NUR über 127.0.0.1 (niemals aus dem Netz) und nur mit dem Token aus
 * ~/.jarvis-agent/token. Auf Android darf jede App auf 127.0.0.1 verbinden,
 * deshalb ist das Token kein Zierrat.
 *
 * Start: node jarvis-agent.mjs      (oder ./starten.sh aus demselben Ordner)
 */

import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { randomBytes, timingSafeEqual } from 'node:crypto';

const VERSION = '1.0.0';
const HOST = '127.0.0.1';
const PORT = Number(process.env.JARVIS_AGENT_PORT || 8777);

/** Eigener Ordner: Token, Sitzungs-Merker und der Arbeitsordner des Agenten. */
const BASIS = process.env.JARVIS_AGENT_HOME || join(homedir(), '.jarvis-agent');
const TOKEN_DATEI = join(BASIS, 'token');
const ARBEIT = process.env.JARVIS_AGENT_WORKDIR || join(homedir(), 'jarvis-arbeit');

/** Höchstdauer eines Auftrags. Danach wird Claude Code beendet. */
const ZEITLIMIT_MS = Number(process.env.JARVIS_AGENT_TIMEOUT_MS || 10 * 60 * 1000);
/** Größte erlaubte Anfrage – bremst versehentliche Dauerschleifen in der App. */
const MAX_BYTES = 64 * 1024;

const SYSTEM = [
  'Du bist Jarvis, der Assistent von Art, und arbeitest gerade als Agent auf seinem Android-Handy.',
  'Antworte auf Deutsch. Deine Antwort wird vorgelesen: kein Markdown, keine Tabellen,',
  'keine Aufzählungszeichen, keine Code-Blöcke – gesprochene Sätze.',
  'Fasse das Ergebnis in höchstens vier Sätzen zusammen. Hast du etwas angelegt oder',
  'geändert, sag in einem Satz was und wo. Konntest du etwas nicht erledigen, sag es',
  'klar und nenne den Grund, statt es zu beschönigen.',
  'Sprich Art mit „Sir“ an, aber höchstens einmal pro Antwort.',
].join(' ');

// ----------------------------------------------------------------- Token

function tokenLaden() {
  if (!existsSync(BASIS)) mkdirSync(BASIS, { recursive: true, mode: 0o700 });
  if (process.env.JARVIS_AGENT_TOKEN) return process.env.JARVIS_AGENT_TOKEN.trim();
  if (existsSync(TOKEN_DATEI)) {
    const t = readFileSync(TOKEN_DATEI, 'utf8').trim();
    if (t) return t;
  }
  const neu = randomBytes(24).toString('base64url');
  writeFileSync(TOKEN_DATEI, neu + '\n', { mode: 0o600 });
  return neu;
}

const TOKEN = tokenLaden();

/** Vergleich ohne Zeitunterschied – kein Rückschluss auf das Token aus der Antwortzeit. */
function tokenStimmt(wert) {
  if (typeof wert !== 'string') return false;
  const a = Buffer.from(wert);
  const b = Buffer.from(TOKEN);
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

// ----------------------------------------------------------------- Zustand

/** Sitzung von Claude Code, damit Rückfragen den Zusammenhang behalten. */
let sitzung = null;
let laufend = null; // { kind, beendet }

// ----------------------------------------------------------------- Claude Code

/**
 * Führt einen Auftrag aus. Der Text geht über die Standardeingabe, nicht als
 * Argument: so gibt es keine Längen- und keine Anführungszeichen-Probleme.
 *
 * Kein „--bare“: dieser Modus liest ausdrücklich keine OAuth-Anmeldung und würde
 * einen API-Schlüssel verlangen – also Guthaben statt Abo. Genau das wollen wir
 * hier nicht.
 */
function auftragAusfuehren(text, neu) {
  return new Promise((fertig) => {
    const args = ['-p', '--output-format', 'json', '--permission-mode', 'bypassPermissions',
      '--append-system-prompt', SYSTEM, '--add-dir', ARBEIT];
    if (!neu && sitzung) args.push('--resume', sitzung);

    const kind = spawn('claude', args, {
      cwd: ARBEIT,
      stdio: ['pipe', 'pipe', 'pipe'],
      env: { ...process.env, CI: '1' },
    });
    laufend = { kind, beendet: false };

    let aus = '';
    let fehlerText = '';
    const uhr = setTimeout(() => {
      laufend.beendet = true;
      kind.kill('SIGTERM');
      setTimeout(() => kind.kill('SIGKILL'), 3000);
    }, ZEITLIMIT_MS);

    kind.stdout.on('data', (d) => { aus += d; });
    kind.stderr.on('data', (d) => { fehlerText += d; });
    kind.on('error', (e) => {
      clearTimeout(uhr);
      laufend = null;
      fertig({ fehler: 'Claude Code lässt sich nicht starten: ' + e.message });
    });
    kind.on('close', (code) => {
      clearTimeout(uhr);
      const abgebrochen = laufend && laufend.beendet;
      laufend = null;
      if (abgebrochen) {
        fertig({ fehler: 'Der Auftrag hat zu lange gedauert und wurde abgebrochen.' });
        return;
      }
      fertig(antwortZerlegen(aus, fehlerText, code));
    });

    kind.stdin.end(text, 'utf8');
  });
}

/**
 * Zerlegt die JSON-Zeile von „claude -p --output-format json“.
 * Felder geprüft gegen Claude Code 2.1.318: result, session_id, total_cost_usd,
 * duration_ms, is_error, num_turns.
 */
function antwortZerlegen(aus, fehlerText, code) {
  const zeile = aus.trim().split('\n').filter((z) => z.trim().startsWith('{')).pop();
  if (!zeile) {
    const grund = (fehlerText || '').trim().split('\n').pop() || ('Beendet mit Code ' + code);
    return { fehler: 'Der Agent hat nichts zurückgegeben. ' + grund };
  }
  let j;
  try {
    j = JSON.parse(zeile);
  } catch {
    return { fehler: 'Die Antwort des Agenten war unlesbar.' };
  }
  const text = typeof j.result === 'string' ? j.result.trim() : '';
  if (j.session_id) sitzung = j.session_id;
  if (j.is_error) {
    return { fehler: text || 'Der Auftrag ist fehlgeschlagen.' };
  }
  if (!text) return { fehler: 'Der Agent hat keine Antwort formuliert.' };
  return {
    antwort: text,
    sitzung: j.session_id || null,
    kosten: typeof j.total_cost_usd === 'number' ? j.total_cost_usd : 0,
    dauer: typeof j.duration_ms === 'number' ? j.duration_ms : 0,
    schritte: typeof j.num_turns === 'number' ? j.num_turns : 0,
  };
}

// ----------------------------------------------------------------- HTTP

function senden(res, code, objekt) {
  const text = JSON.stringify(objekt);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(text),
    'cache-control': 'no-store',
  });
  res.end(text);
}

function koerperLesen(req) {
  return new Promise((fertig, scheitern) => {
    let roh = '';
    let bytes = 0;
    req.on('data', (d) => {
      bytes += d.length;
      if (bytes > MAX_BYTES) {
        scheitern(new Error('Anfrage zu groß'));
        req.destroy();
        return;
      }
      roh += d;
    });
    req.on('end', () => fertig(roh));
    req.on('error', scheitern);
  });
}

const server = createServer(async (req, res) => {
  // Doppelt gesichert: Der Server lauscht nur auf 127.0.0.1, und fremde
  // Herkunft wird zusätzlich abgewiesen.
  const her = req.socket.remoteAddress || '';
  if (!her.includes('127.0.0.1') && her !== '::1' && !her.endsWith(':127.0.0.1')) {
    senden(res, 403, { fehler: 'Nur lokale Verbindungen.' });
    return;
  }

  const pfad = (req.url || '').split('?')[0];

  if (req.method === 'GET' && pfad === '/status') {
    // Ohne Token: verrät nur, dass der Dienst läuft. Die App prüft damit die Verbindung.
    senden(res, 200, {
      bereit: true,
      version: VERSION,
      beschaeftigt: laufend !== null,
      sitzung: sitzung !== null,
      arbeitsordner: ARBEIT,
    });
    return;
  }

  const kopf = req.headers.authorization || '';
  const mitgebracht = kopf.startsWith('Bearer ') ? kopf.slice(7).trim() : '';
  if (!tokenStimmt(mitgebracht)) {
    senden(res, 401, { fehler: 'Falsches oder fehlendes Token. Trag das Token aus ~/.jarvis-agent/token in die Jarvis-Einstellungen ein.' });
    return;
  }

  if (req.method === 'POST' && pfad === '/neu') {
    sitzung = null;
    senden(res, 200, { neu: true });
    return;
  }

  if (req.method === 'POST' && pfad === '/auftrag') {
    if (laufend) {
      senden(res, 409, { fehler: 'Ich arbeite noch am letzten Auftrag.' });
      return;
    }
    let daten;
    try {
      daten = JSON.parse(await koerperLesen(req) || '{}');
    } catch (e) {
      senden(res, 400, { fehler: 'Unlesbare Anfrage: ' + e.message });
      return;
    }
    const text = typeof daten.text === 'string' ? daten.text.trim() : '';
    if (!text) {
      senden(res, 400, { fehler: 'Kein Auftragstext.' });
      return;
    }
    const ergebnis = await auftragAusfuehren(text, daten.neu === true);
    senden(res, ergebnis.fehler ? 502 : 200, ergebnis);
    return;
  }

  senden(res, 404, { fehler: 'Unbekannter Pfad.' });
});

if (!existsSync(ARBEIT)) mkdirSync(ARBEIT, { recursive: true });

server.listen(PORT, HOST, () => {
  console.log('Jarvis-Agent ' + VERSION + ' läuft auf http://' + HOST + ':' + PORT);
  console.log('Arbeitsordner: ' + ARBEIT);
  console.log('Token steht in: ' + TOKEN_DATEI);
  console.log('Dieses Token in die Jarvis-Einstellungen eintragen (Abschnitt „Agent-Rückgrat“).');
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    if (laufend) laufend.kind.kill('SIGTERM');
    server.close(() => process.exit(0));
  });
}
