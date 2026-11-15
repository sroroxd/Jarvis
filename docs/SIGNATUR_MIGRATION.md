# Signaturschluessel-Migration – kontrollierter Entwurf (Issue #85)

**NICHT produktiv ausrollen.** Der bisherige Signaturschluessel wurde samt Passwort oeffentlich versioniert. Der Nachweis muss erst erbringen, dass die bisher installierte APK updatekompatibel bleibt. Dieser Entwurf veraendert die regulaere App und den Hauptbranch nicht.

## Vorgegebene Randbedingungen

- Regulaere Paket-ID: de.art.jarvis; Test-App: de.art.jarvis.test. Die Test-App beweist **keine** Updatekompatibilitaet der regulaeren Paket-ID.
- minSdk 30; API 30–32 verwenden weiterhin die alte Signatur, API 33+ kann den neuen v3.1-Signierer verwenden. Fuer API 30–32 bleibt der alte, kompromittierte Schluessel ein Sicherheitsrisiko.
- Kein neuer privater Signaturschluessel in Git, GitHub-Artefakten, App-Backups oder oeffentlichen Releases.
- Fuer das Upgrade ist ein hoeherer versionCode erforderlich. Alte und neue APK muessen dieselbe Paket-ID besitzen. Die wirklich installierte Ausgangs-APK muss vorab identifiziert werden.

## Stand der Vorbereitung

- Der separate Zweig kann mit dem Gradle-Parameter -PmigrationUnsigned eine **unsignierte** Release-APK erzeugen. Normale Builds behalten ihr bisheriges Signing.
- Das isolierte Skript scripts/signierung/rotation-sign.sh kann eine Proof-of-Rotation-Lineage erzeugen, eine APK mit alter/neuer Signatur nach v3.1 signieren und beide API-Bereiche separat mit apksigner verify pruefen.
- Dieser Skriptlauf **ist noch kein** bestandener Emulator-Upgrade-Test und **kein** Nachweis ueber den Datenerhalt der installierten App.
- Der Skriptaufruf verweigert u. a. das Ueberschreiben existierender Ergebnisdateien und einen neuen privaten Keystore innerhalb des Repository-Baums.

## Sichere lokale Vorbereitung

1. In einer isolierten Linux-/Bash-Umgebung Android SDK Build Tools (apksigner), Java keytool, Gradle und optional adb bereitstellen.
2. Die Signatur-Fingerprints der **tatsaechlich installierten**, bisherigen Produktions-APK ermitteln; keinen Schluessel austauschen oder die App deinstallieren.
3. Einen frischen privaten PKCS12-Keystore **ausserhalb** von Git erstellen, sicher und redundant archivieren. Testschluessel duerfen nur fuer synthetische Probe-APKs verwendet werden; spaeter muss ein separater echter Produktionsschluessel verwendet werden.
4. Eine unsignierte APK im Zweig bauen: ./gradlew :app:assembleRelease -PmigrationUnsigned. Die Datei sollte app/build/outputs/apk/release/app-release-unsigned.apk heissen.
5. Die folgenden elf Umgebungsvariablen fuer das Signierskript bereitstellen: JARVIS_OLD_KEYSTORE, JARVIS_NEW_KEYSTORE, JARVIS_OLD_ALIAS, JARVIS_NEW_ALIAS, JARVIS_OLD_KS_PASS, JARVIS_NEW_KS_PASS, JARVIS_OLD_KEY_PASS, JARVIS_NEW_KEY_PASS, JARVIS_UNSIGNED_APK, JARVIS_OUTPUT_APK, JARVIS_LINEAGE.
6. Die Pfade JARVIS_NEW_KEYSTORE und JARVIS_LINEAGE muessen **ausserhalb** des Repositories liegen. Bestehende Ausgabe-APK bzw. Lineage werden nicht ueberschrieben. Passwoerter weder in der Befehlszeile noch in Build-Logs ausgeben.
7. Das Skript mit bash scripts/signierung/rotation-sign.sh ausfuehren. Es erzeugt eine Lineage, signiert den APK-Inhalt fuer API 30–32 mit dem alten und API 33+ mit dem neuen Schluessel, validiert die Signatur fuer beide API-Bereiche.
8. Danach erst in einer **isolierten Testumgebung mit Paket-ID de.art.jarvis** eine alte, original signierte APK installieren, eine Testeinstellung anlegen, die hoeher versionierte Rotations-APK als Update installieren und Persistenz/Einstellungen sowie Sprach-/Bildschirmfunktionen pruefen.
9. Zum Schluss denselben Upgradepfad am Galaxy S24 Ultra testen, inklusive bestaetigter Sicherung. **Kein Release, keine Installation auf dem Haupttelefon und kein Merge vor erfolgreicher Abnahme.**

## Abnahmeliste

- [ ] Original-APK und tatsaechliche Signatur des installierten Builds verifiziert
- [ ] Neuer Produktionsschluessel ausserhalb Git erzeugt und sicher gesichert
- [ ] Signierprozess mit Wegwerf-Schluesseln vollstaendig ausgefuehrt
- [ ] Signaturen API 30–32 sowie API 33+ erfolgreich verifiziert
- [ ] Upgrade mit identischer Paket-ID und hoeherer versionCode im Emulator bestanden
- [ ] Persistenz von Gedächtnis und Einstellungen vorher/nachher geprueft
- [ ] Samsung S24 Ultra: Upgrade und Sicherungsrueckweg getestet
- [ ] Sichere CI-Signierung und explizite Freigabe fuer Produktiv-Release

Offizielle Dokumentation:
https://developer.android.com/tools/apksigner
https://source.android.com/docs/security/features/apksigning/v3-1
