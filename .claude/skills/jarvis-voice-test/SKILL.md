---
name: jarvis-voice-test
description: "Natürliches Freisprechen und Sprachunterbrechungen (Barge-in) von Jarvis diagnostizieren – Messzeile und Logcat auswerten, Echo/Schwelle/Latenz einordnen, Ursache für „unterbricht nicht“, „unterbricht sich selbst“ oder „antwortet zu langsam“ finden. Nutzen bei jedem Problem mit Zuhören, Dazwischenreden, Echo oder Antwortzeit."
argument-hint: "[Symptom, z. B. unterbricht nicht | zu langsam | hört sich selbst]"
---

# /jarvis-voice-test – Freisprechen und Unterbrechungen diagnostizieren

Symptom: $ARGUMENTS

Priorität 1 des Projekts. Ziel ist eine belegte Ursache, kein Herumprobieren an Schwellen.

## 1. Daten sammeln (was vorhanden ist)

- **Messzeile der App** nach einer Antwort (Bildschirmfoto oder Arts Angabe):
  `Zuhören / KI / Stimme / gesamt` und `Echo · Schwelle · lautester Moment · unterbrochen`.
- **Verlauf**: Messwerte früherer Builds in `STATUS.md`.
- **Handy per USB** (nur wenn angesteckt, PowerShell auf Arts Rechner):
  `adb logcat -d -v time | Select-String -Pattern "Jarvis|BargeIn|SpeechGate|AudioRecord|AEC"`
  und `adb logcat -d -b crash`. Mikrofon-Aufnahmen nur mit Arts Zustimmung.

## 2. Einordnen

| Symptom | Typische Ursache | Wo nachsehen |
|---|---|---|
| Unterbrechen klappt nicht | Echo zu laut → Schwelle hoch; Schranke verlangt zu lange Sprache (≈13 von 15 Blöcken à 20 ms) | `BargeIn`, `SpeechGate`, Telefonmodus/AEC in `TalkAudio` |
| Jarvis unterbricht sich selbst | Echo-Unterdrückung aus, Lautsprecher zu laut, Schwelle zu niedrig | `TalkAudio`, `PcmPlayer`, AEC-Status im Log |
| Antwort zu langsam | „KI“-Anteil (Bildgröße, Ablese-Zeile vor erstem Satz) oder Stimme (erste Silbe) | `Timing`, `ClaudeClient`, `StreamAccumulator`, `SpeechChunker`, `CaptureSize` |
| Hört nicht zu / bricht ab | Dienst beendet, Akku-Optimierung, Mikrofon von anderer App belegt | `JarvisService`, `ListenRules`, Logcat |
| Abgehackte Stimme | Puffer, Offline-Stimme zu langsam | `PcmPlayer`, `PocketSpeaker`, `Voices` |

## 3. Belegen statt raten

- Logik-Hypothesen mit Unit-Tests prüfen (`SpeechGateTest`, `ListenRulesTest`,
  `SpeechChunkerTest`, `TimingTest`). Neue Fälle als Test ergänzen.
- Eine einzelne Messung ist nur ein Hinweis – Schwellen erst bei wiederholtem Befund ändern.
- Für Code-Änderungen an der Sprachkette: Agent `android-voice-engineer`; danach `/jarvis-build`.

## Regeln

- Keine kostenpflichtigen Aufrufe (Claude, ElevenLabs, OpenAI) zum Testen ohne Arts Zustimmung.
- Systemeinstellungen auf dem Handy nicht selbst ändern (`handy/tuning.ps1` startet Art).

## Ausgabe

Symptom · Messwerte · wahrscheinlichste Ursache mit Beleg · vorgeschlagene Änderung ·
wie sie getestet wird · was nur auf dem S24 Ultra prüfbar bleibt.
