import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { once } from 'node:events';

// Echte HTTP-Anfragen an den echten Server; nur das externe Claude-Programm ist eine lokale Attrappe.
test('Token, Login, Abrechnung und geschützte Routen funktionieren zusammen', async (t) => {
  const dir = await mkdtemp(join(tmpdir(), 'jarvis-auth-'));
  const state = join(dir, 'auth.json');
  const calls = join(dir, 'calls');
  await writeFile(join(dir, 'claude'), `#!/usr/bin/env node
import {readFileSync, appendFileSync} from 'node:fs';
const args = process.argv.slice(2);
appendFileSync(process.env.FAKE_CALLS, args.join(' ') + '\\n');
const data = readFileSync(process.env.FAKE_AUTH, 'utf8');
if (args[0] === 'auth') {
  if (data === 'hang') setTimeout(() => {}, 60000);
  else if (data === 'error') { process.stderr.write('PRIVATE-ACCOUNT-DATA'); process.exitCode=1; }
  else process.stdout.write(data);
} else { process.stdout.write(JSON.stringify({type:'result', result:'Fertig.', session_id:'test-session', is_error:false})+'\\n'); }
`, { mode: 0o700 });
  await writeFile(calls, '');
  await writeFile(state, JSON.stringify({ loggedIn: true, authMethod: 'claude.ai', email: 'private@example.test' }));
  const token = 'test-token-abcdefghijklmnopqrstuvwxyz';
  const child = spawn(process.execPath, [resolve('agent/jarvis-agent.mjs')], {
    env: { ...process.env, PATH: dir + ':' + process.env.PATH, FAKE_AUTH: state, FAKE_CALLS: calls,
      JARVIS_AGENT_HOME: join(dir, 'home'), JARVIS_AGENT_WORKDIR: join(dir, 'work'),
      JARVIS_AGENT_TOKEN: token, JARVIS_AGENT_PORT: '0', JARVIS_AGENT_AUTH_TIMEOUT_MS: '200' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  t.after(async () => {
    if (child.exitCode === null) { child.kill('SIGTERM'); await once(child, 'exit'); }
    await rm(dir, { recursive: true, force: true });
  });
  let logs = '';
  let errors = '';
  child.stderr.on('data', b => { errors += b; });
  const port = await new Promise((accept, reject) => {
    const timer = setTimeout(() => reject(new Error('Serverstart fehlgeschlagen: ' + errors)), 5000);
    child.once('exit', () => { clearTimeout(timer); reject(new Error('Server beendet: ' + errors)); });
    child.stdout.on('data', b => {
      logs += b;
      const match = logs.match(/http:\/\/127\.0\.0\.1:(\d+)/);
      if (match) { clearTimeout(timer); accept(Number(match[1])); }
    });
  });
  const request = (path, auth = token, body) => fetch(`http://127.0.0.1:${port}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: auth ? { authorization: 'Bearer ' + auth, 'content-type': 'application/json' } : {},
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const status = await (await request('/status', '')).json();
  assert.deepEqual(Object.keys(status).sort(), ['bereit', 'version']);
  for (const [path, body] of [['/pruefen'], ['/fortschritt'], ['/neu', {}], ['/auftrag', {text:'Test'}]]) {
    for (const wrong of ['', 'falsch', token + 'x']) assert.equal((await request(path, wrong, body)).status, 401);
  }
  assert.equal(await readFile(calls, 'utf8'), '', 'Unbefugte Anfragen starten keinen Prozess');
  const check = await request('/pruefen');
  assert.equal(check.status, 200);
  assert.deepEqual(await check.json(), { bereit: true, angemeldet: true, beschaeftigt: false });
  assert.doesNotMatch(await readFile(calls, 'utf8'), /-p/, 'Prüfung verbraucht keine Modellanfrage');
  for (const data of [JSON.stringify({loggedIn:false,authMethod:'none'}),
    JSON.stringify({loggedIn:true,authMethod:'api_key'}), JSON.stringify({loggedIn:true,authMethod:'third_party'}),
    'kein json', 'error', 'hang']) {
    await writeFile(state, data);
    const response = await request('/pruefen');
    assert.equal(response.status, 503, data);
    assert.doesNotMatch(await response.text(), /PRIVATE-ACCOUNT-DATA|private@example/);
    assert.equal((await request('/auftrag', token, {text:'Test'})).status, 503);
  }
  assert.doesNotMatch(await readFile(calls, 'utf8'), /-p/, 'Ohne Abo-Anmeldung läuft kein Auftrag');
  await writeFile(state, JSON.stringify({loggedIn:true,authMethod:'oauth_token'}));
  const result = await request('/auftrag', token, {text:'Test'});
  assert.equal(result.status, 200);
  assert.equal((await result.json()).antwort, 'Fertig.');
  assert.equal((await request('/neu', token, {})).status, 200);
  assert.equal((await request('/fortschritt')).status, 200);
  assert.equal((await request('/nicht-vorhanden')).status, 404);
});
