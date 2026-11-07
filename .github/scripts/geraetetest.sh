#!/usr/bin/env bash
# Gerätetest im Emulator: installiert die App, startet sie, drückt „Starten“, öffnet die Einstellungen,
# lässt alles eine Weile laufen und prüft dann, ob die App abgestürzt ist oder hängt.
# Schlägt der Test fehl, veröffentlicht die CI KEINE APK – Art bekommt nur, was hier durchgekommen ist.
#
# Aufruf: geraetetest.sh <Paket> <APK>
set -u
PKG="$1"
APK="$2"
ACT="$PKG/de.art.jarvis.MainActivity"
mkdir -p shots
report=shots/geraetetest.txt
: > "$report"
say() { echo "$*" | tee -a "$report"; }

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# -g: alle Laufzeit-Berechtigungen (Mikrofon, Benachrichtigungen) gleich mit erteilen
if ! adb install -r -g "$APK" >> "$report" 2>&1; then
  say "FEHLER: Installation fehlgeschlagen"
  exit 1
fi
adb logcat -c
adb logcat -b crash -c || true

adb shell am start -W -n "$ACT" >> "$report" 2>&1
sleep 6
adb exec-out screencap -p > shots/01_start.png

# „Starten“ in der Mitte des Reaktors (Pixel-6-Profil, 1080 × 2400). Ohne API-Schlüssel meldet die App
# das nur – entscheidend ist, dass sie dabei nicht abstürzt.
adb shell input tap 540 1140
sleep 8
adb exec-out screencap -p > shots/01b_nach_starten.png

# Zahnrad oben rechts → Einstellungen, einmal ganz durchscrollen
adb shell input tap 989 228
sleep 4
adb exec-out screencap -p > shots/02_einstellungen_1.png
for i in 2 3 4 5 6 7; do
  adb shell input swipe 540 1900 540 750 600
  sleep 2
  adb exec-out screencap -p > shots/02_einstellungen_$i.png
done
adb shell input keyevent KEYCODE_BACK
sleep 2

# Zustände des Startbildschirms (nur die Debug-Variante kennt das Extra „demo“)
for st in IDLE LISTENING THINKING SPEAKING; do
  adb shell am start -n "$ACT" --es demo "$st" > /dev/null 2>&1
  sleep 3
  adb exec-out screencap -p > shots/03_zustand_$st.png
done

# Noch eine halbe Minute laufen lassen: Dienst, Wachwort-Engine (ONNX) und Zeitgeber arbeiten weiter.
adb shell am start -n "$ACT" > /dev/null 2>&1
sleep 30
adb exec-out screencap -p > shots/04_nach_30s.png

# ---------------------------------------------------------------- Auswertung
fail=0
crashes=$(adb logcat -d -b crash 2>/dev/null | grep -A25 "Process: $PKG" || true)
if [ -n "$crashes" ]; then
  say "FEHLER: Absturz"
  echo "$crashes" | head -60 | tee -a "$report"
  fail=1
fi
anr=$(adb logcat -d 2>/dev/null | grep -E "ANR in $PKG" || true)
if [ -n "$anr" ]; then
  say "FEHLER: App reagiert nicht (ANR)"
  echo "$anr" | head -10 | tee -a "$report"
  fail=1
fi
if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
  say "FEHLER: App-Prozess läuft nach dem Test nicht mehr"
  fail=1
fi
if adb shell dumpsys activity services "$PKG" | grep -q "JarvisService"; then
  say "Info: Hintergrunddienst läuft"
else
  say "Info: Hintergrunddienst läuft nicht (ohne API-Schlüssel erwartet)"
fi
# Nie leer lassen: GitHub lehnt leere Dateien beim Hochladen ab.
{ echo "Fehlerprotokoll (AndroidRuntime:E, leer = keine Fehler):"; adb logcat -d -s AndroidRuntime:E 2>/dev/null; } > shots/logcat_fehler.txt || true

if [ "$fail" -eq 0 ]; then
  say "BESTANDEN: installiert, gestartet, Einstellungen geöffnet, 30 s gelaufen – kein Absturz, kein Hänger."
fi
exit "$fail"
