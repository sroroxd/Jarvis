#!/usr/bin/env bash
# Auf einem frischen CI-Emulator: echte Android-Keystore-Migration und schlüsselfreier Export.
set -euo pipefail
pkg="$1"
apk="$2"
test_apk="$3"
mkdir -p shots
adb install -r -g "$apk"
adb install -r "$test_apk"
adb shell am instrument -w -r "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee shots/sicherheitstest.txt
if ! grep -qE '^OK \([0-9]+ tests?\)' shots/sicherheitstest.txt; then
  echo 'Android-Sicherheitsprüfungen fehlgeschlagen.' >&2; exit 1
fi
if [ "$pkg" = de.art.jarvis.openaitest ]; then
  adb exec-out run-as "$pkg" cat files/openai-einstellungen.png > shots/openai-einstellungen.png
  adb exec-out run-as "$pkg" cat files/openai-hilfe.png > shots/openai-hilfe.png
fi
