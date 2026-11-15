#!/usr/bin/env bash
# Nur auf dem wegwerfbaren CI-Emulator; keine Befehle für ein Nutzergerät.
set -euo pipefail
pkg="$1"
apk="$2"
test "$pkg" = de.art.jarvis.openaitest
mkdir -p shots
# Der Debug-Test hatte ausschließlich künstliche Zugangsdaten. Die Release-APK hat eine andere Signatur.
adb uninstall "$pkg" >/dev/null
adb install -g "$apk" | tee shots/release_installation.txt
adb logcat -b crash -c
adb shell am start -W -n "$pkg/de.art.jarvis.MainActivity" | tee -a shots/release_installation.txt
sleep 5
adb shell pidof "$pkg" >/dev/null
if adb logcat -b crash -d | grep -q "$pkg"; then
  echo 'Absturz der signierten OpenAI-Test-APK.' >&2; exit 1
fi
adb exec-out screencap -p > shots/05_signierte_openai_test_app.png
echo 'Signierte OpenAI-Test-APK installiert und gestartet.' | tee -a shots/release_installation.txt
