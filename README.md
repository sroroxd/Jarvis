# Jarvis

Persönlicher Android-Assistent für das Galaxy S24 Ultra – mit Claude (oder Gemini) als Gehirn.

## Herunterladen

**Immer die neueste Version:** https://github.com/sroroxd/Jarvis/releases/latest/download/Jarvis.apk

Jede Änderung am Code wird automatisch getestet und als neue APK veröffentlicht. Sie lässt sich direkt über die alte installieren; Schlüssel, Gedächtnis und Einstellungen bleiben erhalten.

## Einrichten

1. APK installieren („Aus dieser Quelle erlauben“ bestätigen).
2. Jarvis öffnen, API-Schlüssel einfügen und **Schlüssel testen** tippen – Jarvis erkennt den Anbieter selbst:
   - **Claude:** platform.claude.com → API Keys (beginnt mit `sk-ant-`)
   - **Gemini (kostenlos, mit Limit):** aistudio.google.com → Get API key (beginnt mit `AIza`)
3. Das Feld **Modell** leer lassen: Alltag = Claude Haiku 5.5 (sehr günstig), TFT-Modus = Claude Sonnet 5.5.
4. **Jarvis starten** → Mikrofon, Mitteilungen, „Über anderen Apps anzeigen“ erlauben → **Gesamter Bildschirm** teilen.
5. Optional: Seitentaste → Zweimal drücken → App öffnen → **Jarvis sprechen**.

## Bedienung

| J-Farbe | Bedeutung |
|---|---|
| Blau | bereit |
| Rot | hört zu |
| Orange | denkt nach |
| Grün | spricht |

- **Antippen:** sprechen. Pausen sind erlaubt – abgeschickt wird nach der eingestellten Sprechpause oder sofort durch erneutes Antippen.
- **Während Jarvis spricht antippen:** unterbrechen und neu fragen.
- **Lange drücken:** neues Gespräch. **Ziehen:** verschieben.
- **Antwortfeld antippen:** ganzen Text zeigen, nochmal: schließen.
- **„Merk dir …“** speichert dauerhaft, **„Vergiss …“** löscht. Alles einsehbar unter „Gedächtnis“ in der App.

## Stimme

Standard ist **Alan** (britisches Englisch, Piper „medium“) aus **SherpaTTS** mit Tempo 0,9. Engine, Stimme, Tempo, Tonhöhe und Sprechpause sind in der App einstellbar („Probe hören“). Alan ist eine englische Stimme und liest deutsche Antworten mit englischer Aussprache; für reines Deutsch eine deutsche SherpaTTS-Stimme wählen.

## Projektaufbau

| Datei | Aufgabe |
|---|---|
| `JarvisService` | Schwebendes J, Zuhören, Bildschirmfoto, Anfrage, Vorlesen |
| `MainActivity` | Einstellungen und Start |
| `ListenActivity` | Schnellstart „Jarvis sprechen“ (Seitentaste) |
| `ClaudeClient`, `GeminiClient` | Anbindung der KI-Anbieter |
| `Persona` | Anweisungen: Ton, Kürze, Ehrlichkeit, TFT-Modus |
| `Memory` | Dauerhaftes Gedächtnis („Merk dir“ / „Vergiss“) |
| `Text` | Textbereinigung, Schlüssel-/Modellprüfung, Fehlermeldungen |
| `Voices` | Sprachausgabe-Engine und Stimmwahl |
| `ScreenGrabber` | Bildschirmaufnahme |
| `Prefs` | Gespeicherte Einstellungen |

Automatische Tests liegen in `app/src/test` und laufen bei jedem Build.

## Ausbaustufen

- [x] **Stufe 1:** Sprechen, Bildschirm sehen, natürliche Antwort, TFT-Modus, Gedächtnis, Stimmwahl
- [ ] Aktivierungswort „Hey Jarvis“ ohne Antippen
- [ ] **Stufe 2:** Nachrichten vorlesen und beantworten, Erinnerungen, Wecker, Termine
- [ ] **Stufe 3:** Apps per Sprache steuern
