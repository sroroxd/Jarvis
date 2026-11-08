# Stand der Dinge

Diese Datei ist das Gedächtnis des Projekts. Sie steht hier, damit der
Projektstand nicht in einzelnen Chats liegt: Art arbeitet in **einem** Chat, und
wird der zu lang, fängt er einen neuen an. Claude liest zu Beginn jeder Sitzung
`CLAUDE.md` (Arbeitsregeln) und diese Datei (Stand) und ist damit auf dem
aktuellen Stand – egal, in welchem Fenster.

Wer etwas fertigstellt oder entscheidet, trägt es hier ein. Letzte Änderung:
8. November 2026 (12 Uhr).

## Wo der aktuelle Stand liegt

**`main` ist seit dem 8. Nov., 11:08 UTC, auf dem Stand von Build 82**: Art hat PR #22 mit Merge-Commit
übernommen (alle früheren Zweige, Gesamtprüfung, Streaming, proaktives Coaching, Selbst-Update über die API).

Weiter geht es auf demselben Arbeitszweig `ccr-4f440a4a-dx81et`, neu aufgesetzt auf `main`, mit einem
**neuen PR** nach `main` (der gemergte #22 wird nicht wiederverwendet). Inhalt: Punkt 21 (Spielplan, Kosten,
Akku) und die Startrunden-Regel des Wächters (Punkt 22).

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

**Geprüft:** Build (Release und Debug), alle 179 Unit-Tests inklusive Wachwort mit echten Aufnahmen.
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
