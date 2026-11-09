# OpenAI für Jarvis

Die Android-App kann OpenAI für Text, Bilder, Websuche und ihre vorhandenen
Kalenderwerkzeuge verwenden. Spracheingabe und Sprachausgabe nutzen den
bestehenden nativen Ablauf. Der separate Langzeit-Agent bleibt Claude Code.
OpenAI-Realtime/WebRTC und eine vollständig offline rechnende KI sind damit
nicht implementiert.

Der OpenAI-Schlüssel gehört **nicht** in die Android-App oder eine APK. Der
lokale Node-Dienst im vorhandenen Ubuntu-Container hält ihn privat. Die App
kennt nur das lokale Kopplungstoken. Ausgehende Providerverbindungen gehen
ausschließlich per HTTPS zur OpenAI Responses API. KI-Anfragen benötigen Internet.

## Einrichtung

`openai-einrichten.sh` in Termux installiert das Programm in den vorhandenen
Ubuntu-Container und legt `~/bin/jarvis-openai` an. Zum Testen eines Arbeitszweigs
`JARVIS_REF=codex-openai-handy` verwenden; für reproduzierbare Installation den
geprüften Commit statt des Zweignamens verwenden. Es wird kein Schlüssel erstellt.

Der vorhandene Schlüssel wird erst nach ausdrücklicher Bestätigung seines
Speicherorts privat eingebunden: `/root/.jarvis-openai/api-key`, Rechte `600`.
Alternativ liest der Prozess seine bereits gesetzte Variable `OPENAI_API_KEY`.
Dateiinhalte werden nie als Shell-Code ausgeführt. Schlüssel nie in Chat,
Shell-Argumente, Repository, Screenshot oder Protokoll schreiben.

Start in Termux: `~/bin/jarvis-openai`. Der Dienst bindet nur `127.0.0.1:8778`.
Er verwendet das vorhandene `/root/.jarvis-agent/token`, sofern vorhanden;
sonst erzeugt er ein eigenes `/root/.jarvis-openai/token`. Dieses Token wird in
der App im Abschnitt Agent eingetragen. Erst nach einem erfolgreichen Live-Test
„OpenAI verwenden“ einschalten und „KI-Verbindung prüfen“ verwenden.

Aktuelles Modell: `gpt-6-luna`. App und Dienst müssen dasselbe Modell verwenden.
Die Kostenanzeige schätzt den Standardtarif; Anbieterzuschläge sind nicht enthalten.
Ein im Vordergrund gestarteter Termux-Dienst ist noch kein nachgewiesener
zuverlässiger Neustart-/Dauerbetrieb. Kein Root, keine automatische Akku-Ausnahme.

## Prüfung und Grenzen

`node --test agent/openai-bridge.test.mjs agent/stream.test.mjs` prüft mit einem
lokalen HTTP-Server und nachgebildeten Providerantworten. Dafür werden weder ein
echter Schlüssel noch kostenpflichtige API-Anfragen verwendet. Android-Build,
Unit-Tests und Emulatorprüfung laufen in GitHub Actions.

Ein erfolgreicher Fixture-Test beweist keinen Kontozugriff oder tatsächliche
OpenAI-Antwort. Ein Live-Test muss Text, Verlauf, Streaming und ein ungefährliches
Werkzeug überprüfen, bevor der Anbieter dauerhaft aktiviert wird.

Werkzeugkontext inklusive benötigter Reasoning-Items lebt nur kurzzeitig im
Arbeitsspeicher. Ein abgelaufener Kontext wird abgelehnt; es wird kein alter
Schreibauftrag automatisch wiederholt. Die native App prüft weiterhin, ob der
Auftrag aktiv ist, bevor sie ein Werkzeug ausführt. Bereits gestartete Änderungen
werden durch einen Abbruch nicht rückgängig gemacht.
