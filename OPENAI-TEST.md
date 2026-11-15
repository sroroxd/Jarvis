# OpenAI Realtime und frische TFT-Bilder – Test zu #93

Nur der Zweig `feature/openai-realtime-tft-vision` baut diese Variante mit
`-PtestApp -PopenAiTest`. Paket: `de.art.jarvis.openaitest`, Name: **Jarvis OpenAI Test**.
Die installierte Produktiv-App `de.art.jarvis`, ihre Daten und ihre Signatur werden nicht verändert.
Die automatische Update-Suche über andere Testzweige ist für diese Variante ausgeschaltet.

## Auf dem Handy

1. Die Test-APK dieses Zweigs installieren, neben der bisherigen App. Für den Test nur einen Assistenten starten.
2. Einstellungen öffnen, **OpenAI-API-Schlüssel (nur auf diesem Handy)** ausfüllen und
   **OpenAI Realtime verwenden (Test)** einschalten. Schlüssel nie in Chat, GitHub oder Fehlerberichte schreiben.
3. Zum Startbildschirm zurück, Kern antippen und Mikrofon erlauben. Die OpenAI-Testvariante fragt ab
   Android 14 über den normalen Systemdialog den **gesamten Bildschirm** an, damit der Wechsel von Jarvis
   zu TFT mit erfasst wird. Ohne Zustimmung gibt es keine Spielbilder; eine verweigerte Freigabe wird respektiert.
   Falls das Gerät trotzdem eine App-Auswahl anbietet: **Gesamten Bildschirm teilen** wählen, nicht Chrome.
4. Für den ersten Bildtest **TFT sichtbar lassen und eine Frage sprechen**. Nach einer gesprochenen
   Frage wird genau ein frisches freigegebenes Bild versucht. Alternativ TFT und Jarvis nebeneinander
   im geteilten Bildschirm anzeigen und **TFT-Bild jetzt prüfen** antippen. Der Knopf erfasst sofort:
   Zeigt das Display nur Jarvis, ist auch nur Jarvis im Bild; ein früherer TFT-Frame wird nicht wiederverwendet.
   Es gibt keinen unbeaufsichtigten Bild-Timer.
5. Während einer Antwort losreden: lokale Audioausgabe wird geleert, die Antwort abgebrochen und die
   gehörte Länge an die Sitzung gemeldet. Kern antippen pausiert, **Beenden** schließt Sitzung und Mikrofon.

Modell ist ausschließlich `gpt-realtime-2.1-mini`. Kein Ausweichmodell, keine automatische Wiederverbindung,
kein zusätzliches Transkriptionsmodell. Der Start ist kostenpflichtig. Eine Sitzung endet spätestens nach
zehn Minuten. Eine feste Begrenzung der Antwortzahl gibt es nicht mehr. Das ist **kein verbindliches Geldlimit**. Verbrauchte Ein-/Ausgabetokens
werden aus den API-Antworten angezeigt, auch wenn die betreffende Antwort unterbrochen wurde.
Doppelte Abschlussereignisse werden nicht mehrfach gezählt. Die Anzeige umfasst die von der API
gemeldete Nutzung und ist kein verbindlicher Abrechnungsnachweis. `429` wegen Guthaben und wegen
Abfragerate werden unterschieden.

## Bild- und Sprachfluss

`JarvisService` behält den vorhandenen Foreground-Service mit Mikrofon-/MediaProjection-Typen und den
vorhandenen `ScreenGrabber`. Ein manueller Bildauftrag oder das Ende einer gesprochenen Frage ruft diesen
Mitschnitt ab. Ein Bild darf höchstens 1,5 Sekunden alt sein und muss frühestens 250 ms vor der Anfrage
eingetroffen sein. Ist beim Abruf noch kein passender Frame vorhanden, wartet ausschließlich diese Anfrage
bis zu einer Sekunde auf einen neuen Frame. Empfangszeiten alter Bilder werden dabei nicht erneuert.
Nach Verarbeitung erneut veraltete Bilder werden ebenfalls verworfen. Bei fehlendem,
widerrufenem oder altem Bild wird kein `input_image` gesendet; der Spielstand heißt **Nicht sicher erkennbar**.
Vor dem nächsten Auftrag wird das vorige Bild aus der Realtime-Unterhaltung gelöscht.
JPEG-Kompression findet außerhalb der Capture-Sperre statt; neue Frames und ein Stop können weiterlaufen.
Ein Reader-Wechsel durch Drehen oder Stop entwertet einen gerade verarbeiteten Frame ebenfalls.

Ab Android 14 verwendet ausschließlich die OpenAI-Testvariante `createConfigForDefaultDisplay()`;
die normalen Varianten behalten `createConfigForUserChoice()`. Die Android-Zustimmung bleibt erforderlich.
Laut Android können Hersteller die Display-Vorgabe bei missbräuchlicher Nutzung überschreiben; der
Intent allein belegt deshalb noch keinen erfolgreichen Samsung-Capture. Bei der Geräteabnahme muss
die tatsächliche Freigabe das Display statt einer fremden App wie Chrome erfassen.

`OpenAiRealtimeClient` erzeugt über HTTPS ein 60 Sekunden gültiges Client-Token und verwendet nur dieses
für den WebSocket-Handshake. Der dauerhafte Schlüssel ist kein Teil des WebSocket-Protokolls, kein
Build-Parameter und kein Teil der APK. PCM16/24 kHz wird nativ über `AudioRecord`/`AudioTrack` verarbeitet,
mit Echo-Unterdrückung, begrenzten Puffern und Abbruch bei Ausfall. Eine WebView wird nicht verwendet.

Der Schalter aktiviert OpenAI ausdrücklich. Der Claude-Pfad wird währenddessen nicht gestartet und
erhält keine automatischen Meta-/Wächteranfragen. Die übrige Claude-Konfiguration bleibt separat erhalten.
Im OpenAI-Modus werden die nicht angebundenen Claude-Sprachoptionen, Meta-Websuche,
GitHub-Wünsche, Termux-Agent und das dauerhafte Gedächtnis ausgeblendet. Beim bewussten
Zurückschalten stehen die bisherigen Einstellungen wieder bereit. OpenAI ist weiterhin eine Cloud-API.
Die Hilfe erklärt den tatsächlichen Sprach-/Bildablauf und die Testpause nach zehn Minuten.
Nach sechs Antworten läuft die Sitzung weiter; der gespeicherte Schlüssel bleibt gültig. Verbindungsfehler
zeigen einen bereinigten technischen Code statt fremder API-Fehlermeldungen oder Zugangsdaten.
Beim Unterbrechen wartet der Client auf die Abbruchbestätigung, bevor er eine neue Antwort anfordert.
Nach einem Android-Freigabedialog startet der Mikrofon-Dienst erst, wenn die Activity wieder aktiv ist.
Eine erteilte Mikrofonberechtigung garantiert allein keinen erlaubten Hintergrundstart. Lehnt Android
den Start ab, zeigt die App eine verständliche Meldung, statt abzustürzen; es gibt keinen automatischen
Neuversuch und keine Umgehung der Systemfreigaben.

Im kleinen Fenster bleibt der Sprechkern mindestens 96 dp groß, Status und Stop bleiben sichtbar.
Einstellungen, Modusschalter, Bildknopf und Verlauf sind dann über den Scrollbereich erreichbar.
„Kostenpflichtig · Status öffnen“ zeigt per Antippen den vollständigen Bericht. In großen Fenstern
stehen die Zusatzknöpfe wieder an ihren bisherigen Positionen. Der Startstatus unterscheidet zwischen
einem fehlenden Schlüssel, einem noch nicht gewählten Anbieter und einer startbereiten OpenAI-Konfiguration.

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
- Echte Android-Bildpuffer: nach Anfrage eintreffender Frame, Austausch eines alten Frames,
  statischer Timeout ohne neue Datierung, Stop beim Warten, veralteter Reader-Callback und mehrere JPEGs.
- Activity-Oberfläche: Bereitschaft nach Anbieter/Schlüssel, vollständiger Statusdialog, kleine Fenster
  mit 400/320 dp Höhe und mehrfacher Wechsel ins große Fenster. Die Fensterbilder sind Renderings des
  echten Viewbaums unter Testmaßen; sie ersetzen keinen echten Samsung-Split-Screen-Test.
- Echter Android-Freigabe-Intent: Display-Konfiguration nur in der OpenAI-Testvariante,
  bisherige App-Auswahl in normalen Varianten; kein erteilter Capture und kein API-Aufruf im Test.
- Node: echte lokale Agent-HTTP-Anfragen gegen fehlende/falsche Token, fehlenden Login,
  unerwünschte API-Abrechnung, Zeitüberschreitung und Prozessfehler.
- CI: Android-Build, Unit-Tests, Instrumentierung, Emulator-Gerätetest, APK-Signaturprüfung.

Die OpenAI-Testvariante verwendet in der CI keine echten API-Schlüssel. Der bisher hinterlegte
Claude-Testschlüssel wurde bei der Prüfung mit `invalid x-api-key` abgelehnt; Claude-Live-Aufrufe
und die separate Pocket-TTS-Machbarkeitsprobe sind deshalb kein Bestandteil dieses OpenAI-Prüflaufs.
Die normalen Buildvarianten behalten ihre bisherigen Prüfungen. Diese Trennung ist kein Nachweis
für einen erfolgreichen Live-Aufruf bei OpenAI oder Claude.

Diese Prüfungen ersetzen **keinen** echten OpenAI-Aufruf und **keinen** S24-Ultra-Test.
Am 15.11.2026 wurden Build 173 und anschließend Build 174 direkt auf einem Samsung SM-S928B mit
Android 17 geprüft: installierte APK-Prüfsumme und Paketversion, maskierter Schlüssel, echte Realtime-
Antworten und weiterlaufender Foreground-Service beim TFT-Wechsel. Die frühere Pause nach sechs
Antworten wurde in Build 173 beobachtet; Build 174 hebt sie auf und erreichte auf dem Gerät die echte
Realtime-Verbindung mit Antwortzähler ohne „/6“. Ein separater Live-Durchlauf über die siebte Antwort
hinaus ist noch nicht dokumentiert. Ein Start meldete HTTP 401, spätere Starts funktionierten; die genaue
Ursache wurde nicht festgestellt und der gespeicherte Schlüssel nicht vom Tester ausgelesen.

Korrekte Empfehlungen aus nachweislich aktuellen TFT-Bildern, Mikrofon/Echo und Unterbrechen sind
noch nicht vollständig abgenommen. Mehrere Antworten meldeten fehlende frische Bilder. Der direkte
Gerätetest läuft über Desktop Commander in Termux und vom Nutzer freigegebenes WLAN-ADB, ohne PC.
Fehlt die WLAN-Verbindung oder ändert sich der Debugging-Port, ist damit keine laufende Bildschirm-
beobachtung möglich; der Terminalzugriff allein genügt nicht. Automatische periodische Bildschirm-
übertragung wird erst nach vollständiger Geräteabnahme ergänzt. Issue #93 bleibt bis dahin offen.

Referenzen:
- https://developers.openai.com/api/docs/guides/realtime-conversations
- https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create
- https://developers.openai.com/api/reference/resources/realtime/client-events
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/reference/android/media/projection/MediaProjectionConfig
- https://developer.android.com/media/grow/media-projection#opt-out
