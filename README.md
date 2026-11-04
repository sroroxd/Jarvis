# Jarvis

Persönlicher Android-Assistent für das Galaxy S24 Ultra – mit Claude als Gehirn. Freihändig per „Jarvis“ oder als Dauergespräch, sieht den Bildschirm und antwortet mit Stimme. Lange Aufgaben übernimmt ein Agent auf dem Handy (Claude Code in Termux).

## Herunterladen

**Immer die neueste Version:** https://github.com/sroroxd/Jarvis/releases/latest/download/Jarvis.apk

Jede Änderung auf `main` wird automatisch getestet und als neue APK veröffentlicht. Sie lässt sich direkt über die alte installieren; Schlüssel, Gedächtnis und Einstellungen bleiben erhalten.

**Test-Apps von Arbeitszweigen:** Jeder andere Zweig baut die separate App **„Jarvis Test“** (Vorab-Version `test-<Zweig>`, z. B. https://github.com/sroroxd/Jarvis/releases/download/test-sammeltest/JarvisTest.apk). Sie installiert sich neben der normalen App, hat eigene Einstellungen und überschreibt nichts. Immer nur eine der beiden Apps einschalten, sonst reagieren beide auf das Wachwort.

**Welche Version läuft?** Steht oben auf dem Startbildschirm („Build N“ bzw. „Test-Build N“). Liegt auf GitHub eine neuere, meldet die App das beim Öffnen und bietet „Laden“ an.

## Einrichten

1. APK installieren („Aus dieser Quelle erlauben“ bestätigen).
2. Jarvis öffnen → **Zahnrad** (Einstellungen) → API-Schlüssel einfügen → **Schlüssel testen**. Jarvis erkennt den Anbieter selbst:
   - **Claude:** platform.claude.com → API Keys (beginnt mit `sk-ant-`)
   - Ein Gemini-Schlüssel (`AIza…`) funktioniert als Notlösung ebenfalls, ist aber nicht der vorgesehene Weg.
3. Das Feld **Modell** leer lassen: Claude Sonnet 5.5 – im Alltag ohne Nachdenken (schnell), im Spielmodus mit kurzem Nachdenken. Sparvariante: `claude-haiku-5-5` eintragen.
4. Zurück zum Startbildschirm, auf den **Kern** tippen → Mikrofon, Mitteilungen und „Über anderen Apps anzeigen“ (für Untertitel) erlauben → **Gesamter Bildschirm** teilen.
5. Optional: Seitentaste → Zweimal drücken → App öffnen → **Jarvis sprechen**.

## Bedienung

- **Dauergespräch:** einmal auf den **Kern** tippen (oder „Sprechen“ in der Benachrichtigung, Seitentaste, schwebendes J). Danach hört Jarvis nach jeder Antwort von selbst weiter zu – kein erneutes Tippen. Beenden: nochmal tippen oder „Gespräch beenden“; nach 15 Minuten ohne ein Wort endet es von selbst. Auch per Sprache: „Hör durchgehend zu“ oder „Dauergespräch an/aus“ – sagt man es in eigenen Worten, schaltet Claude selbst um.
- **„Jarvis“** sagen und lossprechen – für eine einzelne Frage. „Hey Jarvis“ geht auch. Pausen sind erlaubt – abgeschickt wird nach der eingestellten Sprechpause.
- **Während Jarvis spricht:** „Jarvis“ unterbricht und hört neu zu.
- **Kern lange drücken:** neues Gespräch (Verlauf und Zusammenhang werden geleert).
- **Bildschirm:** Chip „Bildschirm“ oben gibt den Bildschirm frei, ohne Neustart; ab Android 14 wird nur „Gesamter Bildschirm“ angeboten. Jede Frage bekommt ein frisches Bild vom Moment der Frage. Eine Bildschirmaufnahme des Handys kann die Freigabe beenden – das steht dann im Verlauf.
- **Verlauf:** unter dem Kern steht das ganze Gespräch, scrollbar.
- **Lange Aufgaben:** „Übernimm das: …“, „Auftrag: …“, „Kümmere dich um …“ gibt sie an den Agenten auf dem Handy (siehe `agent/README.md`).
- **Termine und Websuche:** „Was steht morgen an?“, „Trag mir Freitag 18 Uhr Training ein“; aktuelle Fragen beantwortet Claude mit Websuche.
- **„Spielmodus an“ / „Spielmodus aus“** schaltet per Sprache um – oder der Schalter auf dem Startbildschirm. Jeder Moduswechsel beginnt ein frisches Gespräch.
- **„Neue Partie“** (oder „neues Spiel“): Jarvis vergisst den Verlauf der letzten Partie, damit alte Pläne nicht weiterwirken; der Spielmodus geht dabei an.
- **„Merk dir …“** speichert dauerhaft, **„Vergiss …“** löscht. Alles einsehbar unter Einstellungen → Gedächtnis.

| Farbe des Kerns | Bedeutung |
|---|---|
| Graublau | Jarvis ist aus |
| Blau | bereit |
| Cyan | hört zu |
| Violett | denkt nach |
| Türkis | spricht |

### Spielmodus (TFT)

Nichts verdeckt das Spielbild: kein J, keine Fenster, Antworten nur per Stimme (kurz und knapp, TFT-Coach mit Claude Sonnet 5.5). Optional zeigt eine schmale, halbtransparente Untertitelleiste die Antwort – sie ist nicht anklickbar (Fingertipps gehen durch sie hindurch ins Spiel) und verschwindet nach wenigen Sekunden. Einstellbar unter Einstellungen → Anzeige.

Im Spielmodus schickt Jarvis ein schärferes Bildschirmfoto (1920 statt 1280 Pixel an der langen Kante, weniger Kompression), damit Gold, Stufe, Händler und kleine Gegenstände lesbar bleiben – Claude-Modelle ab 4.7 verarbeiten bis 2576 Pixel. Der Bildschirm-Mitschnitt selbst läuft dauerhaft nur in dieser Größe statt in voller Auflösung (weniger Akku und Wärme in langen Partien). Fehlt die Bildschirmfreigabe, sagt Jarvis das bei Fragen zur Partie zuerst, statt blind zu raten. Begriffe wie im deutschen Client: Phase, Stufe, Händler, Taktikerleben, Attribute.

### Anzeige

- **Schwebendes J:** Standard aus. Im Spielmodus immer ausgeblendet.
- **Untertitel:** im Alltag Standard an, im Spielmodus Standard aus; Position oben oder unten.

## Wachwort „Jarvis“

Die Erkennung läuft komplett offline auf dem Handy mit [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) – ohne Konto, ohne Schlüssel, ohne Kosten. Bei ausgeschaltetem Bildschirm pausiert sie (spart Akku, keine Fehlauslöser in der Tasche). Die Empfindlichkeit ist in den Einstellungen regelbar (Standard 5).

Für „Jarvis“ allein gibt es kein fertiges Modell, deshalb ist es selbst trainiert (`app/src/main/assets/wakeword/jarvis.onnx`, Messwerte in `training/wakeword/ergebnis/`):

- **Positiv:** zehntausende künstliche Sprecher (Piper-Stimmen, Englisch, Deutsch, Niederländisch) sagen „Jarvis“ – englisch und deutsch ausgesprochen („Dschaawis“), auch „Hey/Okay Jarvis“ und direkt weitergesprochen („Jarvis, was …“), mit Hall, Störgeräuschen und Handy-Klang.
- **Negativ:** ähnlich klingende Wörter („Ja, was …“, „Ja, wieso“, Travis, Service, Jahr …), Alltagssätze und rund 370 Stunden echtes Alltagsaudio (ACAV100M-Merkmale aus dem openWakeWord-Projekt).
- **Prüfung:** mit Stimmen, die im Training nie vorkamen, und mit 11 Stunden Validierungsaudio (Fehlalarme pro Stunde).

Neu trainieren: in einem **bestehenden Arbeitszweig** `training/wakeword/start.txt` ändern und pushen (oder GitHub → *Actions* → **Wachwort „Jarvis“ trainieren** → *Run workflow*). Nie auf `main` und nicht beim Anlegen eines Zweigs – das Training läuft stundenlang und committet sein Ergebnis in den Zweig. Drei Varianten laufen parallel; Modelle und Messwerte landen in `training/wakeword/ergebnis/`.

Reagiert Jarvis zu selten, die Empfindlichkeit in den Einstellungen auf 6 stellen; springt er grundlos an, auf 4.

Picovoice Porcupine wurde bewusst nicht genommen: Das kostenlose Angebot wurde zum 30.06.2026 eingestellt, ohne gültigen Schlüssel läuft es nicht.

Modelle: `melspectrogram.onnx` und `embedding_model.onnx` (Apache 2.0), `jarvis.onnx` mit openWakeWord-Daten trainiert (nur nicht-kommerziell). Das frühere `hey_jarvis_v0.1.onnx` von David Scripka (CC BY-NC-SA 4.0) liegt nur noch zum Vergleich unter `training/wakeword/referenz/`.

## Aktuelle TFT-Meta

Im Spielmodus recherchiert Jarvis **einmal am Tag** mit Claudes Websuche die aktuelle Meta (Set, Patch, stärkste Comps, Augments, Buffs/Nerfs) und nutzt sie den ganzen Tag als Wissensstand. Die Recherche läuft im Hintergrund, Fragen warten nicht darauf. Kosten: wenige Cent pro Tag. Manuell: Einstellungen → Spielmodus → „Meta jetzt aktualisieren“. Braucht einen Claude-Schlüssel.

## Stimme

**Premium (empfohlen, am nächsten am Film-Jarvis):** ElevenLabs-Schlüssel in den Einstellungen eintragen. Standardstimme ist „Daniel“ (britisch, tief, ruhig), die mit dem Modell *Multilingual v2* Deutsch mit leichtem britischem Einschlag spricht. Alternativ in ElevenLabs unter *Voice Design* eine eigene Stimme beschreiben und deren ID eintragen. Fällt ElevenLabs aus oder ist das Kontingent leer, spricht Jarvis automatisch mit der Handy-Stimme weiter. Der ElevenLabs-Schlüssel wird nicht in die Sicherungsdatei geschrieben.

**Handy-Stimme (offline):** Jarvis sucht automatisch in dieser Reihenfolge: **Miro** (deutsch, männlich) → **Thorsten** (deutsch, männlich) → **Alan** (britisch), Tempo 0,95, Tonhöhe 0,9. Deutsche Stimmen dafür in SherpaTTS herunterladen (z. B. „de_DE miro high“). Engine, Stimme, Tempo, Tonhöhe und Sprechpause sind in den Einstellungen wählbar („Probe hören“).

## Projektaufbau

| Datei | Aufgabe |
|---|---|
| `JarvisService` | Ablauf: Wachwort, Zuhören, Bildschirmfoto, Anfrage, Vorlesen, Spielmodus |
| `WakeWordEngine` | Mikrofon für das Wachwort „Jarvis“, Übergabe an die Spracherkennung |
| `wakeword/…/WakeWordDetector` | openWakeWord in reinem Java (eigenes Modul, mit Testaufnahmen geprüft) |
| `training/wakeword` | Training des Wachworts „Jarvis“ (läuft auf GitHub, siehe oben) |
| `MainActivity` | Startbildschirm: Status und Reaktor-Knopf |
| `ReactorView` | Animierter Sprechen-Knopf im Arc-Reaktor-Stil |
| `SettingsActivity` | Einstellungen in Gruppen |
| `Subtitles` | Nicht anklickbare Untertitelleiste |
| `FloatingJ` | Optionales schwebendes J |
| `Earcon` | Signalton beim Zuhören |
| `Commands` | Sprachbefehle der App („Spielmodus an/aus“, Dauergespräch, Aufträge an den Agenten) |
| `ChatLog` | Gesprächsverlauf auf dem Startbildschirm |
| `AgentClient`, `AgentReply` | Brücke zum Agenten in Termux (`127.0.0.1`, Token) |
| `UpdateCheck` | Fragt GitHub nach einer neueren Version |
| `ListenActivity` | Schnellstart „Jarvis sprechen“ (Seitentaste) |
| `ClaudeClient`, `GeminiClient` | Anbindung der KI; `ClaudeClient` mit Werkzeug-Schleife (Gemini nur als Notlösung) |
| `Tools`, `Agenda` | Jarvis' „Hände“: Kalender lesen und Termine eintragen (Websuche läuft direkt bei Anthropic) |
| `Persona` | Anweisungen: Ton, Kürze, Ehrlichkeit, Spielmodus |
| `Memory` | Dauerhaftes Gedächtnis („Merk dir“ / „Vergiss“) |
| `Text` | Textbereinigung, Schlüssel-/Modellprüfung, Fehlermeldungen |
| `Voices`, `ElevenTts` | Sprachausgabe (Handy / Premium) |
| `Backup` | Sicherungsdatei für Gedächtnis und Einstellungen |
| `ScreenGrabber` | Bildschirmaufnahme |
| `Prefs` | Gespeicherte Einstellungen |

Layouts liegen in `app/src/main/res/layout`, Farben und Theme in `res/values`.

Automatische Tests liegen in `app/src/test` und `wakeword/src/test` und laufen bei jedem Build. Auf Arbeitszweigen macht zusätzlich ein Emulator Bildschirmfotos (Vorab-Version „vorschau“).

## Ausbaustufen

- [x] **Stufe 1:** Sprechen, Bildschirm sehen, natürliche Antwort, TFT-Modus, Gedächtnis, Stimmwahl
- [x] Aktivierungswort „Jarvis“ ohne Antippen, Spielmodus ohne Einblendungen, neues Design
- [x] Dauergespräch, Termine und Websuche, Agent für lange Aufgaben (im Test)
- [ ] **Stufe 2:** Nachrichten vorlesen und beantworten, Erinnerungen, Wecker
- [ ] **Stufe 3:** Apps per Sprache steuern
