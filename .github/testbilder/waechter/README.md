# Testbilder für den Phasen-Wächter

Echte TFT-Bildschirmfotos von Arts Galaxy S24 Ultra (8. Nov. 2026). Der Gerätetest
(`.github/scripts/geraetetest.sh`) schickt jedes Bild über die App an den Phasen-Wächter
des proaktiven Coachings und vergleicht die Antwort mit dem Dateinamen.

| Datei | Richtig | Woran man es erkennt |
|---|---|---|
| `planung_1-3__laden-offen.jpg` | PLANUNG 1-3 | nur eigene Einheiten (grüne Balken), Laden offen, keine Gegner |
| `kampf__1-2-a.jpg`, `kampf__1-2-b.jpg` | KAMPF | Gegner mit rotem Balken, „Verursachter Schaden“ rechts |
| `kampf__1-3.jpg` | KAMPF | Monster mit roten Balken in der Brettmitte |
| `kampf__2-1.jpg` | KAMPF | Gegner auf dem Brett, Schadenszahlen |
| `anderes__start-1-1.jpg` | ANDERES | Startinsel mit allen Spielern |

**Namensschema:** Was vor `__` steht, ist die richtige Antwort (`planung_1-3` → `PLANUNG 1-3`).

**Aufbereitet:** Spielernamen und die eingeblendete Claude-App sind verpixelt (das Repo ist
öffentlich). Größe wie in der App: lange Kante 1280 Pixel, JPEG-Qualität 75 (`CaptureSize`).

Neue Bilder: genauso benennen, Namen verpixeln, auf 1280 verkleinern.
