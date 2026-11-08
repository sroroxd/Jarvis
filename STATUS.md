# Stand der Dinge

Diese Datei ist das Gedächtnis des Projekts. Sie steht hier, damit der
Projektstand nicht in einzelnen Chats liegt: Art arbeitet in **einem** Chat, und
wird der zu lang, fängt er einen neuen an. Claude liest zu Beginn jeder Sitzung
`CLAUDE.md` (Arbeitsregeln) und diese Datei (Stand) und ist damit auf dem
aktuellen Stand – egal, in welchem Fenster.

Wer etwas fertigstellt oder entscheidet, trägt es hier ein. Letzte Änderung:
8. November 2026.

## Wo der aktuelle Stand liegt – ein Zweig, ein PR

Seit dem 8. November gibt es **einen** Arbeitszweig: `ccr-4f440a4a-dx81et`, **PR #22 nach `main`**.
Er enthält alle früheren Zweige und PRs (#1–#7, #12–#16) plus die Korrekturen der Gesamtprüfung und das
Streaming. Die alte Kette (`sammeltest` → `fix-dazwischenreden-abbruch` → `feinschliff-cache` →
`geraetetest-schranke` → `gespraechstest-ci` → `selbst-update`) wird nicht mehr weitergeführt.

- **Test-App:** github.com/sroroxd/Jarvis/releases/download/test-ccr-4f440a4a-dx81et/JarvisTest.apk –
  ab Build 63 aktualisiert sie sich selbst (Knopf „Installieren“ auf dem Startbildschirm).
- **Zusammenführen** nach Arts Test: #22 mit **Merge-Commit, nicht Squash** – dann landen die
  Original-Commits in `main`. Die alten PRs #1–#7 und #12–#16 sind seit dem 8. Nov. geschlossen
  („in #22 aufgegangen“), ihre Commits stecken vollständig in #22.
- Neue Arbeit kommt auf diesen Zweig, solange #22 offen ist – keine neue Kette.

**`main`** ist noch die ältere Version (Wachwort „Hey Jarvis“, Spielmodus, Tages-Meta, Gedächtnis).

### Was Art auf dem Handy prüfen muss (Test-Build ab 64)

1. **Satzweises Sprechen:** Fängt Jarvis bei längeren Antworten spürbar früher an zu sprechen? Klingen die
   Übergänge zwischen den Sätzen natürlich, oder entstehen Pausen?
2. **Dazwischenreden:** Unterbricht er sich noch selbst? Kommst du in normaler Lautstärke durch, und
   unterbricht „Jarvis“ zuverlässig?
3. **Selbst-Update:** Läuft das nächste Update über den Knopf „Installieren“ durch?
4. **Spielmodus:** Sind die Antworten jetzt kurz genug (#21)?

## Aufräumen (8. November)

**Erledigt (Claude):** Die elf überholten PRs geschlossen. Die Wunsch-Issues zusammengefasst –
16 offene Wünsche waren 5 Themen: #10 (Selbstabbruch beim Sprechen), #17 (Dazwischenreden
zuverlässig), #21 (kürzere Antworten), #23 (TFT-Coaching hat Vorrang), #25 (Empfindlichkeit für
„Jarvis“ und Dazwischenreden einstellbar); die übrigen sind als Duplikat darauf verwiesen. Ursache der
Doppelten in der App behoben: Sie schickte jeden Wunsch, auch wenn derselbe Titel schon notiert war, und
erkannte Umformulierungen desselben Themas nicht (`Wishes.similar`).

**Für Art, ein Klick** (Löschen ist aus der Entwicklungsumgebung gesperrt): GitHub → Actions →
„Alte Releases aufräumen“ → Run workflow → bei **„Use workflow from“ den Zweig
`ccr-4f440a4a-dx81et` wählen** → Run workflow. Löscht die elf alten Zweige (nur wenn jeder Commit in
`ccr-4f440a4a-dx81et` steckt – vorab geprüft: bei allen elf der Fall) und die Releases `test-selbst-update`,
`test-gespraechstest-ci`, `test-geraetetest-schranke`, `test-fix-dazwischenreden-abbruch`,
`test-feinschliff-cache`, `test-sammeltest`, `v1.0.29`, `v1.0.32` samt Tags. Danach stehen nur noch:
Zweige `main` und `ccr-4f440a4a-dx81et`, Releases `v1.0.43` (normale App), `test-ccr-4f440a4a-dx81et`
(Test-App) und `vorschau` (Gerätetest).

Früher erledigt (4. Nov.): Repo geprüft – keine fremden Pushes, keine echten Schlüssel im Code;
„Automatically delete head branches“ ist eingeschaltet.

## Was als Nächstes kommt

0. **Aus dem claude.ai-Chat „Jarvis APK Verbesserung“ (2.–8. Nov.) noch offen:** automatische
   TFT-Erkennung (Jarvis merkt per „Nutzungsdatenzugriff“, dass TFT läuft, schaltet den Spielmodus
   selbst ein und hört ohne Wachwort zu – von Art gewünscht am 2. Nov.), eigenes Symbol für die
   Test-App und den Schnellstart („Jarvis zuhören“ mit Mikrofon-Symbol).
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
2. **Antworten streamen und satzweise sprechen – gebaut am 8. Nov. (PR #22).** Vorher wartete Jarvis auf die
   komplette Antwort und sprach sie dann am Stück. Mit Streaming (SSE,
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
7. **Falsche Zahl, dann Selbstkorrektur** („Zweihundertzwei. Moment, Korrektur: … einhundertzwei“).
   Der Gesprächstest der CI scheiterte daran zweimal in Folge, und das Beispiel stand schon vorher im
   Testskript. Die Grundanweisung verlangte ausgeschriebene Zahlwörter, dabei verhaspelt sich das Modell.
   Jetzt Ziffern (jede Sprachausgabe liest sie vor); das Vorlesen verschluckt keine Zahl am Satzanfang
   mehr („51. …“ galt als Nummerierung).
8. **Selbst-Update scheiterte** („write failed: EBADF“, dann „INSTALL_FAILED_ABORTED“, Arts Test mit
   Build 59): Der Schreib-Stream wurde nach dem Übergeben der Installation ein zweites Mal geschlossen;
   der Fehler daraus ließ die Fehlerbehandlung die schon übergebene Installation verwerfen. Jetzt
   genau einmal schließen, dann übergeben (Reihenfolge laut Android-Doku); Übergebenes wird nie
   verworfen. Test-Apps bis Build 62 haben noch den alten Fehler – einmal von Hand installieren.
9. Kleinkram: Cache-Anzeige wurde von der Meta-Recherche überschrieben; Kalender-Werkzeug verwies
   auf die Uhrzeit „in der Systemanweisung“ (steht seit #13 in der `[KONTEXT]`-Zeile).

10. **Streaming mit satzweisem Sprechen** (8. Nov., Fortsetzung des claude.ai-Chats): Claude-Antworten kommen
    als Strom (`stream: true`); jeder fertige Satz geht sofort an die Handy-Stimme, der nächste wird
    hinten angestellt. Steuerzeilen in eckigen Klammern werden nie vorgelesen. Bricht Art ab, verstummt
    auch der Rest der noch laufenden Antwort; der Verlauf bleibt vollständig. „KI“ in der Zeitmessung ist
    jetzt die Wartezeit bis zum ersten Satz. ElevenLabs bekommt die Antwort weiter am Stück.
11. **`thinking: disabled` scheiterte bei Sonnet 5.5 mit Fehler 400** (laut Claude-Doku seit Sonnet 5.5
    nicht mehr erlaubt). Betroffen waren der Rückfall „Antwort ohne Text“ – er scheiterte damit immer –
    und der Schlüsseltest (lief nur über einen zweiten Versuch). Jetzt überall adaptives Nachdenken mit
    Stufe „low“, wie die Doku es empfiehlt.

**Geprüft:** Build (Release und Debug), alle 143 Unit-Tests inklusive Wachwort mit echten Aufnahmen.
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
