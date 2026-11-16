# JARVIS – Neubau von Grund auf

Stand: 16. November 2026. Dieser Ordner ist ein **eigenständiger Neubau** und keine Reparatur der bisherigen Android-App.

## Ziel
Ein natürlicher, bidirektionaler KI-Sprachassistent für Arts Samsung Galaxy S24 Ultra:
- App starten und **frei sprechen**, ohne Wachwort oder Texteingabe.
- Laufende mündliche Antworten, jederzeit unterbrechbar; danach unmittelbar weiterreden.
- Folgefragen behalten den Gesprächskontext, bis Art das Gespräch zurücksetzt.
- Sprachmodus bleibt während einer aktiven Sitzung zuverlässig eingeschaltet.
- **Keine stillen API-Kosten** und kein automatischer Wechsel zu kostenpflichtigen Diensten.
- Erst nach erfolgreichem Gesprächs-Test: Bildschirmfreigabe, Echtzeit-TFT-Coaching und weitere Gerätesteuerung.

## Strikte Trennung vom Altprojekt
- Neue App-ID für die Test-App; die bisher installierte JARVIS-App wird nicht überschrieben.
- Keine Übernahme von altem ClaudeClient, Termux-Agenten, persönlichem GitHub-Token oder alten Signaturschlüsseln.
- Keine Geheimnisse im Quellcode, in Testprotokollen oder im Build.
- Original-App und `main` bleiben unverändert. Arbeit nur auf isoliertem Zweig und später nach geprüftem PR.
- Engine-Vertrag ausdrücklich austauschbar: Spracheingabe, KI-Antwort und Sprachausgabe als getrennte Komponenten. Die Engine-Wahl muss **praktisch verfügbar und kostentransparent** sein, bevor sie eingebaut wird.

## Erste überprüfbare Etappe
1. Ein unabhängiges Android-Projekt mit eigener Debug-Test-App anlegen.
2. Einen wiederholbaren automatischen Test für den Start, die Mikrofonzustände und den Gesprächsablauf bauen.
3. Erst eine **echte** Ende-zu-Ende-Sprachunterhaltung als bestanden melden, wenn Spracheingabe, KI-Antwort, Sprachausgabe, Dazwischenreden und Kontext tatsächlich nachgewiesen sind.
4. Vor der Gerätefreigabe APK bauen und auf Emulator prüfen. Mikrofon, Latenz und Unterbrechung müssen danach auf Arts S24 Ultra geprüft werden.
5. Kein Release oder Überschreiben der bestehenden App ohne Arts ausdrückliche Zustimmung.

## Was bisher *nicht* erledigt ist
Dies ist der verbindliche Startpunkt. Noch keine neue APK, kein geprüfter KI-Dialog, keine neue Engine implementiert. Der grüne Build der alten App zählt **nicht** als Test des Neubaus.
