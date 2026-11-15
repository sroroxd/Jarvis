---
name: jarvis-build
description: "Debug-APK von Jarvis über die CI bauen lassen und überprüfen – Zweig pushen, GitHub-Actions-Lauf (Build, Unit-Tests, Emulator-Gerätetest) abwarten, Ergebnis, Bericht und Bildschirmfotos prüfen, Fehler analysieren. Nutzen nach jeder Code-Änderung und bevor etwas als fertig gilt."
argument-hint: "[zweig]"
---

# /jarvis-build – Debug-APK erstellen und überprüfen

Zweig (optional, sonst der aktuelle): $ARGUMENTS

Es gibt **kein lokales JDK/Gradle**. Nicht lokal bauen – die CI baut bei jedem Push.

## Schritte

1. **Vorprüfung**: nicht auf `main` (`git branch --show-current`). Arbeitsstand committet,
   Autor `sroroxd <193030253+sroroxd@users.noreply.github.com>`, Committer
   `Claude <noreply@anthropic.com>`. Kein `[skip actions]` – sonst baut nichts.
2. **Pushen**: `git push -u origin <zweig>` (bei Netzfehler bis zu 4× mit Pause wiederholen).
3. **Stand erkennen**: Enthält der Zweig `scripts/signierung/apk-pruefen.sh` (PR #95)?
   - ja → Debug-APK als Artefakt „Jarvis-Debug-APK“, Bericht als „geraetetest-bericht“.
   - nein → Test-App als Release `test-<zweig>`, Bericht unter
     `releases/download/vorschau/geraetetest.txt` und `*.png`.
4. **Ergebnis abwarten**: Check-Runs des Commits/PRs abrufen (`pull_request_read` →
   `get_check_runs`). Nicht im Sekundentakt abfragen; bei PR-Abo auf Ereignisse warten.
   Erwartete Jobs: `build` → `geraetetest` → `veroeffentlichen` (bzw. Artefakte).
5. **Bei Fehlschlag**: `get_job_logs` nur für fehlgeschlagene Jobs. Erste Ursache suchen
   (`error:`, `FAILED`, `What went wrong`, `FATAL EXCEPTION`, `ANR in`, „Prozess beendet“).
   Ursache beheben, Test ergänzen, erneut pushen. Für tiefere Analysen: Agent
   `build-test-engineer`.
6. **Bei Erfolg**: Bericht `geraetetest.txt` lesen und mindestens die Bildschirmfotos
   „Start“ und „Einstellungen“ ansehen – leer, abgeschnitten, Fremd-Dialog?

## Regeln

- Keine kostenpflichtigen Live-Tests: `cloud_tests` nur mit Arts ausdrücklicher Zustimmung.
- Nichts mergen, keine Releases von Hand, nichts löschen.
- Nie Tests überspringen oder abschwächen, um Grün zu bekommen.

## Ausgabe

Lauf-Link · Build ✓/✗ · Unit-Tests ✓/✗ (Anzahl, falls im Log) · Gerätetest ✓/✗ ·
APK-Link bzw. Artefakt · Auffälligkeiten aus Bericht/Bildern · **ungeprüft**: Mikrofon,
Echo, Akku, Stimme, echtes S24 Ultra (x86-Emulator, ohne API-Schlüssel).
