#!/usr/bin/env bash
# Macht Bildschirmfotos von Startbildschirm und Einstellungen (läuft im Emulator).
set -x
mkdir -p shots
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant de.art.jarvis android.permission.RECORD_AUDIO || true
adb shell pm grant de.art.jarvis android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n de.art.jarvis/.MainActivity
sleep 6
adb exec-out screencap -p > shots/01_start.png

# Zahnrad antippen (Position aus der Oberflächenbeschreibung lesen)
tap_desc() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml ui.xml >/dev/null
  python3 - "$1" <<'PY'
import re, sys, subprocess
xml = open("ui.xml", encoding="utf-8").read()
want = sys.argv[1]
for node in re.findall(r"<node [^>]*>", xml):
    if f'content-desc="{want}"' in node or f'text="{want}"' in node:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        break
PY
}

tap_desc "Einstellungen"
sleep 4
adb exec-out screencap -p > shots/02_einstellungen_1.png
for i in 2 3 4 5 6 7; do
  adb shell input swipe 540 1900 540 750 600
  sleep 2
  adb exec-out screencap -p > shots/02_einstellungen_$i.png
done
adb logcat -d -s AndroidRuntime:E > shots/crash.txt || true
ls -la shots
