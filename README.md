# Jarvis

Persönlicher Android-Assistent für das Galaxy S24 Ultra – mit Claude als Gehirn. Hört zu, sobald man losredet, sieht den Bildschirm und antwortet mit Stimme. Hauptaufgabe: Live-Coach für Teamfight Tactics, der sich in jeder Planungsphase von selbst meldet. Lange Aufgaben übernimmt ein Agent auf dem Handy (Claude Code in Termux).

## Herunterladen

**Immer die neueste Version:** https://github.com/sroroxd/Jarvis/releases/latest/download/Jarvis.apk

Jede Änderung auf `main` wird automatisch getestet und als neue APK veröffentlicht. Sie lässt sich direkt über die alte installieren; Schlüssel, Gedächtnis und Einstellungen bleiben erhalten.

**Test-Apps von Arbeitszweigen:** Jeder andere Zweig baut die separate App **„Jarvis Test“** (Vorab-Version `test-<Zweig>`). Sie installiert sich neben der normalen App, hat eigene Einstellungen und überschreibt nichts. Immer nur eine der beiden Apps laufen lassen, sonst hören beide zu.

**Welche Version läuft?** Steht oben auf dem Startbildschirm („Build N“ bzw. „Test-Build N“). Liegt auf GitHub eine neuere, meldet die App das beim Öffnen; **„Installieren“** spielt sie direkt ein (über die GitHub-API, bis zu vier Versuche, sonst „Nochmal“).

## Einrichten

1. APK installieren („Aus dieser Quelle erlauben“ bestätigen).
2. Jarvis öffnen → **Zahnrad** (Einstellungen) → Claude-API-Schlüssel einfügen (platform.claude.com → API Keys, beginnt mit `sk-ant-`) → **Schlüssel testen**.
3. Das Feld **Modell** leer lassen: Claude Sonnet 5.5. Sparvariante: `claude-haiku-5-5`.
4. Zurück zum Startbildschirm, auf den **Kern** tippen → Mikrofon, Mitteilungen und „Über anderen Apps anzeigen“ erlauben → **Gesamter Bildschirm** teilen.
5. Einstellungen → Spielmodus: **„TFT automatisch erkennen“** (einmal „Nutzungsdatenzugriff“ erlauben) und **„Im Hintergrund weiterlaufen lassen“** (sonst beendet Samsung Jarvis gern mitten in der Partie).
6. Optional: Seitentaste → Zweimal drücken → App öffnen → **Jarvis sprechen**.

## Bedienung

- **Einfach losreden:** Mit „Immer zuhören“ (Standard, Chip „Zuhören“) hört Jarvis durchgehend zu, solange er läuft und der Bildschirm an ist – ohne Wachwort, ohne Antippen. Was nicht an ihn gerichtet ist (Gespräch mit anderen, Fernseher), lässt er unbeantwortet. Bildschirm aus: Zuhören pausiert, beim Einschalten geht es weiter.
- **Kern antippen:** pausiert das Zuhören; nochmal tippen hört wieder zu. „Gespräch beenden“ pausiert ebenso.
- **Während Jarvis spricht:** losreden oder „Jarvis“ sagen unterbricht ihn. Damit seine eigene Stimme nicht als Unterbrechung zählt, spricht er im Dauergespräch wie bei einem Videoanruf (Telefonmodus mit Echo-Unterdrückung).
- **Kern lange drücken:** neues Gespräch (Verlauf wird geleert).
- **Bildschirm:** Chip „Bildschirm“ gibt den Bildschirm frei, ohne Neustart. Jede Frage bekommt ein frisches Bild vom Moment der Frage.
- **„Merk dir …“** speichert dauerhaft, **„Vergiss …“** löscht. Alles einsehbar unter Einstellungen → Gedächtnis.
- **„Notier für die Entwicklung: …“** – oder einfach über einen Wunsch reden: Jarvis legt ihn als GitHub-Issue an (gleiche Wünsche nur einmal).
- **Lange Aufgaben:** „Übernimm das: …“ gibt sie an den Agenten auf dem Handy (siehe `agent/README.md`).
- **Termine und Websuche** im Alltag: „Was steht morgen an?“, „Trag mir Freitag 18 Uhr Training ein“.

**Messzeilen auf dem Startbildschirm** (nach jeder Antwort):
- *Letzte Antwort:* Zuhören · KI · Stimme · gesamt, dazu Modell, Cache, Kosten der Antwort und „Senden … (KB) · Claude …“ – zeigt, wo die Sekunden liegen.
- *Dazwischenreden:* Echo, Schwelle, lautester Moment, ob und wodurch unterbrochen, Telefonmodus.
- *Heute bisher:* alle Claude-Anfragen des Tages in US-Cent, auch Phasen-Wächter und Meta-Recherche.

| Farbe des Kerns | Bedeutung |
|---|---|
| Graublau | Jarvis ist aus |
| Blau | bereit |
| Cyan | hört zu |
| Violett | denkt nach |
| Türkis | spricht |

### Spielmodus (TFT)

- **Automatisch:** Läuft TFT im Vordergrund, schaltet Jarvis den Spielmodus selbst ein und hört zu; 20 s nach dem Spiel nimmt er das zurück. Per Sprache: „Spielmodus an/aus“, „Neue Partie“.
- **Von selbst coachen:** Ein günstiger Phasen-Wächter (Claude Haiku, alle 6 s, Bild in Alltagsgröße) erkennt Planung, Kampf oder Sonstiges. Zu Beginn jeder neuen Planungsphase schaut Sonnet aufs scharfe Bild und sagt den wichtigsten Befehl, wenn nötig mit Aufstellung – oder schweigt. Höchstens eine Ansage pro Runde, nie im Kampf, nie in Runde 1-1. Geschätzt 15–20 Cent pro Partie.
- **Spielplan:** Beim ersten lesbaren Brett legt Jarvis Comp, Carry und Gegenstandsträger fest und hält den Plan. „Plan?“ nennt ihn, „Warum?“ begründet den letzten Rat.
- **Erst lesen, dann befehlen:** Zu jedem Bild schreibt Claude eine stille Zeile (Phase, Gold, Stufe, Leben, Plan), die nie vorgelesen wird, aber im Verlauf bleibt.
- **Nichts verdeckt das Spielbild:** Antworten nur per Stimme, kurz (höchstens zwei Sätze). Optional eine nicht anklickbare Untertitelleiste.
- **Scharfes Bild:** 1920 statt 1280 Pixel an der langen Kante, damit Gold, Stufe und Gegenstände lesbar bleiben.
- **Aktuelle Meta:** einmal am Tag recherchiert Jarvis mit Claudes Websuche Set, Patch und stärkste Comps (wenige Cent). Manuell: Einstellungen → Spielmodus → „Meta jetzt aktualisieren“.

Begriffe wie im deutschen Client: Phase, Stufe, Händler, Taktikerleben, Attribute.

## Stimme

**Handy-Stimme (offline):** Jarvis sucht in dieser Reihenfolge: **Miro** → **Thorsten** (deutsch, männlich) → **Alan** (britisch). Deutsche Stimmen in SherpaTTS herunterladen (z. B. „de_DE miro high“). Engine, Stimme, Tempo, Tonhöhe und Sprechpause in den Einstellungen („Probe hören“). „Sir“ spricht sie englisch aus.

**Premium (optional):** ElevenLabs-Schlüssel eintragen, Standardstimme „Daniel“ (Multilingual v2). Fällt ElevenLabs aus, spricht Jarvis mit der Handy-Stimme weiter.

## „Jarvis“-Erkennung zum Unterbrechen

Ein Wachwort zum Aktivieren gibt es seit „Immer zuhören“ nicht mehr. Das Offline-Modell für „Jarvis“ bleibt fürs Unterbrechen: Sagt man „Jarvis“, während er spricht, hört er sofort auf. Es läuft auf dem echobereinigten Ton des Dazwischenreden-Mikrofons mit [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime) – ohne Konto, ohne Kosten. Empfindlichkeit: Einstellungen → „Empfindlichkeit für „Jarvis““ (Standard 5).

Das Modell ist selbst trainiert (`app/src/main/assets/wakeword/jarvis.onnx`, Messwerte in `training/wakeword/ergebnis/`): zehntausende künstliche Sprecher sagen „Jarvis“ (englisch und deutsch ausgesprochen, mit Hall und Störgeräuschen), dagegen ähnlich klingende Wörter und rund 370 Stunden Alltagsaudio. Neu trainieren: in einem Arbeitszweig `training/wakeword/start.txt` ändern und pushen (oder Actions → **Wachwort „Jarvis“ trainieren**). Nie auf `main` – das Training committet sein Ergebnis in den Zweig.

Modelle: `melspectrogram.onnx` und `embedding_model.onnx` (Apache 2.0), `jarvis.onnx` mit openWakeWord-Daten trainiert (nur nicht-kommerziell). Das frühere `hey_jarvis_v0.1.onnx` (CC BY-NC-SA 4.0) liegt nur zum Vergleich unter `training/wakeword/referenz/`.

## Projektaufbau

| Datei | Aufgabe |
|---|---|
| `JarvisService` | Ablauf: Zuhören, Bildschirmfoto, Anfrage, Vorlesen, Spielmodus, proaktives Coaching |
| `MainActivity`, `ReactorView`, `Palette` | Startbildschirm mit Arc-Reaktor-Knopf und Messzeilen |
| `SettingsActivity`, `Prefs`, `Backup` | Einstellungen, gespeicherte Werte, Sicherungsdatei |
| `ClaudeClient`, `StreamAccumulator` | Claude-API mit Streaming, Werkzeug-Schleife, Prompt-Caching, Kosten- und Zeitmessung |
| `SpeechChunker` | Zerlegt die gestreamte Antwort in Sätze – der erste wird gesprochen, während Claude noch schreibt |
| `Persona`, `Commands`, `Memory`, `Text` | Anweisungen an Claude, Sprachbefehle der App, Gedächtnis, Textbereinigung |
| `BargeIn`, `SpeechGate`, `TalkAudio`, `WakeModel` | Dazwischenreden: Mikrofon mit Echo-Unterdrückung, Lautstärke-Schranke, Telefonmodus, „Jarvis“-Modell |
| `wakeword/…/WakeWordDetector` | openWakeWord in reinem Java (eigenes Modul, mit Testaufnahmen geprüft) |
| `GameWatch`, `GameDetector` | Erkennt, ob TFT vorne ist |
| `ProactiveCoach`, `GameReadout`, `Meta` | Phasen-Wächter und Ansagen, stille Ablese-Zeile, tägliche Meta |
| `ScreenGrabber`, `CaptureSize` | Bildschirm-Mitschnitt und Bildgrößen |
| `Voices`, `ElevenTts`, `Earcon` | Sprachausgabe (Handy / Premium), Signalton |
| `Subtitles`, `FloatingJ`, `ChatLog` | Untertitelleiste, optionales schwebendes J, Gesprächsverlauf |
| `Costs`, `Timing` | Kosten nach Anthropic-Preisliste, Zeitmessung |
| `UpdateCheck`, `AppUpdater`, `UpdateReceiver` | Neue Version finden und direkt einspielen |
| `Wishes`, `WishSender` | Wünsche aus dem Gespräch als GitHub-Issues (doppelte werden erkannt) |
| `Tools`, `Agenda` | Kalender lesen und Termine eintragen (Websuche läuft bei Anthropic) |
| `AgentClient`, `AgentReply` | Brücke zum Agenten in Termux (`127.0.0.1`, Token) |
| `ListenActivity` | Schnellstart „Jarvis sprechen“ (Seitentaste) |
| `Io` | Gemeinsame Netz-Hilfe |

Layouts liegen in `app/src/main/res/layout`, Farben und Theme in `res/values`.

## Tests und Abläufe auf GitHub

- **Unit-Tests** in `app/src/test` und `wakeword/src/test` laufen bei jedem Build.
- **Gerätetest** (`.github/scripts/geraetetest.sh`) auf jedem Push: Emulator installiert und bedient die App, stellt echte Fragen an Claude, prüft Streaming und Cache, lässt den Phasen-Wächter Arts echte TFT-Bilder einordnen (`.github/testbilder/waechter`) und lädt die Test-App ohne Anmeldung herunter. Bei Absturz oder Hänger wird keine APK veröffentlicht. Bericht und Bilder: Vorab-Version `vorschau`.
- **Latenz messen** (`.github/bench`, Actions → „Latenz messen“): misst mit der echten API, wie schnell Claude den ersten Satz liefert.
- **Aufräumen** (Actions → „Aufräumen“, von Hand): löscht Zweige, die ganz in `main` stecken, Test-Apps gelöschter Zweige und ältere App-Versionen. Mit „nur_anzeigen = ja“ erst schauen.
- **Wachwort „Jarvis“ trainieren:** siehe oben.

## Ausbaustufen

- [x] **Stufe 1:** Sprechen, Bildschirm sehen, natürliche Antwort, TFT-Modus, Gedächtnis, Stimmwahl
- [x] Dauergespräch, Dazwischenreden, Termine und Websuche, Agent für lange Aufgaben (im Test)
- [x] TFT-Coach: automatische Erkennung, Ansagen von selbst, Spielplan, tägliche Meta
- [x] „Immer zuhören“ statt Wachwort
- [ ] **Stufe 2:** Nachrichten vorlesen und beantworten, Erinnerungen, Wecker
- [ ] **Stufe 3:** Apps per Sprache steuern
