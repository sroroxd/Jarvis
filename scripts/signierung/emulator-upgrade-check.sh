#!/usr/bin/env bash
# CI-Smoke-Test mit synthetischen Schluesseln, niemals direkt am Nutzergeraet ausfuehren.
set -Eeuo pipefail

: "$RUNNER_TEMP"
for f in alt-signiert.apk neu-signiert.apk; do
  [[ -s "$RUNNER_TEMP/$f" ]] || { echo "Fehlende APK: $f" >&2; exit 1; }
done
echo "Installiere alte, synthetisch signierte APK"
adb install -r "$RUNNER_TEMP/alt-signiert.apk"
echo "Erzeuge privaten Persistenzmarker unter der echten Paket-ID"
adb shell run-as de.art.jarvis mkdir -p files
adb shell run-as de.art.jarvis touch files/signing-migration-marker
echo "Installiere hoeher versionierte APK mit Signatur-Lineage"
adb install -r "$RUNNER_TEMP/neu-signiert.apk"
echo "Pruefe Datenerhalt nach Update"
adb shell run-as de.art.jarvis ls files/signing-migration-marker
echo "Pruefe aktualisierte Version"
adb shell dumpsys package de.art.jarvis | grep -F 'versionCode=81002'
echo "Upgrade mit synthetischen Schluesseln erfolgreich; keine Produktionsfreigabe."
