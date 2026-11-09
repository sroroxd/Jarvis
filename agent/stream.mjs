/**
 * Reine Parse-Logik für den stream-json-Strom von „claude -p --output-format stream-json“.
 * Ohne Globals und ohne Netzwerk – deshalb mit `node --test` prüfbar (siehe stream.test.mjs).
 *
 * Der Strom ist eine JSON-Zeile je Ereignis:
 *   {"type":"system",...}  {"type":"assistant","message":{content:[...]}}  {"type":"result",...}
 * Aus den „assistant“-Ereignissen gewinnen wir den sprechbaren Zwischenstand (Werkzeug-Aufrufe
 * und kurze Textstücke), aus „result“ das Endergebnis.
 */

/** Werkzeug-Name von Claude Code → gesprochene Tätigkeit (Ich-Form, für die Ansage). */
export function taetigkeit(name) {
  const n = String(name || '').toLowerCase();
  if (n === 'bash' || n.includes('terminal')) return 'führe gerade einen Befehl aus';
  if (n === 'read' || n === 'glob' || n === 'grep' || n === 'ls') return 'sehe mir Dateien an';
  if (n === 'write' || n === 'edit' || n === 'multiedit' || n === 'notebookedit') return 'schreibe an einer Datei';
  if (n === 'websearch' || n === 'webfetch') return 'suche im Internet';
  if (n === 'task') return 'arbeite an einer Teilaufgabe';
  if (n === 'todowrite') return 'plane die nächsten Schritte';
  return 'arbeite daran';
}

/** Erster Satz, höchstens ~120 Zeichen – für die gesprochene Zwischenansage. */
export function kurz(text) {
  const satz = String(text || '').replace(/\s+/g, ' ').trim();
  const ende = satz.search(/[.!?](\s|$)/);
  let s = ende > 0 ? satz.slice(0, ende + 1) : satz;
  if (s.length > 120) s = s.slice(0, 117) + '…';
  return s;
}

/**
 * Verarbeitet eine stream-json-Zeile und aktualisiert den Zustand.
 * zustand: { fortschritt: string, schritte: number, ergebnis: object|null }
 * Unlesbare oder uninteressante Zeilen werden still übersprungen.
 */
export function verarbeite(zeile, zustand) {
  let j;
  try { j = JSON.parse(zeile); } catch { return; }
  if (j.type === 'assistant' && j.message && Array.isArray(j.message.content)) {
    for (const teil of j.message.content) {
      if (teil && teil.type === 'tool_use') {
        zustand.schritte += 1;
        zustand.fortschritt = taetigkeit(teil.name);
      } else if (teil && teil.type === 'text' && teil.text && teil.text.trim()) {
        zustand.fortschritt = kurz(teil.text);
      }
    }
  } else if (j.type === 'result') {
    zustand.ergebnis = j;
  }
}
