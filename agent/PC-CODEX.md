# Jarvis über USB mit vorhandenem Codex-Zugang

Experimenteller Ersatz für den lokalen Claude-Code-Agenten, wenn dessen Kontingent erschöpft ist.
Die Android-App bleibt die Oberfläche. Der Windows-PC führt `codex exec` mit seiner vorhandenen
ChatGPT-Anmeldung aus; das Modell läuft in der Cloud. Kein neuer API-Schlüssel, kein API-Guthaben.
Das ChatGPT-/Codex-Kontingent wird weiterhin verbraucht.

`pc-codex-agent.mjs` lauscht ausschließlich auf `127.0.0.1:18777`. Über autorisiertes USB-ADB
wird der Handy-Port 8777 auf diesen PC-Port umgeleitet. Kein WLAN-ADB, keine Firewall-Freigabe.
Vor Nutzung muss das bestehende private Handy-Agententoken über eine einmalige, zufällige
Einrichtungskennung gekoppelt werden. Der PC hält das Token nur im Arbeitsspeicher; weder Token
noch Kontoinformationen werden protokolliert. Nach einem Neustart erneut koppeln.

Startumgebung: `JARVIS_PC_SETUP_NONCE` (mindestens 32 zufällige Zeichen), `JARVIS_PC_WORKDIR`
(eigener Arbeitsordner). Start: `node agent/pc-codex-agent.mjs`. Codex muss auf PATH liegen und
`codex login status` muss eine vorhandene ChatGPT-Anmeldung bestätigen.

Die Kopplung ist `POST /token` mit Bearer-Einrichtungskennung und JSON `{ "token": "…" }`.
Danach sind `/auftrag`, `/neu` und `/fortschritt` nur mit dem Agententoken erreichbar.
`/status` enthält ausschließlich technische Bereitschaft. Browser-Origin-Anfragen werden gesperrt.
Keine Zugangsdaten über den Chat austauschen oder in Befehlsargumente kopieren.

Grenzen dieses ersten Schritts: nur lesender Codex-Sandboxbetrieb, keine Android-Werkzeuge,
keine Bildschirmbilder, keine Gesprächshistorie, kein Autostart. PC und USB müssen verbunden bleiben.
Je Auftrag 120 Sekunden Zeitlimit; unvollständige Antworten zählen nicht als Erfolg.
Der Knopf „Agent prüfen“ in Jarvis fordert eine echte kleine Antwort an und benötigt kein Mikrofon.
Die Freigabe des Dauerbetriebs ist damit noch nicht nachgewiesen.

Prüfung: `node --test agent/pc-codex-agent.test.mjs`. Dokumentation zum unterstützten
[nichtinteraktiven Codex-Aufruf](https://learn.chatgpt.com/docs/non-interactive-mode).
