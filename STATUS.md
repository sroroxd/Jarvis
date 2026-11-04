# Stand der Dinge

Diese Datei ist das Gedächtnis des Projekts. Sie steht hier, damit der
Projektstand nicht in einzelnen Chats liegt: Art arbeitet in **einem** Chat, und
wird der zu lang, fängt er einen neuen an. Claude liest zu Beginn jeder Sitzung
`CLAUDE.md` (Arbeitsregeln) und diese Datei (Stand) und ist damit auf dem
aktuellen Stand – egal, in welchem Fenster.

Wer etwas fertigstellt oder entscheidet, trägt es hier ein. Letzte Änderung:
4. November 2026.

## Was auf `main` läuft

Die APK aus `main` ist die Version auf Arts Galaxy S24 Ultra:

- Wachwort „Hey Jarvis" offline auf dem Handy (openWakeWord, ONNX Runtime),
  Empfindlichkeit einstellbar, pausiert bei ausgeschaltetem Bildschirm
- Sprachein- und -ausgabe; Stimme offline über SherpaTTS, Miro (high),
  Tempo 0,95, Tonhöhe 0,9, Sprechpause 1,5 s
- Claude als Gehirn über eigenen API-Schlüssel (Gemini als Alternative)
- Bildschirm teilen, Untertitelleiste, schwebendes J (Standard aus)
- Spielmodus TFT: verdeckt nichts, Antworten nur per Stimme
- Tägliche TFT-Meta per Claude-Websuche, Fragen warten nicht darauf
- Gedächtnis mit „Merk dir …" / „Vergiss …", einsehbar in den Einstellungen

## Offene Zweige – zusammengefasst in `sammeltest`

Alle offenen Zweige sind im Zweig `sammeltest` zusammengeführt (PR #7). Art
testet **eine** App statt fünf und spart das Deinstallieren dazwischen:
github.com/sroroxd/Jarvis/releases/download/test-sammeltest/JarvisTest.apk
(vorher eine alte „Jarvis Test“ deinstallieren – alle Test-Apps haben die
Paket-ID `de.art.jarvis.test`).

| Zweig | PR | Was drin ist |
|---|---|---|
| `wachwort-jarvis` | #2 | Wachwort „Jarvis“ statt „Hey Jarvis“ (selbst trainiertes Modell) |
| `jarvis-haende` | #1 | Websuche für Alltag und TFT-Meta, Kalender |
| `tft-coach` | #3 | Fragen warten nie auf die Meta, „Neue Partie“, schärferes Spielbild |
| `gespraech-modus` | #5 | Gemini-Live-Baustein. Art nutzt Gemini nicht (4. Nov.) – in `sammeltest` wieder entfernt, steht nur noch in der Git-Historie. Das Dauergespräch läuft über Claude. |
| `agent-ruckgrat` | #6 | Aufträge an Claude Code in Termux (Abo statt Guthaben) |

**Zusammenführen:** Nach Arts Test geht `sammeltest` als Ganzes nach `main` –
mit **Merge-Commit, nicht Squash**. Dann enthält `main` die Original-Commits
aller Zweige, und GitHub schließt #1, #2, #3, #5 und #6 von selbst als
zusammengeführt. `main` bekommt damit genau den Stand, den Art getestet hat.

#4 (Doku) ist am 4. November in `main` zusammengeführt.

### Testliste für Art

1. **Wachwort:** „Jarvis“ sagen – hört er zu? Löst er im Alltag falsch aus?
2. **Hände:** „Was steht morgen in meinem Kalender?“, „Trag mir Freitag 18 Uhr
   Training ein“, eine Frage, die eine Websuche braucht.
3. **TFT:** „Spielmodus an“ – antwortet er sofort? In einer Partie fragen,
   „Neue Partie“ sagen.
4. **Dauergespräch (Claude):** einmal auf den Kern tippen – ab dann nach jeder
   Antwort von selbst weiter zuhören, ohne erneut zu tippen. Nochmal tippen oder
   „Gespräch beenden“ beendet es; nach 15 Minuten ohne Wort endet es von selbst.
   Auch per Sprache: „Hör durchgehend zu“ oder in eigenen Worten (Claude schaltet
   dann selbst). Mit geteiltem Bildschirm bekommt jede Frage ein frisches Bild.
   Bildschirm nachträglich freigeben: oben auf „Bildschirm“ tippen.
5. **Agent:** Termux einrichten (`agent/README.md`), Token eintragen, „Übernimm
   das: leg mir eine Datei einkaufsliste.txt mit Milch und Brot an“.

## Aufräumen (geprüft am 4. November)

Repo geprüft: keine fremden Pushes, keine echten Schlüssel im Code, keine
fehlgeschlagenen Builds (nur zwei absichtlich abgebrochene Trainingsläufe).
Aus der Entwicklungsumgebung lässt sich nichts löschen – das macht Art im Browser:

- **Zweig** `claude-md-doku-regeln` – ist in `main` (#4).
- **Vorab-Versionen** `test`, `test-wachwort-jarvis`, `test-tft-coach`,
  `test-jarvis-haende` – ersetzt durch `test-sammeltest`.
- **Alte App-Versionen** `v1.0.1` bis `v1.0.26` – als Rückfall genügen
  `v1.0.43`, `v1.0.32`, `v1.0.29`.
- **Nicht löschen:** `vorschau` (Design-Vorschau schreibt dorthin), `test-sammeltest`.

Nach dem Zusammenführen von #7 können die Zweige `wachwort-jarvis`,
`jarvis-haende`, `tft-coach`, `gespraech-modus`, `agent-ruckgrat` und
`sammeltest` weg.

## Was als Nächstes kommt

1. **Agenten-Rückgrat, Baustein 2.** Baustein 1 liegt in `sammeltest`
   (Aufträge über Claude Code in Termux, Abo statt Guthaben). Als Nächstes:
   Zwischenstände während langer Aufträge (`--output-format stream-json`), eigene
   Skill-Ordner im Arbeitsordner, und das automatische Erkennen von Aufgaben, die
   zum Agenten gehören – bisher muss Art sie ausdrücklich abgeben.
2. **Design und Bedienung.** Die Oberfläche wirkt billig und soll überarbeitet
   werden.
3. **Stimmklon Deutsch,** näher an der Jarvis-Stimme aus den Iron-Man-Filmen.
4. **TFT-Live-Bild** statt einzelner Bildschirmfotos pro Frage.

## Spielregeln, kurz

Ausführlich in `CLAUDE.md`. Das Wichtigste:

- `main` bleibt jederzeit lauffähig, nie direkt dorthin pushen.
- Jede Änderung auf einem eigenen Zweig mit Pull Request.
- Nach `main` erst, wenn Art die Test-App auf dem Handy geprüft hat.
- Nichts als erledigt melden, was nicht gebaut oder getestet wurde. Was sich in
  der Entwicklungsumgebung nicht prüfen lässt (Mikrofon, Echo, Akku,
  Erkennungsqualität), wird ausdrücklich als ungeprüft benannt.
- Zweige und Releases löscht Art im Browser.

## Agent-Rückgrat, kurz

Lange Aufgaben macht nicht die App, sondern ein Agent auf demselben Handy:
Claude Code in Termux (Ubuntu über `proot-distro`), erreichbar über
`127.0.0.1:8777` mit Token. Das kostet Abo statt API-Guthaben. Die schnellen
Wege (Stimme, Gespräch, Spielmodus) bleiben bei der Claude-API, weil der Agent
dafür zu träge ist. Einrichtung und Fehlersuche: `agent/README.md`.
