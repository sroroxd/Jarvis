# Jarvis Lokal

Eigener Launcher „Jarvis Lokal“ und Einstieg auf dem bisherigen Startbildschirm. Die lokale
Textoberfläche lädt keine Cloud-Clients, Sprachdienste oder API-Schlüssel. Das GGUF-Modell
läuft über llama.cpp direkt in derselben Android-App, ohne PC, Termux oder Serverprozess.

## Bedienung

1. „GGUF-Modell importieren“: eine vorhandene Qwen2.x-Instruct-GGUF mit ChatML-Vorlage wählen.
   Empfohlen für den ersten Test ist Qwen2.5-1.5B-Instruct Q4_K_M.
2. Text eingeben und senden. Modellladung und Antwort laufen auf einem eigenen Worker.
3. „Abbrechen“ verwirft unvollständige Antworten. Nach einer Antwort wird der Modellspeicher freigegeben.

Der Import kopiert in den privaten, vom Android-Backup ausgeschlossenen App-Speicher und lädt
die vollständige Datei probeweise. Erst bei Erfolg ersetzt er atomar das vorherige Modell.
Abbruch, kaputte Dateien, inkompatible Architektur und fehlende ChatML-Vorlage erhalten das alte Modell.
Die Quelldatei bleibt unverändert. Höchstens 4 GiB; Kontext 2048 Token, vier CPU-Threads,
Antwort höchstens 256 Token. Eine wegen des Limits gekürzte Antwort wird gekennzeichnet.
Unterhaltung und begrenzter Verlauf bleiben nur im Arbeitsspeicher. Längere Eingaben werden
für das kleine Kontextfenster gekürzt. Kein Zugriff auf Apps, Dateien oder Anrufe durch das Modell.

## Optionales Internet

„Internet für Webseiten erlauben“ ist standardmäßig aus. Bei aktivierter Einstellung kann der
Nutzer ausdrücklich eine öffentliche HTTPS-Webseite lesen und vom lokalen Modell zusammenfassen
lassen. Es gibt keine automatische Websuche, Cloud-Inferenz oder Anbieteranmeldung. Der Schalter
steuert dieses Werkzeug; er schaltet nicht die gesamte Internetverbindung des Handys ab.

Webseiten sind untrusted Quelldaten, keine Werkzeug- oder Systemanweisungen. Nur öffentliche
HTTPS-Ziele auf Port 443, ohne Cookies/Anmeldung, mit geprüften DNS-Adressen und TLS-Hostnameprüfung.
Maximal zwei Weiterleitungen, 128 KiB Body und 6000 Zeichen Text; keine Skriptausführung.
Keine komprimierten Antworten. Lange HTML-Verarbeitung ist begrenzt, linear und abbrechbar.

## Build und Prüfung

NDK 28.2.13676358, CMake 3.22.1, CPU-only. Gepinnter llama.cpp-Commit
`3b4d6ab3f79651935fba1dd93732f29022a7f5f5`, Archiv-SHA256
`f6aa510d53755ccdfe2b916a65b0dde622a4869f333f28e31b3b904d22b90bb1`.
Statische llama/ggml/C++-Abhängigkeiten in `libjarvis_llm.so`; arm64-Release und x86_64-Emulator.
MIT-Lizenz liegt unter `app/src/main/assets/licenses/`.

JVM-Tests prüfen Promptrollen, Kontext-/Unicodegrenzen, URL-Regeln, ausgeschalteten Netzabruf
und problematische HTML-Eingaben. Der Emulator prüft die wirkliche JNI-Bindung mit einer eigenen
ungültigen GGUF und den standardmäßig ausgeschalteten Internetschalter. Ein solcher Test ersetzt
keine echte Modellantwort auf dem Samsung.

Das vorhandene Qwen2.5-1.5B Q4_K_M antwortete bereits in PocketPal auf dem Samsung bei
abgeschaltetem WLAN und Mobilfunk korrekt mit „51“ auf „17 mal 3“. Die Verbindungen wurden danach
wiederhergestellt. Das ist der Ausgangsnachweis; Jarvis selbst muss nach dem Build getrennt geprüft werden.
Die Quelldatei stimmt mit der [offiziellen Modelldatei](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/a615a81362316d7b9f5a7a9c4313adfdf9b54588/qwen2.5-1.5b-instruct-q4_k_m.gguf)
überein: SHA256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`.

Spracherkennung, Sprachausgabe, automatische Handy-Werkzeuge und Dauerbetrieb werden durch
diesen ersten lokalen Textmodus nicht als fertig behauptet.
