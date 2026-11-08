# Wachwort „Jarvis“ – gewähltes Modell

**Variante a** aus Lauf 41429554101 (2. Nov. 2026): Netz mit 32 Einheiten, Gegenbeispiele bis 1500-fach gewichtet,
Rezept wie openWakeWord. Trainiert auf 68.400 Jarvis- und 47.880 Gegenbeispiel-Fenstern (23 Piper-Stimmen,
rund 2.200 Sprecher, 270 echte Raumhall-Aufnahmen) plus 1,05 Mio. Fenstern Alltagsaudio (ACAV100M, ca. 370 h).

| Messung | neu (`jarvis.onnx`) | alt (`hey_jarvis_v0.1.onnx`) |
|---|---|---|
| Fehlalarme pro Stunde (10,7 h Validierungsaudio, Schwelle 0,5) | 0,47 | – |
| Erkannt, englische Teststimmen | 77–82 % | 65–67 % |
| Erkannt, deutsche Teststimmen | 47–48 % | 10–18 % |
| „Jarvis.“ allein (englisch) | 75–83 % | 42 % |
| „Hey Jarvis.“ | 100 % | 100 % |
| Ähnliche Sätze („Ja, was …“, Travis, Service …) | 0–2 % | 0–1 % |

Teststimmen: kerstin, pavoque (deutsch), alan, amy (englisch) – kommen im Training nie vor. Je Satz 3 Tempi,
sauber und mit Stimmengewirr (10 dB), Streaming genau wie in der App, 2 s Vorlauf. Altes und neues Modell
gemeinsam bringen keinen zusätzlichen Treffer, nur mehr Fehlalarme → die App lädt nur `jarvis.onnx`.

Schwachstelle: einzelnes, sehr deutsch ausgesprochenes „Dschaawis.“ (bei der Stimme kerstin auch für das alte
Modell 0 %). Wirksamste Verbesserung: echte Aufnahmen von Art („Jarvis“, 20–30×) ins Training aufnehmen.

`training.log` und `training.json`: Verlauf aller Prüfpunkte (Trefferquote, Fehlalarme pro Stunde).
