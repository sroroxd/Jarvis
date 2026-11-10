import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createBridge, Contexts, requestBody, resultBody, events, DEFAULT_MODEL } from './openai-bridge.mjs';

const token = 'local-test-token-not-a-provider-key';
const base = () => ({ model: DEFAULT_MODEL, max_tokens: 1024,
  system: [{ type: 'text', text: 'Du bist Jarvis.', cache_control: { type: 'ephemeral' } }],
  messages: [{ role: 'user', content: 'Hallo' }] });
const answer = (extra = {}) => ({ status: 'completed', model: DEFAULT_MODEL,
  output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Grüße, Sir.' }] }],
  usage: { input_tokens: 20, input_tokens_details: { cached_tokens: 5 }, output_tokens: 7 }, ...extra });
const upstream = (response = answer(), prefix = []) => new Response(
  [...prefix, { type: 'response.completed', response }].map(e => `event: ${e.type}\ndata: ${JSON.stringify(e)}\n\n`).join(''),
  { status: 200, headers: { 'content-type': 'text/event-stream' } });

async function withServer(fetchImpl, run) {
  const server = createBridge({ apiKey: 'sk-unit-test-only', token, fetchImpl });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
function post(url, body, headers = {}) {
  return fetch(url + '/v1/messages', { method: 'POST', headers: { 'content-type': 'application/json', 'x-api-key': token, ...headers }, body: JSON.stringify(body) });
}

test('Text, Bilder, System und Werkzeugdefinitionen werden für Responses übersetzt', () => {
  const b = base();
  b.messages[0].content = [{ type: 'text', text: 'Was sehe ich?' }, { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'YWJj' } }];
  b.tools = [{ name: 'kalender_lesen', description: 'Termine', input_schema: { type: 'object', properties: {} } },
    { type: 'web_search_20250305', name: 'web_search', max_uses: 2 }];
  const r = requestBody(b, DEFAULT_MODEL, new Contexts());
  assert.equal(r.instructions, 'Du bist Jarvis.'); assert.equal(r.store, false);
  assert.deepEqual(r.include, ['reasoning.encrypted_content']);
  assert.equal(r.input[0].content[1].image_url, 'data:image/jpeg;base64,YWJj');
  assert.equal(r.tools[0].strict, false); assert.equal(r.tools[1].type, 'web_search');
  assert.equal(r.max_tool_calls, 2); assert.equal(r.parallel_tool_calls, false);
});

test('Reasoning und Aufruf-ID bleiben bis zur Werkzeugantwort zusammen erhalten', () => {
  const contexts = new Contexts();
  const output = [{ type: 'reasoning', id: 'rs_1', encrypted_content: 'encrypted' },
    { type: 'function_call', call_id: 'call_7', name: 'kalender_lesen', arguments: '{"von":"2026-11-10"}' }];
  const converted = resultBody(answer({ output }), contexts);
  assert.equal(converted.stop_reason, 'tool_use');
  const b = base();
  b.messages.push({ role: 'assistant', content: converted.content },
    { role: 'user', content: [{ type: 'tool_result', tool_use_id: 'call_7', content: 'Keine Termine.' }] });
  const r = requestBody(b, DEFAULT_MODEL, contexts);
  assert.deepEqual(r.input.slice(1, 3), output);
  assert.deepEqual(r.input[3], { type: 'function_call_output', call_id: 'call_7', output: 'Keine Termine.' });
});

test('Abgelaufener Werkzeugkontext löst keine erneute Werkzeugrunde aus', () => {
  let now = 0; const contexts = new Contexts(() => now);
  const id = contexts.put([]); now = 601_000;
  const b = base(); b.messages.push({ role: 'assistant', content: [{ type: 'jarvis_openai_context', id }] });
  assert.throws(() => requestBody(b, DEFAULT_MODEL, contexts), /abgelaufen/);
});

test('Unvollständige Antworten und defekte Argumente werden abgelehnt', () => {
  assert.throws(() => resultBody(answer({ status: 'incomplete' }), new Contexts()), /vollständig/);
  assert.throws(() => resultBody(answer({ output: [{ type: 'function_call', arguments: '{' }] }), new Contexts()), /ungültige/);
});

test('Verbrauch zählt Cache-Tokens nicht doppelt und erfasst Websuchen', () => {
  const converted = resultBody(answer({ output: [{ type: 'web_search_call' }] }), new Contexts());
  assert.equal(converted.usage.input_tokens, 15); assert.equal(converted.usage.cache_read_input_tokens, 5);
  assert.equal(converted.usage.server_tool_use.web_search_requests, 1);
});

test('SSE verarbeitet geteilte UTF-8-Zeichen und CRLF', async () => {
  const bytes = new TextEncoder().encode('event: test\r\ndata: {"text":"Grüße"}\r\n\r\n');
  async function* chunks() { for (const byte of bytes) yield Uint8Array.of(byte); }
  const received = []; for await (const e of events(chunks())) received.push(e);
  assert.deepEqual(received, [{ text: 'Grüße' }]);
});

test('Ohne Token und mit fremdem Origin kommt keine Provideranfrage zustande', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return upstream(); }, async url => {
    assert.equal((await fetch(url + '/status')).status, 401);
    assert.equal((await post(url, base(), { origin: 'https://fremd.example' })).status, 403);
    assert.equal((await post(url, base(), { 'x-api-key': 'falsch' })).status, 401);
  });
  assert.equal(calls, 0);
});

test('Provider-Schlüssel bleibt im Backend und wird nicht in Status/Antwort gegeben', async () => {
  await withServer(async (url, options) => {
    assert.equal(url, 'https://api.openai.com/v1/responses');
    assert.equal(options.headers.authorization, 'Bearer sk-unit-test-only');
    assert.equal(JSON.parse(options.body).model, DEFAULT_MODEL);
    return upstream();
  }, async url => {
    const status = await fetch(url + '/status', { headers: { 'x-api-key': token } });
    assert.equal((await status.json()).offline, false);
    const response = await post(url, base());
    const text = await response.text(); assert.ok(text.includes('Grüße')); assert.ok(!text.includes('sk-unit'));
  });
});

test('Gestreamter Text, Werkzeugdaten und Abschluss werden für die native App geliefert', async () => {
  await withServer(async () => upstream(answer(), [{ type: 'response.output_text.delta', delta: 'Grüße, Sir.' }]), async url => {
    const response = await post(url, { ...base(), stream: true }); const text = await response.text();
    assert.match(text, /text_delta/); assert.match(text, /message_stop/);
    assert.match(text, /"cache_read_input_tokens":5/);
  });
});

test('Abgerissener Strom liefert keinen erfolgreichen Abschluss', async () => {
  await withServer(async () => new Response('data: {"type":"response.output_text.delta","delta":"Teil"}\n\n'), async url => {
    const text = await (await post(url, { ...base(), stream: true })).text();
    assert.match(text, /"type":"error"/); assert.ok(!text.includes('message_stop'));
  });
});

test('Providerfehler geben keine rohen möglicherweise geheimen Fehlerdetails weiter', async () => {
  await withServer(async () => new Response('sk-private-secret and private input', { status: 401 }), async url => {
    const response = await post(url, base()); assert.equal(response.status, 401);
    assert.ok(!(await response.text()).includes('sk-private'));
  });
});

test('Defekte Anfrage wird vor Providerkontakt abgelehnt', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return upstream(); }, async url => {
    assert.equal((await post(url, { messages: [] })).status, 400);
  });
  assert.equal(calls, 0);
});

test('Verlauf mit Assistententext bleibt als gültige Eingabenachricht erhalten', () => {
  const b = base(); b.messages.push({ role: 'assistant', content: 'Vorige Antwort.' }, { role: 'user', content: 'Und weiter?' });
  assert.equal(requestBody(b, DEFAULT_MODEL, new Contexts()).input[1].content, 'Vorige Antwort.');
});

test('Werkzeugaufruf wird erst nach vollständiger Providerantwort gestreamt', async () => {
  const output = [{ type: 'reasoning', id: 'rs_1', encrypted_content: 'opaque' },
    { type: 'function_call', name: 'kalender_lesen', call_id: 'call_1', arguments: '{"von":"2026-11-10"}' }];
  await withServer(async () => upstream(answer({ output })), async url => {
    const response = await post(url, { ...base(), stream: true }); const text = await response.text();
    assert.match(text, /"type":"tool_use"/); assert.match(text, /"von":"2026-11-10"/);
    assert.match(text, /jarvis_openai_context/); assert.match(text, /"stop_reason":"tool_use"/);
    assert.match(text, /message_stop/); assert.ok(!text.includes('encrypted'));
  });
});

test('Abweichendes Modell wird statt stiller teurerer Modellwahl abgelehnt', () => {
  assert.throws(() => requestBody({ ...base(), model: 'gpt-anders' }, DEFAULT_MODEL, new Contexts()), /dasselbe/);
});

test('Abbruch des lokalen Clients bricht auch den Providerrequest ab', async () => {
  let aborted;
  const interrupted = new Promise(resolve => { aborted = resolve; });
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  await withServer(async (url, options) => {
    options.signal.addEventListener('abort', () => aborted(true)); started();
    return new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('Stop', 'AbortError'))));
  }, async url => {
    const controller = new AbortController();
    const pending = fetch(url + '/v1/messages', { method: 'POST', signal: controller.signal,
      headers: { 'x-api-key': token }, body: JSON.stringify(base()) }).catch(() => null);
    await ready; controller.abort(); await pending;
    const timeout = setTimeout(() => aborted(false), 2000);
    try { assert.equal(await interrupted, true); } finally { clearTimeout(timeout); }
  });
});
