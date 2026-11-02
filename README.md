# Jarvis

Persönlicher Android-Assistent für das Galaxy S24 Ultra – mit Claude (oder Gemini) als Gehirn. Freihändig per „Hey Jarvis“, sieht den Bildschirm und antwortet mit Stimme.

## Herunterladen

**Immer die neueste Version:** https://github.com/sroroxd/Jarvis/releases/latest/download/Jarvis.apk

Jede Änderung auf `main` wird automatisch getestet und als neue APK veröffentlicht. Sie lässt sich direkt über die alte installieren; Schlüssel, Gedächtnis und Einstellungen bleiben erhalten.

## Einrichten

1. APK installieren („Aus dieser Quelle erlauben“ bestätigen).
2. Jarvis öffnen → **Zahnrad** (Einstellungen) → API-Schlüssel einfügen → **Schlüssel testen**. Jarvis erkennt den Anbieter selbst:
   - **Claude:** platform.claude.com → API Keys (beginnt mit `sk-ant-`)
   - **Gemini (kostenlos, mit Limit):** aistudio.google.com → Get API key (beginnt mit `AIza`)
3. Das Feld **Modell** leer lassen: Claude Sonnet 5.5 – im Alltag ohne Nachdenken (schnell), im Spielmodus mit kurzem Nachdenken. Sparvariante: `claude-haiku-5-5` eintragen.
4. Zurück zum Startbildschirm, auf den **Kern** tippen → Mikrofon, Mitteilungen und „Über anderen Apps anzeigen“ (für Untertitel) erlauben → **Gesamter Bildschirm** teilen.
5. Optional: Seitentaste → Zweimal drücken → App öffnen → **Jarvis sprechen**.

## Bedienung

- **„Hey Jarvis“** sagen, kurzen Ton abwarten, lossprechen. Pausen sind erlaubt – abgeschickt wird nach der eingestellten Sprechpause.
- Ohne Wachwort: Kern auf dem Startbildschirm antippen, **„Sprechen“** in der Benachrichtigung, Seitentaste oder (falls eingeschaltet) das schwebende J.
- **Während Jarvis spricht:** „Hey Jarvis“ oder Antippen unterbricht und hört neu zu.
- **Kern lange drücken:** neues Gespräch.
- **„Spielmodus an“ / „Spielmodus aus“** schaltet per Sprache um – oder der Schalter auf dem Startbildschirm.
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

### Anzeige

- **Schwebendes J:** Standard aus. Im Spielmodus immer ausgeblendet.
- **Untertitel:** im Alltag Standard an, im Spielmodus Standard aus; Position oben oder unten.

## Wachwort „Hey Jarvis“

Die Erkennung läuft komplett offline auf dem Handy mit [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) – ohne Konto, ohne Schlüssel, ohne Kosten. Bei ausgeschaltetem Bildschirm pausiert sie (spart Akku, keine Fehlauslöser in der Tasche). Die Empfindlichkeit ist in den Einstellungen regelbar (Standard 5).

Picovoice Porcupine wurde bewusst nicht genommen: Das kostenlose Angebot wurde zum 30.06.2026 eingestellt, ohne gültigen Schlüssel läuft es nicht.

Ein reines „Jarvis“ (ohne „Hey“) gibt es nicht als fertiges Modell. Es lässt sich mit dem Trainings-Notebook von openWakeWord in Google Colab selbst erstellen und gegen `app/src/main/assets/wakeword/hey_jarvis_v0.1.onnx` austauschen.

Modelle: `melspectrogram.onnx` und `embedding_model.onnx` (Apache 2.0), `hey_jarvis_v0.1.onnx` von David Scripka (CC BY-NC-SA 4.0 – nur nicht-kommerziell).

## Aktuelle TFT-Meta

Im Spielmodus recherchiert Jarvis **einmal am Tag** mit Claudes Websuche die aktuelle Meta (Set, Patch, stärkste Comps, Augments, Buffs/Nerfs) und nutzt sie den ganzen Tag als Wissensstand. Die Recherche läuft im Hintergrund, Fragen warten nicht darauf. Kosten: wenige Cent pro Tag. Manuell: Einstellungen → Spielmodus → „Meta jetzt aktualisieren“. Braucht einen Claude-Schlüssel.

## Stimme

**Premium (empfohlen, am nächsten am Film-Jarvis):** ElevenLabs-Schlüssel in den Einstellungen eintragen. Standardstimme ist „Daniel“ (britisch, tief, ruhig), die mit dem Modell *Multilingual v2* Deutsch mit leichtem britischem Einschlag spricht. Alternativ in ElevenLabs unter *Voice Design* eine eigene Stimme beschreiben und deren ID eintragen. Fällt ElevenLabs aus oder ist das Kontingent leer, spricht Jarvis automatisch mit der Handy-Stimme weiter. Der ElevenLabs-Schlüssel wird nicht in die Sicherungsdatei geschrieben.

**Handy-Stimme (offline):** Jarvis sucht automatisch in dieser Reihenfolge: **Thorsten** (deutsch, männlich) → **Miro** (deutsch, männlich) → **Alan** (britisch). Deutsche Stimmen dafür in SherpaTTS herunterladen (z. B. „de_DE thorsten high“). Engine, Stimme, Tempo, Tonhöhe und Sprechpause sind in den Einstellungen wählbar („Probe hören“).

## Projektaufbau

| Datei | Aufgabe |
|---|---|
| `JarvisService` | Ablauf: Wachwort, Zuhören, Bildschirmfoto, Anfrage, Vorlesen, Spielmodus |
| `WakeWordEngine` | Mikrofon für „Hey Jarvis“, Übergabe an die Spracherkennung |
| `wakeword/…/WakeWordDetector` | openWakeWord in reinem Java (eigenes Modul, mit echten Aufnahmen getestet) |
| `MainActivity` | Startbildschirm: Status und Reaktor-Knopf |
| `ReactorView` | Animierter Sprechen-Knopf im Arc-Reaktor-Stil |
| `SettingsActivity` | Einstellungen in Gruppen |
| `Subtitles` | Nicht anklickbare Untertitelleiste |
| `FloatingJ` | Optionales schwebendes J |
| `Earcon` | Signalton beim Zuhören |
| `Commands` | Sprachbefehle der App („Spielmodus an/aus“) |
| `ListenActivity` | Schnellstart „Jarvis sprechen“ (Seitentaste) |
| `ClaudeClient`, `GeminiClient` | Anbindung der KI-Anbieter |
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
- [x] Aktivierungswort „Hey Jarvis“ ohne Antippen, Spielmodus ohne Einblendungen, neues Design
- [ ] **Stufe 2:** Nachrichten vorlesen und beantworten, Erinnerungen, Wecker, Termine
- [ ] **Stufe 3:** Apps per Sprache steuern
