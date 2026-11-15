import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {requestHandler} from './jarvis-realtime-test.mjs';

async function withServer(run) {
  const server = http.createServer((req, res) => requestHandler(req, res, 'unused-test-key'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const address = server.address();
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
}

test('unbekannte Besucher dürfen die Seite nicht lesen', () => withServer(async url => {
  const response = await fetch(url);
  assert.equal(response.status, 403);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(await response.text(), 'Zugriff verweigert');
}));

test('unbekannte Besucher erhalten kein kostenpflichtiges Sitzungstoken', () => withServer(async url => {
  const response = await fetch(`${url}/token`, {method:'POST', headers:{Origin:'https://attacker.example'}});
  assert.equal(response.status, 403);
  assert.equal(await response.text(), 'Zugriff verweigert');
}));

test('falscher Startlink setzt kein Zugriffscookie', () => withServer(async url => {
  const response = await fetch(`${url}/?access=wrong`, {redirect:'manual'});
  assert.equal(response.status, 403);
  assert.equal(response.headers.get('set-cookie'), null);
}));
