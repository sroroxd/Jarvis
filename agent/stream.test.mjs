// Lokaler Test der Parse-Logik: `node --test agent/stream.test.mjs`
// (Läuft nicht in der Android-CI – der Agent ist Laufzeit auf dem Handy, nicht Teil des App-Builds.)
import test from 'node:test';
import assert from 'node:assert/strict';
import { taetigkeit, kurz, verarbeite } from './stream.mjs';

test('taetigkeit: Werkzeug → gesprochene Tätigkeit', () => {
  assert.equal(taetigkeit('Bash'), 'führe gerade einen Befehl aus');
  assert.equal(taetigkeit('Read'), 'sehe mir Dateien an');
  assert.equal(taetigkeit('Grep'), 'sehe mir Dateien an');
  assert.equal(taetigkeit('Edit'), 'schreibe an einer Datei');
  assert.equal(taetigkeit('WebSearch'), 'suche im Internet');
  assert.equal(taetigkeit('Task'), 'arbeite an einer Teilaufgabe');
  assert.equal(taetigkeit('TodoWrite'), 'plane die nächsten Schritte');
  assert.equal(taetigkeit('IrgendwasNeues'), 'arbeite daran');
  assert.equal(taetigkeit(undefined), 'arbeite daran');
});

test('kurz: erster Satz, gekürzt', () => {
  assert.equal(kurz('Ich sehe mir das an. Dann weiter.'), 'Ich sehe mir das an.');
  assert.equal(kurz('  mehrere   Leerzeichen\nund Zeile '), 'mehrere Leerzeichen und Zeile');
  const lang = 'a'.repeat(200);
  const k = kurz(lang);
  assert.ok(k.length <= 120, 'höchstens 120 Zeichen');
  assert.ok(k.endsWith('…'), 'mit Auslassung gekürzt');
  assert.equal(kurz(''), '');
});

test('verarbeite: Schritte zählen, Fortschritt und Ergebnis', () => {
  const z = { fortschritt: '', schritte: 0, ergebnis: null };
  verarbeite('{"type":"system","subtype":"init","session_id":"s1"}', z);
  assert.equal(z.schritte, 0);
  assert.equal(z.ergebnis, null);

  verarbeite('{"type":"assistant","message":{"content":[{"type":"text","text":"Ich sehe mir das an."},{"type":"tool_use","name":"Read","input":{}}]}}', z);
  assert.equal(z.schritte, 1);
  assert.equal(z.fortschritt, 'sehe mir Dateien an');

  verarbeite('{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{}}]}}', z);
  assert.equal(z.schritte, 2);
  assert.equal(z.fortschritt, 'führe gerade einen Befehl aus');

  verarbeite('{"type":"result","subtype":"success","result":"Fertig, Sir.","session_id":"s1","total_cost_usd":0.01,"duration_ms":1234,"num_turns":2,"is_error":false}', z);
  assert.ok(z.ergebnis, 'Ergebnis-Ereignis gemerkt');
  assert.equal(z.ergebnis.result, 'Fertig, Sir.');
  assert.equal(z.ergebnis.num_turns, 2);
});

test('verarbeite: unlesbare und leere Zeilen werden übersprungen', () => {
  const z = { fortschritt: 'start', schritte: 3, ergebnis: null };
  verarbeite('kein json', z);
  verarbeite('{kaputt', z);
  verarbeite('', z);
  assert.equal(z.fortschritt, 'start');
  assert.equal(z.schritte, 3);
  assert.equal(z.ergebnis, null);
});

test('verarbeite: reiner Textschritt setzt sprechbaren Fortschritt', () => {
  const z = { fortschritt: '', schritte: 0, ergebnis: null };
  verarbeite('{"type":"assistant","message":{"content":[{"type":"text","text":"Lade die Daten herunter, das dauert einen Moment."}]}}', z);
  assert.equal(z.fortschritt, 'Lade die Daten herunter, das dauert einen Moment.');
  assert.equal(z.schritte, 0);
});
