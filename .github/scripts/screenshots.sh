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

# Zahnrad oben rechts antippen (feste Position auf dem Pixel-6-Profil, 1080 × 2400).
# Eine Oberflächenbeschreibung per uiautomator klappt nicht, weil der Reaktor ständig animiert.
adb shell input tap 989 228
sleep 4
adb exec-out screencap -p > shots/02_einstellungen_1.png
for i in 2 3 4 5 6 7; do
  adb shell input swipe 540 1900 540 750 600
  sleep 2
  adb exec-out screencap -p > shots/02_einstellungen_$i.png
done
# Zustände des Startbildschirms (nur Debug-Variante kennt das Extra „demo“)
for st in IDLE LISTENING THINKING SPEAKING; do
  adb shell am start -n de.art.jarvis/.MainActivity --es demo $st
  sleep 3
  adb exec-out screencap -p > shots/03_zustand_$st.png
done
echo "Absturzprotokoll (leer = kein Absturz):" > shots/crash.txt
adb logcat -d -s AndroidRuntime:E >> shots/crash.txt || true
ls -la shots
