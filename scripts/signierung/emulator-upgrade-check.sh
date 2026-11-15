#!/usr/bin/env bash
# Rotationsprobe im CI-Emulator, nur mit Wegwerf-Schlüsseln – nie auf einem echten Handy ausführen.
#  1. APK mit dem „alten“ Schlüssel installieren, eine private Datei anlegen.
#  2. Höhere Version mit Rotationsnachweis (alt → neu) darüber installieren: muss gelingen, Datei muss bleiben.
#  3. Angriff: noch höhere Version, NUR mit dem alten Schlüssel signiert. Ab Android 13 muss Android sie ablehnen
#     (ERWARTE_ALTSCHLUESSEL=abgelehnt); auf Android 11–12L gilt die alte Signatur weiter – dort wird das Ergebnis
#     nur berichtet (bekanntes Restrisiko).
set -Eeuo pipefail

: "$RUNNER_TEMP"
PKG="${PKG:-de.art.jarvis}"
for f in alt-signiert.apk neu-signiert.apk angriff-alt-signiert.apk; do
  [[ -s "$RUNNER_TEMP/$f" ]] || { echo "Fehlende APK: $f" >&2; exit 1; }
done
version() { adb shell dumpsys package "$PKG" | grep -oE 'versionCode=[0-9]+' | head -1; }

adb uninstall "$PKG" >/dev/null 2>&1 || true
echo "1. Installiere APK mit altem (Wegwerf-)Schlüssel"
adb install -r "$RUNNER_TEMP/alt-signiert.apk"
adb shell run-as "$PKG" mkdir -p files
adb shell run-as "$PKG" touch files/signing-migration-marker
echo "   installiert: $(version)"

echo "2. Update mit Rotationsnachweis (alt → neu)"
adb install -r "$RUNNER_TEMP/neu-signiert.apk"
adb shell run-as "$PKG" ls files/signing-migration-marker
[[ "$(version)" == "versionCode=81002" ]] || { echo "FEHLER: Version nach Update ist $(version)" >&2; exit 1; }
echo "   Update gelungen, private Datei erhalten, $(version)"

echo "3. Angriff: Update nur mit dem alten Schlüssel"
if out="$(adb install -r "$RUNNER_TEMP/angriff-alt-signiert.apk" 2>&1)"; then angriff=angenommen; else angriff=abgelehnt; fi
echo "   Android: $angriff – $(tr '\n' ' ' <<< "$out" | cut -c1-200)"
echo "   installiert: $(version)"
if [[ "${ERWARTE_ALTSCHLUESSEL:-}" == abgelehnt ]]; then
  [[ "$angriff" == abgelehnt && "$(version)" == "versionCode=81002" ]] \
    || { echo "FEHLER: Update mit dem alten Schlüssel wurde NICHT abgelehnt" >&2; exit 1; }
  echo "   BESTANDEN: Der alte Schlüssel kann nach der Rotation kein Update mehr unterschieben."
else
  echo "   Hinweis (Android 11–12L): Ergebnis nur berichtet – dort gilt die alte Signatur weiter."
fi
adb uninstall "$PKG" >/dev/null 2>&1 || true
echo "Rotationsprobe mit Wegwerf-Schlüsseln abgeschlossen; keine Produktionsfreigabe."
