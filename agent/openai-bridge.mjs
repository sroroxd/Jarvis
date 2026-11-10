#!/usr/bin/env node
// Private Loopback-Brücke: OpenAI-Schlüssel bleibt im Termux-/Container-Prozess.
import { createServer } from 'node:http';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export const DEFAULT_MODEL = 'gpt-6-luna';
const MAX_BODY = 12 * 1024 * 1024; // einzelne Bildschirmaufnahme einschließlich Base64
const CONTEXT_TTL = 10 * 60 * 1000;

function fail(message, status = 400) { return Object.assign(new Error(message), { status }); }
function equal(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const x = Buffer.from(a), y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/** In-Memory-Kontext enthält auch Reasoning-Items, die zwischen Werkzeugrunden nötig sind. */
export class Contexts {
  constructor(now = Date.now) { this.items = new Map(); this.now = now; }
  put(output) {
    for (const [id, value] of this.items) if (value.expires <= this.now()) this.items.delete(id);
    while (this.items.size >= 64) this.items.delete(this.items.keys().next().value);
    const id = randomBytes(24).toString('hex');
    this.items.set(id, { output, expires: this.now() + CONTEXT_TTL });
    return id;
  }
  get(id) {
    const value = this.items.get(id);
    if (!value || value.expires <= this.now()) throw fail('Werkzeugkontext abgelaufen. Auftrag erneut ausdrücklich stellen.', 409);
    return value.output;
  }
}

function parts(content, role) {
  if (typeof content === 'string') return [{ type: role === 'assistant' ? 'output_text' : 'input_text', text: content }];
  return (content || []).flatMap(p => {
    if (p.type === 'text') return [{ type: role === 'assistant' ? 'output_text' : 'input_text', text: p.text || '' }];
    if (p.type === 'image') {
      const s = p.source;
      if (s?.type !== 'base64' || !['image/jpeg', 'image/png', 'image/webp'].includes(s.media_type))
        throw fail('Nicht unterstütztes Bildformat.');
      return [{ type: 'input_image', image_url: `data:${s.media_type};base64,${s.data}`, detail: 'auto' }];
    }
    return [];
  });
}

/** Konvertiert nur die bereits vom nativen Jarvis angebotenen Werkzeuge; kein Shell-Zugriff. */
export function requestBody(body, model, contexts) {
  if (body.model && body.model !== model) throw fail('App und lokaler Dienst müssen dasselbe OpenAI-Modell verwenden.');
  if (!Array.isArray(body.messages) || !body.messages.length) throw fail('Nachrichten fehlen.');
  const input = [];
  for (const message of body.messages) {
    const role = message.role;
    if (!['user', 'assistant'].includes(role)) throw fail('Unbekannte Nachrichtenrolle.');
    const blocks = Array.isArray(message.content) ? message.content : [];
    const saved = blocks.find(p => p.type === 'jarvis_openai_context');
    if (saved) {
      if (role !== 'assistant') throw fail('Ungültiger Werkzeugkontext.');
      input.push(...contexts.get(saved.id));
      continue;
    }
    const normal = parts(message.content, role);
    if (normal.length) input.push({ role, content: role === 'assistant' ? normal.map(p => p.text || '').join('') : normal });
    for (const p of blocks) {
      if (p.type === 'tool_result') {
        input.push({ type: 'function_call_output', call_id: p.tool_use_id,
          output: typeof p.content === 'string' ? p.content : JSON.stringify(p.content) });
      } else if (p.type === 'tool_use') {
        input.push({ type: 'function_call', call_id: p.id, name: p.name, arguments: JSON.stringify(p.input || {}) });
      }
    }
  }
  const tools = (body.tools || []).map(t => {
    if (t.type?.startsWith('web_search')) return { type: 'web_search' };
    if (typeof t.name !== 'string' || !t.input_schema) throw fail('Ungültige Werkzeugbeschreibung.');
    return { type: 'function', name: t.name, description: t.description || '', parameters: t.input_schema, strict: false };
  });
  const instructions = typeof body.system === 'string' ? body.system
    : (body.system || []).filter(p => p.type === 'text').map(p => p.text).join('\n');
  const result = { model, instructions, input, tools, store: false, stream: true,
    include: ['reasoning.encrypted_content'],
    max_output_tokens: Math.max(256, Math.min(Number(body.max_tokens) || 2048, 8192)),
    reasoning: { effort: 'low' }, parallel_tool_calls: false };
  const web = (body.tools || []).find(t => t.type?.startsWith('web_search'));
  if (web) result.max_tool_calls = Math.max(1, Math.min(Number(web.max_uses) || 3, 6));
  return result;
}

export function resultBody(response, contexts) {
  if (response.status !== 'completed') throw fail('OpenAI-Antwort wurde nicht vollständig abgeschlossen.', 502);
  const output = response.output || [];
  const content = [];
  for (const item of output) {
    if (item.type === 'message') {
      for (const p of item.content || []) {
        if (p.type === 'output_text') content.push({ type: 'text', text: p.text || '' });
        else if (p.type === 'refusal') content.push({ type: 'text', text: p.refusal || '' });
      }
    } else if (item.type === 'function_call') {
      let input;
      try { input = JSON.parse(item.arguments); } catch { throw fail('OpenAI lieferte ungültige Werkzeugargumente.', 502); }
      content.push({ type: 'tool_use', id: item.call_id, name: item.name, input });
    }
  }
  if (content.some(p => p.type === 'tool_use')) content.push({ type: 'jarvis_openai_context', id: contexts.put(output) });
  const usage = response.usage || {};
  const cached = usage.input_tokens_details?.cached_tokens || 0;
  return { type: 'message', role: 'assistant', model: response.model, content,
    stop_reason: content.some(p => p.type === 'tool_use') ? 'tool_use' : 'end_turn',
    usage: { input_tokens: Math.max(0, (usage.input_tokens || 0) - cached), cache_read_input_tokens: cached,
      cache_creation_input_tokens: 0, output_tokens: usage.output_tokens || 0,
      server_tool_use: { web_search_requests: output.filter(p => p.type === 'web_search_call').length } } };
}

/** SSE-Daten werden inkrementell dekodiert, einschließlich geteilter UTF-8-Zeichen. */
export async function* events(stream) {
  const decoder = new TextDecoder();
  let pending = '';
  for await (const chunk of stream) {
    pending += decoder.decode(chunk, { stream: true });
    let end;
    while ((end = pending.search(/\r?\n\r?\n/)) >= 0) {
      const frame = pending.slice(0, end);
      const separator = pending.slice(end).match(/^\r?\n\r?\n/)[0];
      pending = pending.slice(end + separator.length);
      const data = frame.split(/\r?\n/).filter(l => l.startsWith('data:')).map(l => l.slice(5).trimStart()).join('\n');
      if (data && data !== '[DONE]') yield JSON.parse(data);
    }
  }
  // Responses schließt vollständige Ereignisse mit einer Leerzeile. Ein Rest wird nicht als Erfolg gewertet.
}

function event(res, data) { res.write(`event: ${data.type}\ndata: ${JSON.stringify(data)}\n\n`); }
function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}
async function readBody(req) {
  const chunks = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw fail('Anfrage zu groß.', 413);
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks)); } catch { throw fail('Ungültiges JSON.'); }
}

export function createBridge({ apiKey, token, model = DEFAULT_MODEL, fetchImpl = fetch, contexts = new Contexts() }) {
  if (!apiKey?.startsWith('sk-') || !token || token.length < 24) throw fail('Privater Schlüssel oder Kopplungstoken fehlt.');
  return createServer(async (req, res) => {
    // Browser anderer Apps sollen die lokale Brücke weder lesen noch über CORS ansprechen können.
    if (req.headers.origin || !['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress)) {
      json(res, 403, { error: { type: 'permission_error', message: 'Nur lokaler App-Zugriff erlaubt.' } }); return;
    }
    const supplied = req.headers['x-api-key'] || req.headers.authorization?.replace(/^Bearer /, '');
    if (!equal(supplied, token)) {
      json(res, 401, { error: { type: 'authentication_error', message: 'Lokales Kopplungstoken stimmt nicht.' } }); return;
    }
    if (req.method === 'GET' && req.url === '/status') {
      json(res, 200, { bereit: true, provider: 'openai', model, offline: false }); return;
    }
    if (req.method !== 'POST' || req.url !== '/v1/messages') {
      json(res, 404, { error: { type: 'not_found_error', message: 'Unbekannter Endpunkt.' } }); return;
    }
    const controller = new AbortController();
    const deadline = setTimeout(() => controller.abort(), 85_000);
    res.on('close', () => { if (!res.writableEnded) controller.abort(); });
    try {
      const body = await readBody(req);
      const upstream = await fetchImpl('https://api.openai.com/v1/responses', {
        method: 'POST', signal: controller.signal,
        headers: { authorization: `Bearer ${apiKey}`, 'content-type': 'application/json' },
        body: JSON.stringify(requestBody(body, model, contexts)) });
      if (!upstream.ok) {
        // Keine rohen Providerfehler ausgeben: sie könnten Anfragedaten/Schlüssel enthalten.
        const message = upstream.status === 401 ? 'OpenAI-Schlüssel abgelehnt.'
          : upstream.status === 429 ? 'OpenAI-Kontingent oder Anfragelimit erreicht.'
          : `OpenAI-Anfrage fehlgeschlagen (HTTP ${upstream.status}).`;
        await upstream.body?.cancel();
        json(res, upstream.status, { error: { type: 'api_error', message } }); return;
      }
      const streaming = body.stream === true;
      if (streaming) {
        res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-store' });
        event(res, { type: 'message_start', message: { role: 'assistant', content: [], usage: {} } });
        event(res, { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } });
      }
      let response; let streamedText = '';
      for await (const ev of events(upstream.body)) {
        if (ev.type === 'response.output_text.delta') {
          streamedText += ev.delta || '';
          if (streaming) event(res, { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: ev.delta || '' } });
        } else if (ev.type === 'response.completed') response = ev.response;
        else if (['response.failed', 'response.incomplete', 'error'].includes(ev.type)) throw fail('OpenAI-Antwort fehlgeschlagen oder unvollständig.', 502);
      }
      if (!response) throw fail('OpenAI-Verbindung vor Abschluss unterbrochen.', 502);
      const converted = resultBody(response, contexts);
      if (!streaming) { json(res, 200, converted); return; }
      const finalText = converted.content.filter(p => p.type === 'text').map(p => p.text).join('');
      if (!streamedText && finalText) event(res, { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: finalText } });
      else if (streamedText !== finalText) throw fail('Unvollständiger OpenAI-Textstrom.', 502);
      event(res, { type: 'content_block_stop', index: 0 });
      let index = 1;
      for (const block of converted.content.filter(p => p.type !== 'text')) {
        event(res, { type: 'content_block_start', index, content_block: block });
        event(res, { type: 'content_block_stop', index }); index++;
      }
      event(res, { type: 'message_delta', delta: { stop_reason: converted.stop_reason }, usage: converted.usage });
      event(res, { type: 'message_stop' }); res.end();
    } catch (error) {
      const message = error.name === 'AbortError' ? 'OpenAI-Anfrage abgebrochen oder Zeitlimit erreicht.'
        : error.status ? error.message : 'OpenAI-Verbindung fehlgeschlagen.';
      if (!res.destroyed) {
        if (res.headersSent) { event(res, { type: 'error', error: { type: 'api_error', message } }); res.end(); }
        else json(res, error.status || 502, { error: { type: 'api_error', message } });
      }
    } finally { clearTimeout(deadline); }
  });
}

function start() {
  const dir = process.env.JARVIS_OPENAI_HOME || join(homedir(), '.jarvis-openai');
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  const agentToken = join(homedir(), '.jarvis-agent', 'token');
  const tokenFile = existsSync(agentToken) ? agentToken : join(dir, 'token');
  if (!existsSync(tokenFile)) writeFileSync(tokenFile, randomBytes(32).toString('hex'), { mode: 0o600, flag: 'wx' });
  const token = readFileSync(tokenFile, 'utf8').trim();
  // Kein Ausführen/„Sourcen“ von env-Dateien; eine private Schlüsseldatei enthält nur den Schlüssel.
  const keyFile = join(dir, 'api-key');
  const apiKey = process.env.OPENAI_API_KEY || (existsSync(keyFile) ? readFileSync(keyFile, 'utf8').trim() : '');
  const server = createBridge({ apiKey, token,
    model: process.env.JARVIS_OPENAI_MODEL || DEFAULT_MODEL });
  server.listen(8778, '127.0.0.1', () => console.log('Jarvis-OpenAI-Brücke bereit auf 127.0.0.1:8778; KI benötigt Internet.'));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { start(); } catch { console.error('OpenAI-Brücke nicht gestartet: privaten Schlüssel und Einrichtung prüfen.'); process.exitCode = 1; }
}
