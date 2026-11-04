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

## Offene Zweige

Nichts davon ist in `main`. Jeder Zweig baut eine eigene Test-App
(„Jarvis Test", Paket `de.art.jarvis.test`). Alle Test-Apps haben dieselbe
Paket-ID – eine ältere lässt sich nicht über eine neuere installieren, dann
vorher deinstallieren.

| Zweig | PR | Was drin ist | Was Art prüfen muss |
|---|---|---|---|
| `gespraech-modus` | #5 | Gesprächsmodus über Gemini Live: Protokoll, Mikrofon mit Echo-Unterdrückung, Tonausgabe, Live-Verbindung, Kostenzähler | Redet Jarvis sich selbst ins Wort? Bleibt die Verbindung stehen? Kostenzähler plausibel? |
| `tft-coach` | #3 | Fragen warten nie auf die Meta, Erkennung „neue Partie", schärferes Spielbild | Coaching während einer echten Partie |
| `wachwort-jarvis` | #2 | Wachwort „Jarvis" statt „Hey Jarvis" (selbst trainiertes Modell) | Löst es im Alltag falsch aus? Wird „Jarvis" sicher erkannt? |
| `jarvis-haende` | #1 | Erste „Hände": gemeinsame Websuche für Alltag und TFT-Meta, Kalender | Findet Jarvis Termine? Stimmen Suchergebnisse? |
| `agent-ruckgrat` | #6 | Agent-Rückgrat: Aufträge gehen an Claude Code in Termux (Abo statt Guthaben), Brücke über `127.0.0.1`, Einstellungen, Einrichtungsskript | Termux einrichten (`agent/README.md`), dann „Übernimm das: …“ sagen: kommt eine Antwort? Bleibt der Dienst im Hintergrund stehen? |

Reihenfolge der Empfehlung: erst #2 und #1, dann #3, dann #5, zuletzt #6.
#4 (Doku) ist am 4. November in `main` zusammengeführt.

## Was als Nächstes kommt

1. **Agenten-Rückgrat, Baustein 2.** Baustein 1 liegt im Zweig `agent-ruckgrat`
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
