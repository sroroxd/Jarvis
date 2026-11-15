# Signaturschlüssel: Lage, Wechsel, Auswirkungen

Stand 15. Nov. 2026 (Phase 1 „Sicherheit und Build-Reparatur“). Vorarbeit: PR #84 und PR #89, Issue #85.

## Die Lage

Der Schlüssel, mit dem alle bisherigen Jarvis-APKs signiert sind (`app/jarvis.keystore`), liegt seit dem
31. Okt. 2026 samt Passwort im öffentlichen Repository. Er gilt als **kompromittiert**: Jeder kann damit eine APK
bauen, die Android als gültiges Update für Jarvis annimmt. Aus der Git-Historie lässt er sich nicht mehr
zurückholen – wer ihn wollte, hat ihn.

Fingerabdruck des kompromittierten Zertifikats (SHA-256, öffentlich):
`548a724b0101b6666476d4ecc75abf3f101dbe10e8b3a06d0c2b378d94f2f9b6`

Praktisches Risiko heute: Jarvis lädt Updates nur über HTTPS aus den Releases von `sroroxd/Jarvis`. Ein Angreifer
bräuchte also Schreibzugriff auf das Repo oder müsste Art eine fremde APK unterschieben (Link, Datei). Dann aber
würde sie sich ohne Warnung über Jarvis installieren und käme an API-Schlüssel, Tokens, Mikrofon und
Bildschirmfreigabe.

## Was seit Phase 1 gilt

| | vorher | jetzt |
|---|---|---|
| Passwort in `app/build.gradle` | stand im Klartext drin | entfernt – in der Datei steht kein Schlüssel und kein Passwort |
| Debug-APK | alter Schlüssel | Androids Debug-Schlüssel, eigene Paket-ID `….debug` |
| Release-APK | alter Schlüssel, von Gradle signiert | Gradle baut **unsigniert**; signiert wird in der CI mit dem **neuen** Schlüssel |
| Veröffentlichung (Release, Test-App, Vorschau) | bei jedem Push | nur, wenn der neue Schlüssel als GitHub-Secret hinterlegt ist |
| Prüfung | keine | `scripts/signierung/apk-pruefen.sh` bricht den Build ab, wenn eine APK den kompromittierten Schlüssel trägt |

`app/jarvis.keystore` bleibt vorerst im Repo. Löschen brächte keine Sicherheit (er ist öffentlich), und er wird
noch **einmal** gebraucht: für den Rotationsnachweis, ohne den sich die neue Version nicht über die installierte
legen ließe. Er signiert nie mehr allein.

## Auswirkungen auf das, was auf dem Handy installiert ist

- **Jarvis und Jarvis Test laufen unverändert weiter.** Nichts wird deinstalliert, keine Daten gehen verloren.
- **Bis der neue Schlüssel hinterlegt ist, gibt es keine neuen Releases und keine neuen Test-Apps** – also auch
  kein Update über den Knopf „Installieren“. Das ist Absicht: lieber kein Update als eines mit dem alten Schlüssel.
- **Danach kommt das erste Update mit Rotationsnachweis** (APK Signature Scheme v3.1). Android prüft: „Der alte
  Schlüssel bestätigt den neuen“ – und installiert das Update über die vorhandene App, Einstellungen und
  Gedächtnis bleiben. Ab diesem Update gilt auf dem Handy der neue Schlüssel; eine nur mit dem alten Schlüssel
  signierte APK lehnt Android dann ab.
- **Falls das Update auf dem Handy doch scheitert** (Meldung „App nicht installiert“): vorher über
  Einstellungen → Sicherung eine Sicherung anlegen, Jarvis deinstallieren, neu installieren, Sicherung
  zurückholen. Das wäre die einmalige Neuinstallation; der Rotationsnachweis soll genau sie vermeiden.
- **Debug-APK** (Artefakt „Jarvis-Debug-APK“ am CI-Lauf): eigene App „Jarvis (Test) Debug“ neben den anderen.
  Sie ersetzt nichts. Jeder CI-Lauf signiert sie mit einem frischen Debug-Schlüssel – eine neuere Debug-APK lässt
  sich deshalb nicht über eine ältere installieren; die alte Debug-App vorher deinstallieren (betrifft nur deren
  eigene Daten). Debug-Builds sind zum Prüfen da, nicht für den Alltag: Sie enthalten Test-Zugänge
  (Intent-Extras wie `testkey`) und schreiben Gesprächstext ins Geräteprotokoll.
- **Android 11–12L (API 30–32):** Dort gilt weiter die alte Signatur, weil die Rotation auf diesen Versionen laut
  apksigner-Doku nicht verlässlich ist. Arts Galaxy S24 Ultra ist nicht betroffen. Für andere Geräte bleibt das
  Risiko bestehen.
- **Geht der neue Schlüssel verloren, gibt es nie wieder ein Update ohne Neuinstallation.** Datei und Passwort
  deshalb an zwei Orten sichern (Passwort-Manager, verschlüsselter Datenträger) – nicht im Repo, nicht im Chat.

## Was Art einmalig tun muss

Erst danach veröffentlicht die CI wieder. Der private Schlüssel entsteht bei Art und geht nur als GitHub-Secret
in die CI – nie durch einen Chat und nie ins Repository.

1. **Schlüssel erzeugen** – auf einem Rechner mit Java, zum Beispiel in Termux auf dem Handy
   (`pkg install openjdk-21`):

   ```bash
   keytool -genkeypair -storetype PKCS12 -keystore jarvis-neu.p12 -alias jarvis \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Jarvis, O=Art"
   ```

   `keytool` fragt nach einem neuen Passwort (lang, zufällig, nirgends sonst benutzt). Der Alias muss `jarvis`
   heißen.
2. **Sichern:** `jarvis-neu.p12` und das Passwort an zwei Orten ablegen.
3. **Für GitHub umwandeln:** `base64 -w0 jarvis-neu.p12 > jarvis-neu.b64`
4. **Drei Secrets anlegen** unter GitHub → Repository → Settings → Secrets and variables → Actions:

   | Secret | Inhalt |
   |---|---|
   | `JARVIS_NEU_KEYSTORE_BASE64` | Inhalt von `jarvis-neu.b64` |
   | `JARVIS_NEU_KEYSTORE_PASSWORT` | das neue Passwort |
   | `JARVIS_ALT_KEYSTORE_PASSWORT` | das alte Passwort – steht in der Git-Historie von `app/build.gradle` (Zeile `storePassword`, letzter Stand vor diesem Wechsel) |

5. `jarvis-neu.b64` löschen.
6. **Vor dem ersten Update auf dem Handy:** Einstellungen → Sicherung anlegen. Dann erst die Test-App
   aktualisieren und prüfen, danach die normale App.

## Was bewiesen ist und was nicht

Automatisch geprüft (Workflow „Signaturmigration – Rotationsprobe im Emulator“, nur Wegwerf-Schlüssel):

- Signieren mit Rotationsnachweis gelingt; `apksigner verify` besteht für API 30–32 und 33+.
- Android 14 (API 34): Update über eine Installation mit dem „alten“ Schlüssel gelingt, private Datei bleibt.
- Android 14: Ein späteres Update **nur mit dem alten Schlüssel** wird abgelehnt.
- Android 12L (API 32): Update gelingt, Datei bleibt (dort gilt weiter die alte Signatur).

Zusätzlich von Hand geprüft (15. Nov., lokal): Das Signierskript arbeitet mit dem **echten** alten Schlüssel aus dem
Repo und einem Probe-Schlüssel; die echte unsignierte Release-APK wird gültig signiert, ab Android 13 gilt der
Probe-Schlüssel, dem alten sind Rollback, gemeinsame UID, Berechtigungen und Auth entzogen. Gegenproben: Eine nur
mit dem alten Schlüssel signierte APK fällt in `apk-pruefen.sh` durch; der alte Keystore als „neuer“ wird abgelehnt.

Nicht bewiesen:

- [ ] Der Ablauf mit Arts **echtem neuem** Schlüssel in der CI (geht erst, wenn die Secrets da sind).
- [ ] Das Update auf dem **Galaxy S24 Ultra** über die tatsächlich installierte App, mit Arts echten Daten.
- [ ] Samsung-Besonderheiten bei der Rotation (der Emulator ist ein Pixel-Abbild).

## Werkzeuge

- `scripts/signierung/rotation-sign.sh` – signiert eine unsignierte APK mit altem und neuem Schlüssel (v3.1).
  Alles über Umgebungsvariablen, Passwörter nie auf der Befehlszeile.
- `scripts/signierung/apk-pruefen.sh <APK> <debug|unsigniert|release>` – prüft Paket-ID, Android-Sicherung und
  Signatur; schlägt beim kompromittierten Schlüssel fehl.
- `scripts/signierung/emulator-upgrade-check.sh` – die Probe im Emulator (alt → rotiert → Angriff).

Quellen: https://developer.android.com/tools/apksigner ·
https://source.android.com/docs/security/features/apksigning/v3-1
