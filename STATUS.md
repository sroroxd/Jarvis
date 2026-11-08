# Stand der Dinge

Diese Datei ist das Gedächtnis des Projekts. Sie steht hier, damit der
Projektstand nicht in einzelnen Chats liegt: Art arbeitet in **einem** Chat, und
wird der zu lang, fängt er einen neuen an. Claude liest zu Beginn jeder Sitzung
`CLAUDE.md` (Arbeitsregeln) und diese Datei (Stand) und ist damit auf dem
aktuellen Stand – egal, in welchem Fenster.

Wer etwas fertigstellt oder entscheidet, trägt es hier ein. Letzte Änderung:
8. November 2026 (14 Uhr).

## Wo der aktuelle Stand liegt

**`main` ist auf dem Stand von Build 87** (8. Nov.):
- 11:08 UTC hat Art PR #22 übernommen (alle früheren Zweige, Gesamtprüfung, Streaming, proaktives Coaching,
  Selbst-Update über die API).
- Am Nachmittag folgte PR #48 (Immer zuhören statt Wachwort, Spielplan, Kosten in Cent, Akku-Knopf,
  Startrunden-Regel). Art hatte ihn ausdrücklich freigegeben („überprüfe und führe ggf. zusammen“), Claude hat
  ihn nach bestandenem Gerätetest und mit Build 86 auf Arts Handy zusammengeführt.

Weiter geht es auf demselben Arbeitszweig `ccr-4f440a4a-dx81et`, jeweils neu aufgesetzt auf `main`, mit einem
neuen PR pro Runde. Gemergte PRs werden nicht wiederverwendet.

### Messwerte von Arts Handy, 8. Nov. (Build 82 um 12:37, Build 86 um 13:36)

| | Build 82 | Build 86 |
|---|---|---|
| Bildschirmfoto mitgeschickt | nein | ja (Spielmodus, Freigabe an) |
| Zuhören / KI / Stimme / gesamt | 0,1 / 3,1 / 0,0 / 3,2 s | 0,1 / 5,2 / 0,0 / 5,3 s |
| Kosten der Antwort | – | 1,3 ct (Cache 5,5 Tsd. gelesen) |
| Echo / Schwelle / lautester Moment | −90 / −40 / −10 dB | −73 / −40 / −31 dB |
| unterbrochen | durch Stimme | nein |

- **Dazwischenreden funktioniert grundsätzlich:** Der Telefonmodus filtert Jarvis' Stimme fast ganz heraus
  (Echo −73 bis −90 dB), die Schwelle liegt damit am Boden von −40 dB. In Build 82 hat Art ihn per Stimme
  unterbrochen.
- In Build 86 lag der lauteste Moment zwar über der Schwelle, aber die Schranke verlangt rund 0,26 s
  durchgehende Sprache (13 von 15 Blöcken à 20 ms). Ein kurzes Geräusch zählt nicht. Ob Art es da überhaupt
  versucht hat, ist offen – deshalb keine Änderung an den Schwellen.
- **Zeit:** Die App selbst braucht 0,1 s; die Wartezeit ist fast ganz „KI“. Mit Bildschirmfoto 5,2 s statt
  3,1 s – zwei Einzelmessungen mit verschiedenen Fragen, also nur ein Hinweis. Größte Hebel für #43: Bildgröße
  und die Ablese-Zeile vor dem ersten gesprochenen Satz.
- **Kosten:** 46 Cent bis 13:36. Eine Antwort mit Bild kostet 1,3 ct; nachgerechnet ist das stimmig (Bild rund
  2.200 Tokens ≈ 0,44 ct, Cache 0,06 ct, übrige Eingabe, Nachdenken und Antwort). Aufschlüsseln nach Fragen,
  Ansagen und Wächter kann die App noch nicht.
- **Arts Anweisung an Jarvis** („… die Logik hinter deinem TFT-Coaching aktualisieren … wie ein Bot für mich …“)
  ist auf dem Bild abgeschnitten und wurde nicht als Wunsch angelegt. Den vollen Satz hat Claude bei Art erfragt.

- **Test-App:** github.com/sroroxd/Jarvis/releases/download/test-ccr-4f440a4a-dx81et/JarvisTest.apk –
  aktualisiert sich selbst (Knopf „Installieren“), ab Build 77 über die GitHub-API.
- **Zusammenführen** nach Arts Test: mit **Merge-Commit, nicht Squash**.

### Gerätetest 8. Nov. mit Build 69 (Art)

- **Selbst-Update über „Installieren“: klappt.** Build 75 kam wegen der 504-Störung bei GitHub nicht durch
  (Punkt 17); Build 77 hat Art per Direktlink auf den Dateispeicher installiert – erfolgreich. Ab 77 lädt die App
  über die GitHub-API.
- **Dazwischenreden: klappt noch nicht.** Vermutete Ursache: Ohne Telefonmodus filtert das Handy Jarvis' Stimme
  nicht heraus, sie kommt am Mikrofon lauter an als Art. Ab Build 70: Telefonmodus im Dauergespräch (Punkt 15)
  und eine Messzeile auf dem Startbildschirm nach jeder Antwort: „Dazwischenreden: Echo … · Schwelle … ·
  lautester Moment … · Echo-Filter an/aus · unterbrochen: … · Telefonmodus an/aus“. So liest man sie:
  lautester Moment unter der Schwelle, obwohl Art geredet hat → Art kam nicht über das Echo (Regler höher
  oder Echo noch zu laut); Echo-Filter „aus“ → das Handy bietet keine Echo-Unterdrückung an.

### Was Art auf dem Handy prüfen muss (Test-Build ab 64)

1. **Satzweises Sprechen:** Fängt Jarvis bei längeren Antworten spürbar früher an zu sprechen? Klingen die
   Übergänge zwischen den Sätzen natürlich, oder entstehen Pausen?
2. **Dazwischenreden:** Unterbricht er sich noch selbst? Kommst du in normaler Lautstärke durch, und
   unterbricht „Jarvis“ zuverlässig?
3. **Selbst-Update:** Läuft das nächste Update über den Knopf „Installieren“ durch?
4. **Spielmodus:** Sind die Antworten jetzt kurz genug (#21)?
5. **TFT automatisch erkennen** (Einstellungen → Spielmodus, einmal „Nutzungsdatenzugriff“ erlauben):
   TFT öffnen → sagt Jarvis „TFT erkannt“ und hört ohne „Jarvis“ zu? Nach dem Spiel (20 s raus) wieder aus?
6. **Empfindlichkeit beim Dazwischenreden** (Einstellungen → Sprachsteuerung, Wunsch #25): Unterbricht
   Jarvis sich selbst → niedriger stellen; kommst du nicht durch → höher.
7. **TFT-Coaching (#23):** Kommen die Befehle schneller? Stimmen Gold, Stufe und Leben, auf die er sich
   bezieht? Wenn die Pläne zu flach wirken: Einstellungen → Spielmodus → „Gründlicher nachdenken“ an
   und vergleichen.
8. **Von selbst coachen (#41, ab Build 71):** Bildschirm freigeben, TFT spielen. Meldet sich Jarvis zu
   Beginn jeder Planungsphase einmal mit einem sinnvollen Befehl? Schweigt er im Kampf? Redet er zu oft
   oder zu selten? Bei Störung: Einstellungen → Spielmodus → „Von selbst coachen“ aus.

## Aufräumen (8. November)

**Erledigt (Claude):** Die elf überholten PRs geschlossen. Die Wunsch-Issues zusammengefasst –
16 offene Wünsche waren 5 Themen: #10 (Selbstabbruch beim Sprechen), #17 (Dazwischenreden
zuverlässig), #21 (kürzere Antworten), #23 (TFT-Coaching hat Vorrang), #25 (Empfindlichkeit für
„Jarvis“ und Dazwischenreden einstellbar); die übrigen sind als Duplikat darauf verwiesen. Ursache der
Doppelten in der App behoben: Sie schickte jeden Wunsch, auch wenn derselbe Titel schon notiert war, und
erkannte Umformulierungen desselben Themas nicht (`Wishes.similar`).

**Erledigt (Art, 8. Nov. 04:11 UTC):** Aufräum-Workflow gelaufen, ohne Warnung. Seitdem gibt es nur noch
die Zweige `main` und `ccr-4f440a4a-dx81et`, die Releases und Tags `v1.0.43` (normale App),
`test-ccr-4f440a4a-dx81et` (Test-App) und `vorschau` (Gerätetest). Ein zweiter Lauf schadet nicht.

Früher erledigt (4. Nov.): Repo geprüft – keine fremden Pushes, keine echten Schlüssel im Code;
„Automatically delete head branches“ ist eingeschaltet.

### Repo-Durchsicht am Nachmittag des 8. Nov. (Arts Auftrag: Doppeltes und Überbleibsel)

**Zweige und PRs:** `zeitmessung-netz` (PR #53, Latenz-Messung und Zeitmessung) übernommen; `latenz-messung`
steckt vollständig darin und kann weg. PR #49 geschlossen – sein einziger Commit kam mit #53 nach `main`.

**Toter Code entfernt:**
- *Gemini ganz raus* (`GeminiClient`, `isGemini`, Modell-Standard, Schlüsselerkennung): Jarvis fragt
  ausschließlich Claude. Die Verzweigungen lagen an elf Stellen und machten jede Messung und jede
  Kostenanzeige doppelt zu lesen.
- *Wachwort als Aktivierung raus*: `WakeWordEngine` (eigenes Mikrofon, Fehler-Wiederholung,
  „Wachwort pausiert“) ist seit „Immer zuhören“ unerreichbar. Geblieben ist `WakeModel` – nur noch das
  Laden des Modells für `BargeIn`, damit „Jarvis“ weiter unterbricht. Auch `Prefs.wakeWord`,
  der Sicherungseintrag und der tote Zweig in `Backup` sind weg.
- Vier fast gleiche `readAll`-Kopien → `Io.readAll`; `BuildConfig.DEBUG` statt viermal `FLAG_DEBUGGABLE`;
  tote Methoden (`echoCancellerActive`, `isSpeaking`, `lastYou`, `lastJarvis`, `wakeProblem`,
  `isScreenAwake`, `setAgentPort`, `Timing.answered`) entfernt.

**Dabei gefundene echte Fehler:**
- *Zeitmessung zeigte fremde Werte:* `ClaudeClient.lastNet` (aus #53) wurde auch vom Phasen-Wächter (alle 6 s)
  und von der Meta-Recherche gesetzt. Auf dem Startbildschirm stand dann deren „Senden/Claude“ statt der
  Werte von Arts Frage. Jetzt zählt nur noch der Thread, der Arts Fragen beantwortet.
- *Dazwischenreden konnte stillschweigend ausfallen:* Kam der nächste Satz, während das Mikrofon noch
  freigegeben wurde, tat `BargeIn.start()` nichts – und für diesen Satz war das Unterbrechen aus. Jetzt
  startet es nach der Freigabe nach.
- Zwei Javadoc-Blöcke aus #53 standen über der falschen Methode.

**Wunsch-Erkennung verbessert:** `Wishes.similar` verglich nur Titel. Arts dreifacher Stimm-Wunsch
(#50–#52) teilt im Titel nur „Stimme“ – daraus wurden drei Issues. Jetzt zählt auch die Beschreibung.
Gegen alle 33 echten Wünsche geprüft (`app/src/test/resources/wuensche_8nov.tsv`): 28 statt 11 doppelte
Paare erkannt, keines falsch zusammengelegt.

**Aufräum-Workflow ohne Pflegeliste:** `aufraeumen.yml` hatte feste Listen vom Morgen, längst abgearbeitet.
Jetzt löscht er selbst, was nachweislich weg kann – Zweige, die ganz in `main` stecken, Test-Apps gelöschter
Zweige, ältere `v1.0.x` außer der neuesten. Mit „nur_anzeigen = ja“ erst ansehen. Logik mit nachgebauten
GitHub-Antworten geprüft.

**Lint:** keine Fehler mehr (`clearCommunicationDevice` braucht API 31), keine ungenutzte Ressource
(`ic_mic.xml` entfernt). README auf den heutigen Stand gebracht.

## Was als Nächstes kommt

0. **Aus dem claude.ai-Chat „Jarvis APK Verbesserung“ (2.–8. Nov.) noch offen:** eigenes Symbol für die
   Test-App und den Schnellstart („Jarvis zuhören“ mit Mikrofon-Symbol). Die automatische TFT-Erkennung
   ist seit dem 8. Nov. gebaut, Wunsch #23 ebenso (siehe Punkt 14 der Gesamtprüfung).
1. **Agenten-Rückgrat, Baustein 2.** Baustein 1 liegt in `sammeltest`
   (Aufträge über Claude Code in Termux, Abo statt Guthaben). Als Nächstes:
   Zwischenstände während langer Aufträge (`--output-format stream-json`), eigene
   Skill-Ordner im Arbeitsordner, und das automatische Erkennen von Aufgaben, die
   zum Agenten gehören – bisher muss Art sie ausdrücklich abgeben.
2. **Design und Bedienung.** Die Oberfläche wirkt billig und soll überarbeitet
   werden.
3. **Stimmklon Deutsch,** näher an der Jarvis-Stimme aus den Iron-Man-Filmen.
4. **TFT-Live-Bild** statt einzelner Bildschirmfotos pro Frage. Teilweise erledigt: Der Phasen-Wächter des
   proaktiven Coachings schaut alle 6 s aufs Bild (Punkt 16 der Gesamtprüfung).
5. **Vollzugriff aufs Handy (#44).** Weg: Android-Bedienungshilfe (AccessibilityService) – damit kann Jarvis
   jede App bedienen (tippen, wischen, schreiben), auch Nachrichten-Apps; Dateien über „Zugriff auf alle
   Dateien“. Apps deinstallieren geht nur über Androids eigenen Bestätigungsdialog. Regeln: Löschen nur nach
   gesprochener Bestätigung, und Text auf dem Bildschirm (Nachrichten, Webseiten) gilt nie als Befehl – sonst
   könnte eine eingehende Nachricht „Jarvis, lösch alle Fotos“ etwas auslösen.
   **Voraussetzung: erst den Signaturschlüssel tauschen** (siehe Vorschläge unten). Mit dem öffentlichen
   Schlüssel könnte jeder ein Update bauen, das sich über Jarvis installiert und diese Rechte erbt.

## Gesamtanalyse vom 8. November (Arts Auftrag)

Geprüft: Architektur, Stabilität, Latenz, Kosten, dazu gezielt Dauergespräch, Echo-Filter,
Bildschirmfreigabe und Kalender. Gemessene Zahlen sind als solche gekennzeichnet, alles andere ist
Schätzung oder Rechnung.

### Architektur

Der Zuschnitt stimmt: Alles, was sich rechnen oder entscheiden lässt, steckt in kleinen Klassen ohne
Android (`SpeechGate`, `SpeechChunker`, `Costs`, `ProactiveCoach`, `Wishes`, `Meta`, `GameDetector`,
`Text`, `Timing`, `StreamAccumulator`, `CaptureSize`, `GameReadout`) – daher 182 automatische Tests.

Die eine Schwachstelle: **`JarvisService` ist mit 1.824 Zeilen die halbe App** und hält Zustandsmaschine,
Mikrofon, Bildschirm, Netz, Coaching, Wünsche und Agent zugleich. Kein einziger Test deckt ihn ab – und
genau dort saßen beide Fehler, die diese Durchsicht gefunden hat. Nächster sinnvoller Schnitt: den
Gesprächs-Ablauf (zuhören → fragen → sprechen → weiter zuhören) als eigene, testbare Klasse herauslösen.

### Kosten (gemessen auf Arts Handy, 8. Nov.)

| Fall | Kosten |
|---|---|
| Frage mit Bildschirmfoto (Spielmodus, Cache greift) | 1,3 ct |
| Frage ohne Bild, Folgefrage | 0,1–0,2 ct |
| Erste Frage einer Sitzung (Cache wird geschrieben) | 1,6 ct |
| Ein Blick des Phasen-Wächters (Haiku) | rund 0,013 ct |
| Arts Testtag bis 13:36 Uhr | 46 ct |

**Das größte Kostenrisiko ist seit heute „Immer zuhören“:** Jede erkannte Äußerung geht an Claude, auch
die, auf die er mit `[STILL]` schweigt. Es gibt keinen Filter davor. Bei laufendem Fernseher sind das
grob 1–2 € pro Stunde (Rechnung: 20 Äußerungen pro Minute à 0,1–0,2 ct). Ein vorgeschalteter Haiku-Wächter,
wie ihn das Coaching schon nutzt, kostet rund 0,0015 ct je Äußerung – etwa siebzigmal weniger.

### Latenz (gemessen)

| Messung | erster vorlesbarer Text |
|---|---|
| Claude selbst, Spielmodus mit Bild (CI, USA) | 1,10 s |
| Claude selbst, Alltag (CI, USA) | 0,63 s |
| Arts Handy, „KI“ mit Bild | 5,2 s |
| Arts Handy, „KI“ ohne Bild | 3,1 s |

Die Lücke liegt also **nicht am Modell**. Seit PR #53 zerlegt die App die Wartezeit in „Senden (KB)“ und
„Claude“; die nächste Messung auf dem Handy zeigt, welcher Teil es ist. Zwei Verdächtige:

1. **Das Hochladen des Bildes.** Im Spielmodus 1920 Pixel lange Kante bei Qualität 82 – mehrere hundert KB
   über den Mobilfunk-Upload.
2. **Die stille Ablese-Zeile steht vor dem ersten Satz.** `[STAND: Phase … | Gold … | Plan …]` wird zuerst
   geschrieben, und `SpeechChunker` liest Klammern nie vor. Jarvis schweigt also, bis diese Zeile fertig ist.
   Das ist Absicht (der Befehl stützt sich auf die abgelesenen Werte), kostet aber messbar Zeit vor dem ersten
   Ton. Vor einem Umbau einmal mit der Zeile am Ende messen und beide Antworten vergleichen.

### Gefundene Schwachstellen

1. **Dauerzuhören kann dauerhaft verstummen (schwer).** Meldet die Spracherkennung fünfmal hintereinander
   einen echten Fehler, ruft die App `stopContinuous(false)`. Das setzt `continuous` auf aus, aber **nicht**
   `listenPaused` – und `updateAlwaysListen()` läuft nur beim Ein- und Ausschalten des Bildschirms oder beim
   Ändern einer Einstellung. Jarvis hört danach nicht mehr zu, obwohl „Immer zuhören“ an ist, bis Art den
   Bildschirm aus- und wieder einschaltet. Die Ansage („Ich beende das Dauergespräch“) passt seit heute auch
   nicht mehr. *Noch nicht behoben – siehe „Was zuerst“.*
2. **Bildschirmfoto blockiert den Haupt-Thread (mittel).** Der Phasen-Wächter wurde heute auf einen eigenen
   Thread verlegt, **Arts eigene Fragen nicht** (`JarvisService` Zeilen 661, 667, 1243, 1249). Pro Frage
   laufen Bitmap-Kopie, Verkleinern und JPEG auf dem Haupt-Thread.
3. **Zwei Vollbilder je Aufnahme (mittel).** `ScreenGrabber.toBitmap` legt erst ein gepolstertes, dann ein
   zugeschnittenes Bitmap an – bei 1920 × 886 je rund 6,8 MB, dazu die verkleinerte Fassung. Alle 6 Sekunden
   während einer Partie. Dazu hält die Methode dabei `lock`, sodass der Mitschnitt-Thread solange wartet.
   Beides kostet Wärme und Akku in langen Partien.
4. **Echo-Filter: in Ordnung.** Arts Messzeilen zeigen Echo −73 bis −90 dB bei Schwelle −40 dB, der
   Telefonmodus filtert Jarvis' Stimme also fast ganz heraus; in Build 82 hat Art per Stimme unterbrochen.
   Der eine echte Fehler (Neustart fiel aus, wenn der nächste Satz schneller kam als die Mikrofon-Freigabe)
   ist heute behoben.
5. **Bildschirmfreigabe und Kalender: in Ordnung.** Projektionsende, Drehung und fehlende Berechtigungen sind
   überall abgefangen, Überlagerungen (`Subtitles`, `FloatingJ`) fangen `addView` ab – der klassische
   Android-Absturz kann hier nicht passieren.

### Die vier großen Bausteine

Aufwand = meine Bauzeit bis zum bestandenen Gerätetest.

| Baustein | Aufwand | Lage |
|---|---|---|
| **Offline-Spracherkennung (whisper.cpp)** | 2–3 Tage | Macht Jarvis unabhängig von Googles Erkennung (die heute fünfmal fehlschlagen kann) und spart die Netzrunde je Äußerung. Kostet dafür dauerhaft Rechenzeit und Akku. **Das Projekt hat heute keine NDK-Werkzeugkette** – die muss zuerst her. |
| **Offline-Stimme (PocketTTS.cpp)** | 3–5 Tage, erst Machbarkeit | Siehe unten. |
| **Agent-Rückgrat, Baustein 2** | 1–2 Tage | Zwischenstände während langer Aufträge (`--output-format stream-json`), eigener Skill-Ordner, selbst erkennen, was zum Agenten gehört. Baustein 1 läuft. |
| **App-Steuerung (#44)** | 3–4 Tage | Bedienungshilfe. **Blockiert:** Der Signaturschlüssel liegt öffentlich im Repo; vorher tauschen, sonst erbt ein fremdes Update diese Rechte. |

### PocketTTS.cpp – geprüft, bevor gebaut wird

`VolgaGerm/PocketTTS.cpp` gibt es (MIT, einzelne C++-Datei, CMake, ONNX Runtime). Es fährt Kyutai Pocket TTS:
100 Mio. Parameter, läuft laut Projekt in Echtzeit auf der CPU, INT8-ONNX, **und kann seit April 2026 Deutsch**
(sechs Sprachen). Es beherrscht Stimmklonen aus einer kurzen Aufnahme – damit wäre Arts „Film-Jarvis“-Stimme
(Wünsche #50–#52 und „Stimmklon Deutsch“) tatsächlich erreichbar, und die App wäre nicht mehr auf die
SherpaTTS-App angewiesen.

Vier Dinge, die den Auftrag teurer machen als er klingt:

1. **Das Projekt nennt nur Linux, macOS und Windows.** Kein Android, kein ARM, kein JNI – nur eine C-Schnittstelle
   (`ptt_create`, `ptt_stream_start`, `ptt_stream_read`) und `libpocket_tts.so`. Der Android-Teil ist unsere Arbeit.
2. **Jarvis hat noch keine NDK-Werkzeugkette** (`app/build.gradle` hat kein `externalNativeBuild`).
3. **Doppelte ONNX Runtime.** Die App bringt schon `onnxruntime-android:1.23.2` mit (fürs Wachwort-Modell);
   PocketTTS holt sich per CMake seine eigene. Zwei `libonnxruntime.so` in einer APK gehen nicht gut – entweder
   gegen die vorhandene linken oder die Java-Nutzung ablösen.
4. **SentencePiece** muss ebenfalls für `arm64-v8a` übersetzt werden.

Deshalb zuerst **ein Tag Machbarkeit**: NDK einrichten, PocketTTS für `arm64-v8a` übersetzen, eine deutsche
Probeausgabe auf dem Gerät hören. Erst danach die restlichen Tage. Scheitert der Tag an Punkt 3 oder 4, ist das
Ergebnis trotzdem etwas wert – die Werkzeugkette braucht whisper.cpp genauso.

### Vertiefung (zweiter Durchgang)

**Die Premium-Stimme schaltet das satzweise Sprechen ganz ab.** In `ask()` steht
`stream = !prefs.elevenKey().isEmpty() ? null : new StreamSpeech(id)`. Sobald ein ElevenLabs-Schlüssel
eingetragen ist, gibt es kein `StreamSpeech` – Jarvis wartet auf die **komplette** Antwort, lädt dann die
**komplette** MP3 (`ElevenTts` lädt ganz herunter und spielt sie über `MediaPlayer`, kein Streaming) und
fängt erst danach an zu reden. Zum Gewinn von 0,6–1,1 s durch das satzweise Sprechen kämen also noch einmal
Antwortende plus Download dazu. Art hat derzeit keinen ElevenLabs-Schlüssel eingetragen, der Nachteil ist also
latent – aber er schlägt genau in dem Moment zu, in dem Art die Stimme verbessern will. **Das ist das stärkste
Argument für PocketTTS:** eine lokale Stimme, die streamt, bringt die bessere Stimme *und* behält das satzweise
Sprechen, statt zwischen beidem zu wählen.

**Nichts wirft das Zuhören wieder an.** `updateAlwaysListen()` wird genau zweimal gerufen: aus `applySettings()`
und aus dem Bildschirm-Empfänger. Es gibt keinen regelmäßigen Wächter. Das ist die Ursache dafür, dass Schwachstelle 1
dauerhaft wirkt statt sich von selbst zu erholen.

**Kalender: gründlich geprüft, in Ordnung.** `Agenda` rechnet ganztägige Termine in UTC (so speichert Android sie)
und normale in Ortszeit, fängt „Ende vor Beginn“ ab, schneidet Zeitzonen-Zusätze bewusst weg und gibt bei
unlesbaren Angaben verständliche Meldungen zurück statt zu raten. `Tools` prüft jede Berechtigung einzeln und
fängt `SecurityException` getrennt ab. Hier ist nichts zu tun.

**Verlauf und Anzeige: kein Leck.** `ChatLog` ist bei 80 Einträgen gedeckelt und synchronisiert, `history` wird
nur vom Haupt-Thread angefasst und paarweise gekürzt, der statische `UiListener` wird in `onStop` gelöst.

**Agent-Rückgrat: tragfähige Basis, eine echte Lücke.** `AgentClient` läuft auf eigenem Thread, nur auf
127.0.0.1, mit Token, 10 Minuten Zeitlimit. Was fehlt: **Während eines langen Auftrags hört Art nichts.** Jarvis
sagt einmal „Ich kümmere mich darum, Sir“ und schweigt dann bis zu zehn Minuten. Zwischenstände
(`--output-format stream-json`) sind deshalb nicht Kür, sondern der eigentliche Punkt von Baustein 2.

**whisper.cpp nachgeprüft statt geschätzt.** Android ist offiziell unterstützt (`examples/whisper.android`),
MIT, Quantisierung und VAD vorhanden – das senkt das Risiko gegenüber PocketTTS deutlich. Aber der
Speicherbedarf ist der Haken: tiny rund 273 MB, base rund 388 MB Arbeitsspeicher. Neben einer laufenden
TFT-Partie ist das viel; realistisch ist tiny oder base quantisiert, und die Erkennungsgüte auf Deutsch muss
gegen Googles Erkennung gemessen werden, bevor umgestellt wird. Für Android nennt das Projekt keine
Geschwindigkeitswerte – also messen.

**Proaktives Coaching, nachgerechnet.** Wächter rund 0,012 ct je Blick, bei 10 Blicken pro Minute und 35 Minuten
rund 4 ct. Ansagen mit Sonnet und scharfem Bild rund 0,7 ct, bei etwa 20 Ansagen rund 14 ct. Zusammen also die
15–20 ct, die in den Einstellungen stehen – die Angabe stimmt. Stellschraube: Die Ansagen denken mit „medium“
vor, das kostet Ausgabe-Tokens und Zeit.

### Was zuerst – drei Punkte

1. **Dauerzuhören absichern und eine Kostenbremse einziehen** (½–1 Tag). Behebt den Fehler, nach dem Jarvis
   dauerhaft verstummt, und deckelt die offene Rechnung: ein Haiku-Wächter vor jeder Äußerung statt Sonnet für
   alles. Beides betrifft das, was seit heute auf Arts Handy läuft – Stabilität und Geld zugleich.
2. **Die Latenz-Lücke schließen** (1 Tag). Die Messung ist eingebaut, die nächste Partie zeigt den Posten.
   Danach der größte Hebel: kleineres Bild, Ablese-Zeile ans Ende, oder beides – gemessen statt geraten.
   Das ist Wunsch #43, Arts ältester offener Punkt.
3. **PocketTTS: der Tag Machbarkeit** (1 Tag). Beantwortet die offene Frage (läuft es auf dem S24 Ultra?),
   bevor drei bis fünf Tage daran hängen, und baut die NDK-Werkzeugkette, die whisper.cpp ebenfalls braucht.

Danach in dieser Reihenfolge: Rest von PocketTTS, Agent-Rückgrat, whisper.cpp, App-Steuerung (nach dem
Schlüsseltausch).

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

12. **Automatische Spielerkennung** (8. Nov.): Einstellung „TFT automatisch erkennen“. Alle 3 s fragt Jarvis
    per `UsageStatsManager.queryEvents` ab, welche App vorne ist (nur bei eingeschaltetem Bildschirm).
    TFT (`com.riotgames.league.teamfighttactics`) vorne → Spielmodus an, Dauergespräch an, „TFT erkannt“.
    20 s außerhalb → nur zurücknehmen, was die Erkennung selbst eingeschaltet hat. Während TFT vorne ist,
    endet das Dauergespräch nicht nach 15 min Stille. Braucht einmalig „Nutzungsdatenzugriff“.
13. **Empfindlichkeit beim Dazwischenreden** einstellbar (Wunsch #25): Stufe 1–10, 5 = 10 dB über dem
    Echo (bisheriger Wert), 1 = 14,8 dB, 10 = 4 dB. Für „Jarvis“ gilt der vorhandene Regler „Empfindlichkeit“.

14. **TFT-Coaching hat Vorrang** (Wunsch #23, 8. Nov.):
    - *Erst lesen, dann befehlen:* Zu jedem Spielbild schreibt Claude zuerst eine stille Zeile
      `[STAND: Phase … | Gold … | Stufe … | Leben …]` (Unsicheres mit „?“) und stützt den Befehl darauf.
      Nie vorgelesen, bleibt aber im Verlauf – die nächste Runde sieht die letzten Werte. Im Testprotokoll als „stand:“.
    - *Tempo:* Spielmodus standardmäßig mit Stufe „low“ wie der Alltag (laut Claude-Doku denkt das Modell ab
      „medium“ vor fast jeder Antwort nach). Schalter „Gründlicher nachdenken“ holt „medium“ zurück.
    - *Geprüfte Meta:* Eine Recherche ohne „Set <n>“ und „Patch <n>“ in der ersten Zeile (oder zu dünn)
      wird nicht gespeichert und nach 30 Minuten wiederholt; Jarvis sagt bis dahin, dass sein Stand veraltet
      sein kann.

15. **Telefonmodus fürs Dazwischenreden** (8. Nov., nach Arts Gerätetest): Im Dauergespräch mit Dazwischenreden
    schaltet Jarvis in den Kommunikationsmodus (`MODE_IN_COMMUNICATION` + `setCommunicationDevice(Lautsprecher)`,
    laut Android-Doku) und spielt seine Stimme als Sprachkommunikation ab – wie ein Videoanruf. Nur dort filtert
    die Echo-Unterdrückung laut Berichten auf Samsung-Geräten das eigene Abspielen heraus. Nicht mit
    Kopfhörern (kein Echo; Bluetooth würde auf Telefon-Ton wechseln), erst ab Android 12. Schalter
    „Echo-Filter wie beim Telefonieren“ (Standard an). Dazu die Messzeile (siehe Gerätetest oben).
    Ungeprüft: Klang, Lautstärke (jetzt Anruflautstärke), Spielsound von TFT im Telefonmodus.

16. **Von selbst coachen** (Wunsch #41/#42, 8. Nov.): Jarvis meldet sich zu Beginn jeder Planungsphase
    ungefragt mit dem wichtigsten Befehl, wenn nötig samt Aufstellung – oder schweigt, wenn nichts zu tun ist.
    Zwei Stufen: Ein Phasen-Wächter (Claude Haiku, Bild in Alltagsgröße, alle 6 s) antwortet nur
    „PLANUNG 3-2“, „KAMPF“ oder „ANDERES“. Beginnt danach eine neue Planungsphase (Kampf oder Karussell
    dazwischen, oder die Phasen-Anzeige hat gewechselt; frühestens 25 s nach der letzten Ansage), schaut Sonnet
    aufs scharfe Bild und sagt höchstens zwei kurze Sätze – oder antwortet `[STILL]`, dann bleibt Jarvis stumm.
    Hat Art in dieser Planungsphase selbst gefragt, entfällt die Ansage. Nur wenn Jarvis gerade nichts tut oder
    im Dauergespräch ohne Worte zuhört; mit Spielerkennung nur, wenn TFT vorne ist. Braucht die
    Bildschirmfreigabe. Schalter „Von selbst coachen“ (Einstellungen → Spielmodus, Standard an).
    Kosten (geschätzt, nicht gemessen): Wächter rund 4 Cent pro Partie, Ansagen rund 0,8 Cent pro Stück, zusammen etwa
    15–20 Cent pro Partie.
    Logik in `ProactiveCoach` (8 Unit-Tests). Der CI-Gerätetest schickt dem Wächter einen gemalten
    Einstellungs-Bildschirm an die echte API und erwartet „ANDERES“ (prüft Modellname und Format). **Ungeprüft:** ob Haiku die Phasen auf echten TFT-Bildern
    sicher erkennt – dafür braucht es ein paar echte Bildschirmfotos von Art (Planung, Kampf, Karussell).

17. **Selbst-Update übersteht GitHub-Störungen** (8. Nov., Art: „75 lässt sich nicht automatisch herunterladen“):
    Der normale Download-Link github.com/…/releases/download/… antwortete zwei von drei Malen nach 11 s mit 504,
    dieselbe Datei über die GitHub-API (`releases/assets/<id>`, „Accept: application/octet-stream“) sofort. Die App
    lädt jetzt zuerst über die API, dann über den Link, insgesamt bis zu vier Versuche mit Pause. Schlägt alles
    fehl, bietet der Hinweis „Nochmal“ an – vorher kam ein neuer Versuch erst nach 30 Minuten. Der Gerätetest
    lädt die veröffentlichte Test-App ohne Anmeldung über die API und meldet das Ergebnis („Update-Weg“).
    Alte Builds (bis 75) haben den neuen Weg noch nicht: bei 504 die App aus der Übersicht wischen, neu öffnen,
    erneut „Installieren“.

18. **Nach Arts Blick auf die Einstellungen (8. Nov., Build 77):**
    - *Meta zeigte englisches Vorgeplänkel* („I have enough to answer …“) statt der Recherche. Die Prüfung fand
      Set und Patch irgendwo im Text und speicherte alles. Jetzt zählt die Meta erst ab der Kopfzeile
      „Set …, Patch …“ (`Meta.extract`); alte Einträge werden beim Anzeigen und in der Grundanweisung bereinigt.
    - *„Jarvis“ unterbrach beim Sprechen nie*, wenn „Freihändig mit Jarvis“ aus war (bei Art aus) – obwohl Hilfe und
      Einstellungen es versprechen. Jetzt hört das Dazwischenreden-Mikrofon im Dauergespräch immer auch auf „Jarvis“.
    - *Wunschliste:* „✓“ hieß „gesendet“, Art las „erledigt“. Jetzt mit echtem Stand von GitHub (offen, erledigt,
      Doppelte nur als Anzahl).
    - *Ungefragte Ansagen denken gründlicher* („medium“): Darauf wartet niemand, und laut Gedächtnis ist Art
      Qualität wichtiger als Tempo. Arts eigene Fragen bleiben schnell („low“).
    - Knopf „Wiederherstellen“ brach um → „Zurückholen“; Hilfe „So nutzt du Jarvis“ beschreibt TFT und Coaching.

19. **Arts Test mit Build 79 (8. Nov.):** Meta zeigt jetzt „Set 18 (Enchanted Wilds), Patch 18.6 …“, die
    Wunschliste den echten Stand – beides bestätigt. Neu umgesetzt:
    - *Wunsch #45:* Im Verlauf steht „Sie:“ statt „Du:“ vor Arts Eingaben.
    - *Wunsch #46:* Die Handy-Stimme spricht „Sir“ englisch – für sie wird es „Sör“ geschrieben
      (`Text.sirForDeviceVoice`, auch bei „Probe hören“). ElevenLabs bekommt weiter „Sir“.
    - *Messzeilen waren im Dauergespräch nie zu sehen* („Letzte Antwort“, „Dazwischenreden“): Sie standen nur im
      Leerlauf außerhalb des Dauergesprächs da, nach dem Beenden war die Zeile ganz weg. Jetzt immer, sobald es
      Werte gibt – auch nach dem Beenden.

20. **Arts echte TFT-Bilder als Testbilder (8. Nov.):** Sechs Bildschirmfotos (Planung 1-3 mit offenem Laden,
    vier Kämpfe in 1-2, 1-3 und 2-1, Start 1-1) liegen verpixelt und auf App-Größe gebracht in
    `.github/testbilder/waechter`. Der Gerätetest schickt sie über die App an den Wächter und berichtet, wie viele
    er richtig einordnet (nur Bericht, kein Abbruch).
    - *Erkenntnis aus den Bildern:* Kampf und Planung sehen fast gleich aus. Der Unterschied: Im Kampf stehen
      Gegner mit ROTEN Lebensbalken auf dem Brett, es gibt Schadenszahlen und rechts „Verursachter Schaden“; in der
      Planung nur eigene Einheiten mit grünen Balken, oft mit offenem Laden. Diese Merkmale stehen jetzt in der
      Frage an den Wächter, dazu „im Zweifel KAMPF“.
    - *Eine Ansage pro Runde:* Ist die Rundenanzeige lesbar, entscheidet sie allein. Hält der Wächter ein Kampfbild
      einmal für Planung, schweigt Jarvis – Planung und Kampf einer Runde tragen dieselbe Nummer (Test ergänzt).
    - Noch offen: ein zweites Planungsbild, möglichst ohne offenen Laden und aus Stufe 3 oder 4.

21. **Abgleich mit Arts Definition vom 30. Okt.** (Dokument „Jarvis – Definition & Build-Prompt“), drei Lücken
    geschlossen:
    - *Spielplan:* Claude legt beim ersten lesbaren Brett einen Plan fest (Comp, Carry, Gegenstandsträger), hält ihn
      und wechselt nur mit Ansage „Planwechsel: …“. Der Plan läuft in der stillen Ablese-Zeile mit
      (`[STAND: … | Plan …]`) und steht so in jeder Runde im Verlauf. „Plan?“ nennt ihn in einem Satz, „Warum?“
      begründet den letzten Rat. Bei Schlüsselmomenten (Helden-Verstärkung, Embleme, Artefakte, Gold in
      Aktualisierungen auf Stufe 8/9) ein dritter Satz. Ungefragte Ansagen sprechen an, was Platzierung kostet.
    - *Kosten in Cent:* jede Claude-Anfrage wird mit den Preisen der Anthropic-Preisliste (geprüft 8. Nov.: Sonnet 5.5
      2/2,50/0,10/10 $, Haiku 5.5 0,10/0,125/0,01/0,50 $ je Million Tokens für Eingabe/Cache schreiben/Cache
      lesen/Ausgabe, Websuche 1 Cent) abgerechnet (`Costs`). „Letzte Antwort“ zeigt die Kosten der Antwort, darunter
      „Heute bisher: … (US-Cent)“ – inklusive Phasen-Wächter und Meta-Recherche.
    - *Akku:* Einstellungen → Spielmodus zeigt „Im Hintergrund weiterlaufen lassen“, solange Jarvis der
      Akku-Optimierung unterliegt (Systemdialog, Berechtigung `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
    - Bewusst nicht übernommen: frei editierbarer Systemprompt (Verbesserungen kämen sonst nicht mehr an).
      Verschlüsselter API-Schlüssel kommt mit dem Tausch des Signaturschlüssels.

22. **Startrunde 1-1 ist nie Planung** (Gerätetest Build 84): Der Wächter hielt Arts Startbild einmal für
    „PLANUNG 1-1“ (vorher zweimal richtig „ANDERES“) – ein Sprachmodell antwortet nicht jedes Mal gleich. Jetzt
    entscheidet die App selbst: 1-1 zählt nie als Planungsphase, also keine Ansage zum Start. Der Gerätetest prüft
    die Entscheidung der App („=> ANDERES“), nicht nur die Rohantwort.

23. **Immer zuhören statt Wachwort** (Wünsche #47 „Wachwort entfernen“ und #39 „reagiert, sobald Art losredet“):
    - Solange Jarvis läuft und der Bildschirm an ist, läuft das Dauergespräch von selbst – einfach losreden, ohne
      „Jarvis“ und ohne Antippen. Bildschirm aus: Zuhören endet still, beim Einschalten geht es weiter. Keine
      Viertelstunden-Grenze mehr. Antippen oder „Gespräch beenden“ pausiert, bis Art wieder antippt.
    - Was nicht an Jarvis gerichtet ist (Gespräch mit anderen, Fernseher, Selbstgespräch), beantwortet Claude mit
      `[STILL]` – Jarvis bleibt stumm (derselbe Weg wie beim proaktiven Coaching). Die Regel steht im Kontext jeder
      Frage im Dauergespräch (`Persona.turnContext`, openMic).
    - Das Wachwort als Aktivierung ist entfernt (`Prefs.wakeWord()` liefert immer false; Schalter „Freihändig“ und
      Chip „Wachwort“ weg, dafür Schalter „Immer zuhören“ und Chip „Zuhören“). „Jarvis“ unterbricht weiter, während
      er spricht; der Regler heißt jetzt „Empfindlichkeit für „Jarvis““.
    - Grenzen: Jede gehörte Äußerung geht an Claude (mit Cache rund 0,1–0,2 Cent, mit Bildschirmfreigabe im
      Spielmodus rund 0,6 Cent) – die Tagessumme steht auf dem Startbildschirm. Akku: die Spracherkennung läuft
      durchgehend, solange der Bildschirm an ist. Ein sparsamerer Weg (Silero VAD vor der Spracherkennung, #39) ist
      der nächste Schritt, falls Akku oder Kosten stören.
    - Im CI-Gesprächstest ist „Immer zuhören“ aus (kein Mikrofon im Emulator).

**Geprüft:** Build (Release und Debug), alle 180 Unit-Tests inklusive Wachwort mit echten Aufnahmen.
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
