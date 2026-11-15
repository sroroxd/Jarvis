# OpenAI Realtime und frische TFT-Bilder – Test zu #93

Nur der Zweig `feature/openai-realtime-tft-vision` baut diese Variante mit
`-PtestApp -PopenAiTest`. Paket: `de.art.jarvis.openaitest`, Name: **Jarvis OpenAI Test**.
Die installierte Produktiv-App `de.art.jarvis`, ihre Daten und ihre Signatur werden nicht verändert.
Die automatische Update-Suche über andere Testzweige ist für diese Variante ausgeschaltet.

## Auf dem Handy

1. Die Test-APK dieses Zweigs installieren, neben der bisherigen App. Für den Test nur einen Assistenten starten.
2. Einstellungen öffnen, **OpenAI-API-Schlüssel (nur auf diesem Handy)** ausfüllen und
   **OpenAI Realtime verwenden (Test)** einschalten. Schlüssel nie in Chat, GitHub oder Fehlerberichte schreiben.
3. Zum Startbildschirm zurück, Kern antippen und Mikrofon erlauben. Der Android-Systemdialog bietet
   die Bildschirmfreigabe an. Ohne Zustimmung gibt es keine Spielbilder; eine verweigerte Freigabe wird respektiert.
4. Für den ersten Bildtest **TFT-Bild jetzt prüfen** antippen oder eine Frage sprechen. Nach einer gesprochenen
   Frage wird genau ein frisches freigegebenes Bild versucht. Es gibt keinen unbeaufsichtigten Bild-Timer.
5. Während einer Antwort losreden: lokale Audioausgabe wird geleert, die Antwort abgebrochen und die
   gehörte Länge an die Sitzung gemeldet. Kern antippen pausiert, **Beenden** schließt Sitzung und Mikrofon.

Modell ist ausschließlich `gpt-realtime-2.1-mini`. Kein Ausweichmodell, keine automatische Wiederverbindung,
kein zusätzliches Transkriptionsmodell. Der Start ist kostenpflichtig. Eine Sitzung endet spätestens nach
zehn Minuten oder sechs Antworten. Das ist **kein verbindliches Geldlimit**. Verbrauchte Ein-/Ausgabetokens
werden aus den API-Antworten angezeigt. `429` wegen Guthaben und wegen Abfragerate werden unterschieden.

## Bild- und Sprachfluss

`JarvisService` behält den vorhandenen Foreground-Service mit Mikrofon-/MediaProjection-Typen und den
vorhandenen `ScreenGrabber`. Ein manueller Bildauftrag oder das Ende einer gesprochenen Frage ruft diesen
Mitschnitt ab. Ein Bild darf höchstens 1,5 Sekunden alt sein und muss frühestens 250 ms vor der Anfrage
eingetroffen sein. Nach Verarbeitung erneut veraltete Bilder werden ebenfalls verworfen. Bei fehlendem,
widerrufenem oder altem Bild wird kein `input_image` gesendet; der Spielstand heißt **Nicht sicher erkennbar**.
Vor dem nächsten Auftrag wird das vorige Bild aus der Realtime-Unterhaltung gelöscht.

`OpenAiRealtimeClient` erzeugt über HTTPS ein 60 Sekunden gültiges Client-Token und verwendet nur dieses
für den WebSocket-Handshake. Der dauerhafte Schlüssel ist kein Teil des WebSocket-Protokolls, kein
Build-Parameter und kein Teil der APK. PCM16/24 kHz wird nativ über `AudioRecord`/`AudioTrack` verarbeitet,
mit Echo-Unterdrückung, begrenzten Puffern und Abbruch bei Ausfall. Eine WebView wird nicht verwendet.

Der Schalter aktiviert OpenAI ausdrücklich. Der Claude-Pfad wird währenddessen nicht gestartet und
erhält keine automatischen Meta-/Wächteranfragen. Die übrige Claude-Konfiguration bleibt separat erhalten.
Im OpenAI-Modus werden die nicht angebundenen Claude-Sprachoptionen, Meta-Websuche,
GitHub-Wünsche, Termux-Agent und das dauerhafte Gedächtnis ausgeblendet. Beim bewussten
Zurückschalten stehen die bisherigen Einstellungen wieder bereit. OpenAI ist weiterhin eine Cloud-API.
Die Hilfe erklärt den tatsächlichen Sprach-/Bildablauf und die Testpause. Nach sechs Antworten
bleibt der gespeicherte Schlüssel gültig; zum Fortsetzen den Kern antippen. Verbindungsfehler
zeigen einen bereinigten technischen Code statt fremder API-Fehlermeldungen oder Zugangsdaten.
Beim Unterbrechen wartet der Client auf die Abbruchbestätigung, bevor er eine neue Antwort anfordert.

## Zugangsdaten und Signierung

Alle eingegebenen Zugangsdaten werden mit AES-256-GCM und einem appgebundenen Android-Keystore-Schlüssel
gespeichert. Bestehende Klartextwerte werden erst nach dauerhaftem Speichern und Prüfen der neuen Kopie
entfernt. Verschlüsselte Felder sind durch ihren Feldnamen gegen Vertauschen geschützt. Unlesbare Werte
werden nicht durch ein leeres Formular überschrieben. System-Backups und Geräteübertragung schließen
die Einstellungsdateien aus; JSON-Exporte enthalten keine Zugangsdaten. Auch schlüsselfreie Sicherungen
können persönliche Gedächtniseinträge enthalten.

Die alte öffentliche Keystore-Datei ist aus diesem Testzweig entfernt. Die CI signiert diese **separate**
OpenAI-Test-App mit einem temporären eigenen Schlüssel, der nicht als Artefakt gespeichert wird. Ein späterer
Build kann deshalb nicht garantiert über eine vorhandene OpenAI-Test-App installiert werden. Bei einem
Signaturkonflikt ausschließlich die alte **OpenAI-Test-App** nach Sicherung ihrer Daten entfernen – niemals
die Produktiv-App. Dauerhafte Produktionssignierung und Rotation benötigen geschützte Actions-Secrets
und sind ausdrücklich kein Teil dieser Geräteinstallation. Ohne diese Secrets veröffentlicht die CI
keine neue Produktiv-APK. Die vorhandene Produktivsignatur wird hier nicht rotiert.

## Prüfungen und offene Abnahme

- JUnit: Sitzungsformat, festes Modell, frische/veraltete Bilder, Quota-/Ratenfehler,
  echter lokaler HTTP-/WebSocket-Fluss mit simuliertem OpenAI-Server, getrennte Header,
  Unterbrechen und Verwerfen verspäteter Audiodaten; AES-GCM-Manipulationserkennung.
- Android-Instrumentierung: echter Keystore, verlustfreie Migration, alte Backup-Importe,
  schlüsselfreie Exporte, Anbieter-Wiederherstellung, Schutz vor leerem Überschreiben beschädigter Werte
  und tatsächlicher Einstellungswechsel mit erhaltenem Schlüssel und Oberflächenfotos.
- Node: echte lokale Agent-HTTP-Anfragen gegen fehlende/falsche Token, fehlenden Login,
  unerwünschte API-Abrechnung, Zeitüberschreitung und Prozessfehler.
- CI: Android-Build, Unit-Tests, Instrumentierung, Emulator-Gerätetest, APK-Signaturprüfung.

Die OpenAI-Testvariante verwendet in der CI keine echten API-Schlüssel. Der bisher hinterlegte
Claude-Testschlüssel wurde bei der Prüfung mit `invalid x-api-key` abgelehnt; Claude-Live-Aufrufe
und die separate Pocket-TTS-Machbarkeitsprobe sind deshalb kein Bestandteil dieses OpenAI-Prüflaufs.
Die normalen Buildvarianten behalten ihre bisherigen Prüfungen. Diese Trennung ist kein Nachweis
für einen erfolgreichen Live-Aufruf bei OpenAI oder Claude.

Diese Prüfungen ersetzen **keinen** echten OpenAI-Aufruf und **keinen** S24-Ultra-Test. Ohne einen gültigen
lokal eingetragenen Schlüssel sind Modellzugriff, Audioqualität/Echo, Hintergrundverhalten bei TFT und
die Qualität der Spielbildanalyse nicht als bestanden nachgewiesen. Automatische periodische Bildschirm-
übertragung wird erst nach dieser Geräteabnahme ergänzt. Issue #93 bleibt bis zur vollständigen Abnahme offen.

Referenzen:
- https://developers.openai.com/api/docs/guides/realtime-conversations
- https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create
- https://developers.openai.com/api/reference/resources/realtime/client-events
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
- https://developer.android.com/identity/data/autobackup
