# Stand der Dinge

Diese Datei ist das Gedächtnis des Projekts. Sie steht hier, damit der
Projektstand nicht in einzelnen Chats liegt: Art arbeitet in **einem** Chat, und
wird der zu lang, fängt er einen neuen an. Claude liest zu Beginn jeder Sitzung
`CLAUDE.md` (Arbeitsregeln) und diese Datei (Stand) und ist damit auf dem
aktuellen Stand – egal, in welchem Fenster.

Wer etwas fertigstellt oder entscheidet, trägt es hier ein. Letzte Änderung:
8. November 2026.

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

Getestet am 5. Nov. (Test-Build 50, Video): Dauergespräch läuft – „Sprach-Thematik
vorerst okay“. Daraus drei Wünsche, umgesetzt ab Test-Build 51:

1. **Dazwischenreden:** Im Dauergespräch während Jarvis spricht einfach losreden –
   bricht er sofort ab und hört zu? Unterbricht er sich selbst (Lautsprecher laut)?
   Dann Einstellungen → „Dazwischenreden unterbricht Jarvis“ aus und Bescheid geben.
2. **TFT-Coach:** keine Optionen, ein Befehl mit „Danach:“; Reihenfolge
   Taktikerleben → Gold → Stufe → Ziel-Comp; gezielte Rückfrage statt Raten.
3. **Wünsche direkt ins Repo:** GitHub-Schlüssel eintragen (Einstellungen →
   „Wünsche an die Entwicklung“), dann „Notier für die Entwicklung: …“ sagen oder
   einen Wunsch in eigenen Worten äußern → erscheint als Issue „Wunsch: …“.

Weiter offen: Wachwort im Alltag, Hände (Kalender, Websuche), Agent in Termux.

## Aufräumen (geprüft am 4. November)

Repo geprüft: keine fremden Pushes, keine echten Schlüssel im Code, keine
fehlgeschlagenen Builds (nur zwei absichtlich abgebrochene Trainingsläufe).
Erledigt von Art: Zweig `claude-md-doku-regeln` gelöscht, „Automatically delete
head branches“ eingeschaltet (zusammengeführte Zweige verschwinden jetzt von selbst).

Alte Releases: **GitHub → Actions → „Alte Releases aufräumen“ → Run workflow**
(PR #8, auf `main`). Löscht `test`, `test-wachwort-jarvis`, `test-tft-coach`,
`test-jarvis-haende` und `v1.0.1` bis `v1.0.26` samt Git-Tags. Geschützt:
`v1.0.43`, `v1.0.32`, `v1.0.29`, `vorschau`, `test-sammeltest`. Starten muss Art
selbst – Löschen ist aus der Entwicklungsumgebung gesperrt.

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

## Verfeinerung – Recherche vom 7. November

Geprüft gegen den Code und die aktuelle Doku (Anthropic, Android). Reihenfolge nach Nutzen:

1. **Prompt-Caching – gebaut, PR #13.** Die Uhrzeit stand auf die Minute in der
   Grundanweisung, dadurch war jede Anfrage neu. Jetzt steht sie als `[KONTEXT: …]`
   vor der Frage, Grundanweisung und Werkzeuge kommen aus dem Zwischenspeicher
   (Sonnet 5.5: Lesen 0,10 statt 2 USD pro Mio. Tokens). Anzeige auf dem
   Startbildschirm: „Cache … gelesen“.
2. **Antworten streamen und satzweise sprechen.** Heute wartet Jarvis auf die
   komplette Antwort und spricht sie dann am Stück. Mit Streaming (SSE,
   `content_block_delta`/`text_delta`) kann der erste Satz gesprochen werden,
   während Claude noch schreibt. Steuerzeilen (`[WUNSCH: …]`, `[MERKEN: …]`,
   `[DAUERGESPRÄCH: …]`) stehen am Ende und müssen zurückgehalten werden. Größter
   Gewinn bei der gefühlten Wartezeit.
3. **Dazwischenreden an der Wurzel.** Die Echo-Unterdrückung sitzt im
   Aufnahmepfad `VOICE_COMMUNICATION`. Laut mehreren Berichten greift sie auf
   Samsung-Geräten aber erst im Modus `MODE_IN_COMMUNICATION`, und Jarvis spielt
   seine Stimme bisher als normale Ausgabe ab. Plan: im Dauergespräch
   Kommunikationsmodus, Ausgabe als Sprachkommunikation, Lautsprecher per
   `setCommunicationDevice` (ab API 31). Zusätzlich Silero-VAD (ca. 2 MB,
   ONNX Runtime ist schon im Projekt) statt reiner Lautstärke, damit Spielsound
   nicht als Sprechen zählt. Ungeprüft bis zum Gerätetest: Klang und
   Lautstärkeregelung im Kommunikationsmodus.
4. **Bildkosten passen.** Ein Bildschirmfoto kostet im Alltag 1.012 Tokens
   (0,2 Cent), im Spielmodus 2.208 Tokens (0,44 Cent). Gerechnet mit der
   Formel aus der Doku: aufgerundete Breite/28 mal aufgerundete Höhe/28.
   Kein Handlungsbedarf.

## Gesamtprüfung vom 8. November – Zweig `ccr-4f440a4a-dx81et`

Den ganzen Code (`main` und die Zweig-Kette #7 → #16) durchgesehen, gebaut und getestet. Die Korrekturen
sitzen oben auf `selbst-update` (#16), damit sie nicht mit der Kette kollidieren.

**Behoben:**

1. **Jarvis bricht sich mitten im Satz selbst ab** (Wünsche #11, #18, #20). Ursache in `SpeechGate`:
   In jeder Sprechpause zwischen zwei Sätzen sank die Echo-Schätzung Richtung Stille; ab rund 300 ms
   Pause galt der nächste, gleich laute Satz als Art. In der Simulation: Abbruch in 199 von 200 Läufen.
   Zweiter Fehler: Gelernt wurde ab dem ersten Block, auch wenn die Stimme noch gar nicht lief. Jetzt
   lernt die Schranke erst ab dem Start der Stimme und senkt die Schätzung nie. Regressionstests dazu.
2. **Dazwischenreden unzuverlässig** (Wünsche #17, #19). Die Schwelle lag 14 dB über dem Echo – eine
   Reaktion auf Fehler 1. Jetzt 10 dB. Zusätzlich hört das Dazwischenreden-Mikrofon (mit
   Echo-Unterdrückung) auf „Jarvis“; vorher war das Wachwort beim Sprechen im Dauergespräch ganz aus,
   obwohl der Startbildschirm „„Jarvis“ unterbricht“ anzeigte.
3. **Kürzere Antworten im Spielmodus** (Wunsch #21): höchstens zwei kurze Sätze, zusammen höchstens
   zwanzig Wörter, keine Begründung ohne Nachfrage.
4. **Schlüssel weg nach Wiederherstellen:** Nach dem Einlesen einer Sicherung mit Schlüsseln schrieb das
   Verlassen der Einstellungen die alten (leeren) Felder zurück. Ebenso überschrieb das Gedächtnisfeld,
   was Jarvis sich gemerkt hatte, während die Einstellungen offen waren.
5. **Sicherungsdatei nach jeder Antwort neu geschrieben** – auf dem Haupt-Thread, direkt vor dem
   Sprechen. Jetzt nur, wenn sich das Gedächtnis ändert.
6. **„du“ und „Sie“ gemischt:** Jarvis siezt laut Grundanweisung, die festen Ansagen der App duzten.
   Alle gesprochenen Meldungen jetzt per „Sie“.
7. Kleinkram: Cache-Anzeige wurde von der Meta-Recherche überschrieben; Kalender-Werkzeug verwies
   auf die Uhrzeit „in der Systemanweisung“ (steht seit #13 in der `[KONTEXT]`-Zeile).

**Geprüft:** Build (Release und Debug), alle 129 Unit-Tests inklusive Wachwort mit echten Aufnahmen.
**Ungeprüft – nur Arts Handy zeigt es:** ob Echo und Spielsound jetzt unter der Schwelle bleiben, ob
Art in normaler Lautstärke durchkommt und ob „Jarvis“ auf dem echobereinigten Ton zuverlässig erkannt wird.
Unterbricht Jarvis sich weiter selbst, ist der nächste Schritt Punkt 3 der Verfeinerung oben
(Kommunikationsmodus für die Echo-Unterdrückung auf Samsung).

**Vorschläge, nicht umgesetzt:**

- **Signaturschlüssel tauschen.** Er liegt öffentlich im Repo. Seit dem Selbst-Update wiegt das schwerer:
  Jede APK, die mit diesem Schlüssel signiert ist, installiert sich über Jarvis und erbt alle
  gespeicherten API-Schlüssel. Ein neuer Schlüssel heißt einmal deinstallieren und neu installieren
  (Gedächtnis über die Sicherungsdatei zurückholen). Entscheidung von Art.
- **`main` nachziehen.** `main` liegt sieben PRs hinter dem getesteten Stand. Je länger die Kette, desto
  größer das Risiko beim Zusammenführen.
- Das Bildschirmfoto wird auf dem Haupt-Thread umgewandelt und als JPEG komprimiert (nicht gemessen);
  das lässt sich auf den Aufnahme-Thread verlegen, damit die Oberfläche dabei nicht stockt.
- Selbst-Update: Verlangt Android eine Bestätigung, während Jarvis im Hintergrund ist, unterdrückt Android
  den Dialog. Dann einfach die App öffnen und erneut auf „Installieren“ tippen.

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
