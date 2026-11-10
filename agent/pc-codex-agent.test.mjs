import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import { request as httpRequest } from 'node:http';
import { agentServer, codexAntwort } from './pc-codex-agent.mjs';

const nonce = 'n'.repeat(40), token = 't'.repeat(40);
async function fixture(t, execute = async text => ({ antwort: text })) {
  const server = agentServer({ nonce, execute });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const base = 'http://127.0.0.1:' + server.address().port;
  const request = (path, body, key = token, extra = {}) => fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { authorization: 'Bearer ' + key, ...extra }, body: body === undefined ? undefined : JSON.stringify(body),
  });
  const pair = () => request('/token', { token }, nonce);
  const slowPost = (path, key) => {
    let req;
    const result = new Promise((done, reject) => {
      req = httpRequest(base + path, { method: 'POST', headers: { authorization: 'Bearer ' + key } }, res => {
        const chunks = [];
        res.on('data', part => chunks.push(part));
        res.on('end', () => done({ status: res.statusCode, body: JSON.parse(Buffer.concat(chunks)) }));
      });
      req.on('error', reject);
    });
    return { req, result };
  };
  return { request, pair, slowPost };
}
test('Kopplung bleibt geheim, einmalig und erforderlich', async t => {
  const { request, pair } = await fixture(t);
  assert.equal((await request('/auftrag', { text: 'Hallo' })).status, 401);
  assert.equal((await request('/status')).status, 200);
  assert.equal((await pair()).status, 200);
  assert.equal((await pair()).status, 401);
  assert.equal((await request('/auftrag', { text: 'Hallo' }, 'falsch')).status, 401);
  const result = await request('/auftrag', { text: 'Hallo' });
  assert.deepEqual(await result.json(), { antwort: 'Hallo' });
  assert.equal((await request('/status', undefined, token, { origin: 'https://example.com' })).status, 403);
});
test('Ein verzögerter zweiter Kopplungsrequest überschreibt kein bestätigtes Token', async t => {
  const { request, pair, slowPost } = await fixture(t);
  const late = slowPost('/token', nonce);
  late.req.write('{"token":"');
  await new Promise(done => setTimeout(done, 20));
  assert.equal((await pair()).status, 200);
  late.req.end('z'.repeat(40) + '"}');
  assert.equal((await late.result).status, 400);
  assert.equal((await request('/auftrag', { text: 'Hallo' })).status, 200);
});
test('Byteweise empfangene Aufträge behalten ihre Umlaute und Emoji', async t => {
  const { pair, slowPost } = await fixture(t); await pair();
  const { req, result } = slowPost('/auftrag', token);
  for (const byte of Buffer.from(JSON.stringify({ text: 'Grüße 👋' }))) {
    req.write(Buffer.from([byte]));
    await new Promise(done => setTimeout(done, 1));
  }
  req.end();
  assert.equal((await result).body.antwort, 'Grüße 👋');
});
test('Leere und übergroße Aufträge werden abgewiesen', async t => {
  const { request, pair } = await fixture(t); await pair();
  assert.equal((await request('/auftrag', { text: ' ' })).status, 400);
  assert.equal((await request('/auftrag', { text: 'x'.repeat(66000) })).status, 413);
});
test('Parallelauftrag wird gesperrt und nach Abschluss wieder freigegeben', async t => {
  let finish;
  const { request, pair } = await fixture(t, () => new Promise(done => { finish = done; })); await pair();
  const first = request('/auftrag', { text: 'Hallo' });
  while (!finish) await new Promise(done => setTimeout(done, 5));
  assert.equal((await request('/auftrag', { text: 'Noch eins' })).status, 409);
  assert.equal((await (await request('/fortschritt')).json()).beschaeftigt, true);
  finish({ antwort: 'Fertig' }); assert.equal((await first).status, 200);
  assert.equal((await (await request('/fortschritt')).json()).beschaeftigt, false);
});
function fake(lines, code = 0, captured = {}) {
  return (binary, args, options) => {
    Object.assign(captured, { binary, args, options });
    const child = new EventEmitter();
    child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.kill = () => {};
    child.stdin.on('finish', () => queueMicrotask(() => {
      child.stdout.write(lines.join('\n')); child.emit('close', code);
    }));
    return child;
  };
}
test('Nur vollständig erfolgreicher Codex-Turn gilt als Antwort, Aufruf bleibt lesend', async () => {
  const captured = {};
  const result = await codexAntwort('Hallo', { spawnProcess: fake([
    JSON.stringify({ type: 'item.completed', item: { type: 'agent_message', text: 'Guten Tag.' } }),
    JSON.stringify({ type: 'turn.completed' }),
  ], 0, captured) });
  assert.equal(result.antwort, 'Guten Tag.');
  assert.ok(captured.args.includes('read-only')); assert.ok(captured.args.includes('--ephemeral'));
  assert.equal(captured.options.windowsHide, true);
  assert.equal(captured.options.env.OPENAI_API_KEY, undefined);
  assert.equal(captured.options.env.CODEX_API_KEY, undefined);
});
test('Geteilte UTF-8-Zeichen bleiben in der gesprochenen Antwort erhalten', async () => {
  const stream = Buffer.from(JSON.stringify({ type: 'item.completed', item: { type: 'agent_message', text: 'Grüße 👋' } })
    + '\n' + JSON.stringify({ type: 'turn.completed' }));
  const spawnProcess = () => {
    const child = new EventEmitter();
    child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
    child.stdin.on('finish', () => queueMicrotask(() => {
      for (const byte of stream) child.stdout.write(Buffer.from([byte]));
      child.emit('close', 0);
    }));
    return child;
  };
  assert.equal((await codexAntwort('Hallo', { spawnProcess })).antwort, 'Grüße 👋');
});
test('Teilantwort und Anbieterfehler werden nicht als Erfolg ausgegeben', async () => {
  const partial = JSON.stringify({ type: 'item.completed', item: { type: 'agent_message', text: 'Teil' } });
  for (const lines of [[partial], [partial, JSON.stringify({ type: 'turn.failed', error: { message: 'Geheimnis' } })]]) {
    const result = await codexAntwort('Hallo', { spawnProcess: fake(lines) });
    assert.ok(result.fehler); assert.equal(result.antwort, undefined);
    assert.ok(!result.fehler.includes('Geheimnis'));
  }
});
