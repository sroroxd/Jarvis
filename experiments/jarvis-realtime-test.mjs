// JARVIS Realtime: unabhängiger 2-Minuten-Prototyp für Termux + Handy-Browser.
// Keine fremden npm-Abhängigkeiten und keine Speicherung des API-Schlüssels.
import http from 'node:http';
import { randomBytes } from 'node:crypto';

const HOST = '127.0.0.1';
const PORT = 8788;
const MODEL = 'gpt-realtime-2.1-mini';
const ACCESS_CODE = randomBytes(12).toString('base64url');
const COOKIE_VALUE = randomBytes(24).toString('hex');
const ORIGIN = `http://${HOST}:${PORT}`;
let sessionCreated = false;

const html = String.raw`<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>JARVIS · Realtime-Test</title>
<style>
  :root{color-scheme:dark;font-family:system-ui,sans-serif}
  body{background:#081524;color:#e7f5ff;margin:0;display:grid;min-height:95vh;place-items:center}
  main{max-width:420px;padding:32px 22px;text-align:center}
  h1{letter-spacing:.18em;color:#8fe8ff} p{line-height:1.55;color:#b9cbd6}
  button{border:1px solid #7be2ff;border-radius:12px;padding:15px 22px;margin:7px;background:#07374b;color:white;font-size:1rem}
  button:disabled{opacity:.4} #state{margin-top:18px;min-height:3rem;word-break:break-word}
  small{color:#b7c9d8}
</style>
</head><body><main>
<h1>JARVIS</h1>
<p>OpenAI-Realtime · Testmodus für freie Gespräche. Der Test endet automatisch nach zwei Minuten.</p>
<button id="start">🎤 Gespräch starten</button><button id="stop" disabled>■ Beenden</button>
<p id="state" role="status">Bereit. Mikrofonfreigabe erfolgt erst beim Start.</p>
<small>Nur ein Test pro Serverstart. Kein TFT-Bild und keine APK-Integration. API-Nutzung ist kostenpflichtig.</small>
<audio id="speaker" autoplay playsinline></audio>
<script>
(() => {
 const start = document.querySelector('#start');
 const stop = document.querySelector('#stop');
 const state = document.querySelector('#state');
 const speaker = document.querySelector('#speaker');
 let peer = null, stream = null, timer = null;
 const status = message => { state.textContent = message; };
 function end(message = 'Gespräch beendet.') {
   if (timer) clearTimeout(timer);
   timer = null;
   if (peer) peer.close(); peer = null;
   if (stream) stream.getTracks().forEach(t => t.stop()); stream = null;
   speaker.srcObject = null;
   stop.disabled = true;
   start.disabled = true; // Kostenbremse: je Serverstart nur eine Sitzung.
   status(message);
 }
 stop.onclick = () => end();
 start.onclick = async () => {
   start.disabled = true;
   stop.disabled = false;
   try {
     status('Mikrofon wird freigegeben …');
     stream = await navigator.mediaDevices.getUserMedia({ audio: {echoCancellation:true, noiseSuppression:true} });
     peer = new RTCPeerConnection();
     peer.ontrack = event => { speaker.srcObject = event.streams[0]; speaker.play().catch(() => {}); };
     peer.onconnectionstatechange = () => {
       if (peer?.connectionState === 'failed' || peer?.connectionState === 'disconnected') end('Verbindung unterbrochen.');
     };
     peer.addTrack(stream.getAudioTracks()[0], stream);
     const events = peer.createDataChannel('oai-events');
     events.onopen = () => status('Verbunden. Sprich einfach los – Unterbrechen ist möglich.');
     events.onmessage = event => {
       try {
         const data = JSON.parse(event.data);
         if (data.type === 'error') status('Realtime-Fehler: ' + (data.error?.message || 'Unbekannt'));
       } catch {}
     };
     const offer = await peer.createOffer();
     await peer.setLocalDescription(offer);
     status('OpenAI verbindet …');
     const tokenResponse = await fetch('/token', {method:'POST', headers:{'Content-Type':'application/json'}, body:'{}', credentials:'same-origin'});
     if (!tokenResponse.ok) throw Error('Token-Anfrage fehlgeschlagen (HTTP ' + tokenResponse.status + ').');
     const {token} = await tokenResponse.json();
     const answerResponse = await fetch('https://api.openai.com/v1/realtime/calls', {
       method:'POST', headers:{Authorization:'Bearer ' + token, 'Content-Type':'application/sdp'}, body:offer.sdp
     });
     if (!answerResponse.ok) throw Error('Realtime-Verbindung fehlgeschlagen (HTTP ' + answerResponse.status + ').');
     await peer.setRemoteDescription({type:'answer', sdp:await answerResponse.text()});
     timer = setTimeout(() => end('Zwei Minuten vorbei. Test beendet.'), 120000);
   } catch (error) { end(error.message || 'Verbindung fehlgeschlagen.'); }
 };
})();
</script></main></body></html>`;

function noStore(res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self' https://api.openai.com; media-src 'self' blob:; img-src 'self'; object-src 'none'; base-uri 'none'");
}

function send(res, status, body, contentType = 'text/plain; charset=utf-8') {
  res.statusCode = status;
  res.setHeader('Content-Type', contentType);
  res.end(body);
}

function authorized(req) {
  const cookies = req.headers.cookie || '';
  return cookies.split(';').some(cookie => cookie.trim() === `jarvis_test=${COOKIE_VALUE}`);
}

async function requestHandler(req, res, key) {
  noStore(res);
  if (req.method === 'GET' && req.url?.startsWith('/?access=')) {
    const candidate = new URL(req.url, ORIGIN).searchParams.get('access');
    if (candidate !== ACCESS_CODE) return send(res, 403, 'Zugriff verweigert');
    res.statusCode = 303;
    res.setHeader('Set-Cookie', `jarvis_test=${COOKIE_VALUE}; HttpOnly; SameSite=Strict; Path=/`);
    res.setHeader('Location', '/');
    res.end();
    return;
  }
  if (!authorized(req)) return send(res, 403, 'Zugriff verweigert');
  if (req.method === 'GET' && req.url === '/') return send(res, 200, html, 'text/html; charset=utf-8');
  if (req.method === 'POST' && req.url === '/token') {
    if (req.headers.origin !== ORIGIN) return send(res, 403, 'Falsche Herkunft');
    if (sessionCreated) return send(res, 429, 'Nur eine Sitzung pro Start.');
    sessionCreated = true; // Selbst bei OpenAI-Fehler keine stillen Wiederholungen.
    try {
      const response = await fetch('https://api.openai.com/v1/realtime/client_secrets', {
        method: 'POST',
        headers: {'Authorization': `Bearer ${key}`, 'Content-Type': 'application/json'},
        body: JSON.stringify({
          expires_after: {anchor:'created_at', seconds:60},
          session: {
            type:'realtime', model:MODEL, output_modalities:['audio'],
            instructions:'Du bist JARVIS. Sprich auf Deutsch, natürlich und knapp. Keine Behauptungen über nicht sichtbare TFT-Bilder. Antworte möglichst in zwei Sätzen.',
            audio:{input:{turn_detection:{type:'server_vad'}},output:{voice:'marin'}},
            max_output_tokens:700
          }
        }),
        signal: AbortSignal.timeout(12000)
      });
      if (!response.ok) return send(res, 502, `OpenAI hat HTTP ${response.status} gemeldet.`);
      const payload = await response.json();
      if (typeof payload.value !== 'string' || !payload.value.startsWith('ek_')) {
        return send(res, 502, 'OpenAI hat kein gültiges Kurzzeittoken geliefert.');
      }
      return send(res, 200, JSON.stringify({token:payload.value}), 'application/json; charset=utf-8');
    } catch {
      return send(res, 502, 'OpenAI ist gerade nicht erreichbar.');
    }
  }
  return send(res, 404, 'Nicht gefunden');
}

async function readKey() {
  if (process.env.OPENAI_API_KEY) return process.env.OPENAI_API_KEY;
  if (!process.stdin.isTTY || !process.stdin.setRawMode) {
    throw Error('Bitte direkt in Termux starten, um den API-Schlüssel verdeckt einzugeben.');
  }
  process.stdout.write('OpenAI-API-Schlüssel hier verdeckt einfügen, dann Enter: ');
  const input = process.stdin;
  input.setRawMode(true);
  input.resume();
  return new Promise((resolve, reject) => {
    let result = '';
    const onInput = data => {
      for (const ch of data.toString()) {
        if (ch === '\r' || ch === '\n') {
          input.off('data', onInput);
          input.setRawMode(false);
          input.pause();
          process.stdout.write('\n');
          resolve(result.trim());
          return;
        }
        if (ch === '\x03') {
          input.setRawMode(false);
          reject(Error('Abgebrochen.'));
          return;
        }
        if (ch === '\x7f' || ch === '\b') result = result.slice(0, -1);
        else if (ch >= ' ') result += ch;
      }
    };
    input.on('data', onInput);
  });
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  try {
    const key = await readKey();
    if (!key.startsWith('sk-') || key.length < 30) throw Error('Das sieht nicht wie ein OpenAI-API-Schlüssel aus.');
    const server = http.createServer((req,res) => requestHandler(req,res,key));
    server.listen(PORT, HOST, () => {
      console.log('JARVIS Realtime-Test bereit. Kein Schlüssel gespeichert.');
      console.log(`Öffne diesen Link im Handy-Browser: ${ORIGIN}/?access=${ACCESS_CODE}`);
      console.log('Beenden: Strg+C. Pro Start nur eine Sitzung, maximal zwei Minuten.');
    });
  } catch (error) {console.error(error.message); process.exitCode = 1;}
}

export { requestHandler };
