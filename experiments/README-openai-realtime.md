# JARVIS Realtime: eigenständiger Termux-Test

Kein APK-Eingriff und kein Ersatz für den bisherigen JARVIS Build 158.
Voraussetzung: Node.js in Termux (`pkg install nodejs`).

## Start (Samsung, Termux)

```sh
curl -fL https://raw.githubusercontent.com/sroroxd/Jarvis/openai-realtime-termux-20261115/experiments/jarvis-realtime-test.mjs -o jarvis-realtime-test.mjs
node jarvis-realtime-test.mjs
```

Den eigenen OpenAI-API-Schlüssel bei der **verdeckten Eingabe in Termux** einfügen
und Enter drücken. Er wird nicht auf Festplatte geschrieben. Die Datei mit dem
Schlüssel keinesfalls auf GitHub hochladen. Falls der Schlüssel noch in der
Zwischenablage liegt, dort nach dem Start löschen.

Im Terminal erscheint ein einmaliger `http://127.0.0.1:8788/?access=...`-Link.
**Diesen Link direkt auf demselben Handy in Chrome öffnen**, Mikrofonfreigabe
bestätigen, auf **Gespräch starten** tippen und einfach sprechen.
Beenden über den Button oder mit `Strg+C` in Termux.

## Eigenschaften / Grenzen

- Modell: `gpt-realtime-2.1-mini`, OpenAI GA Realtime WebRTC.
- Der Langzeit-API-Schlüssel bleibt ausschließlich im Speicher des Termux-Prozesses.
- Die Webseite erhält nur ein kurzlebiges OpenAI-Token.
- Nur ein Start der Sprachsitzung pro Prozess; nach spätestens zwei Minuten im
  Browser wird die Verbindung getrennt. Der Test verursacht trotzdem API-Kosten.
- Der lokale Dienst ist an `127.0.0.1` gebunden. Die Webseite und Token-Ausgabe
  sind mit einem zufälligen Sitzungscookie geschützt; der Server speichert keine
  Logs von Gesprächen oder Geheimnissen.
- Nur ein technischer Sprachnachweis: kein Zugriff auf den TFT-Bildschirm, keine
  Android-Overlay-Funktion, kein Wechsel in den Hintergrund und **keine Integration
  in die bestehende JARVIS-App**. TFT im Vordergrund ist erst beim Android-Einbau testbar.
- Keine automatische Guthabenaufladung oder Änderung deiner OpenAI-Kontoeinstellungen.
- Stopp nach zwei Minuten ist eine Testbegrenzung, **kein garantiertes Geldlimit**.

## Entwicklung

```sh
node --test experiments/jarvis-realtime-test.test.mjs
```

Die Tests sind lokal und verwenden **keinen echten API-Schlüssel**. Die echte
Audio-Verbindung muss auf dem Samsung-Gerät mit dem berechtigten API-Konto geprüft werden.

API-Referenz: https://developers.openai.com/api/docs/guides/voice-webrtc
