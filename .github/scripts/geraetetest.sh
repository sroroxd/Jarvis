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

# ---------------------------------------------------------------- Gesprächstest mit Claude
# Nur wenn der API-Schlüssel als GitHub-Secret ANTHROPIC_API_KEY hinterlegt ist. Die Fragen laufen
# genau den Weg einer gesprochenen Frage (ohne Mikrofon) bis zu Claude und zurück.
gfail=0
answers() { adb logcat -d -s JarvisTest:I 2>/dev/null | grep -E "antwort \[|fehler:" || true; }
ask() {  # ask <Name> <Frage> <Muster für die richtige Antwort> [Muster, das nicht vorkommen darf]
  local before line
  before=$(answers | wc -l)
  adb shell "am start -n $ACT --es testfrage '$2'" > /dev/null 2>&1
  for _ in $(seq 1 45); do
    sleep 2
    [ "$(answers | wc -l)" -gt "$before" ] && break
  done
  line=$(answers | tail -1)
  if [ "$(answers | wc -l)" -le "$before" ]; then
    say "  FEHLER $1: keine Antwort nach 90 s"; gfail=1; return
  fi
  say "  $1: ${line#*JarvisTest: }"
  if echo "$line" | grep -q "fehler:"; then gfail=1; return; fi
  if ! echo "$line" | grep -qiE "$3"; then say "  FEHLER $1: Antwort passt nicht zu /$3/"; gfail=1; fi
  # Falsche Zwischenergebnisse („Zweihundertzwei. Moment, Korrektur …“) sind beim Vorlesen genauso schlimm.
  if [ -n "${4:-}" ] && echo "$line" | grep -qiE "$4"; then say "  FEHLER $1: Antwort enthält /$4/"; gfail=1; fi
  LAST_LINE="$line"
}
if [ -n "${JARVIS_TESTKEY:-}" ]; then
  say "Gesprächstest mit Claude:"
  adb shell "am start -n $ACT --es testkey '$JARVIS_TESTKEY' --ez teststart true" > /dev/null 2>&1
  sleep 10
  ask "Rechnen" "Wie viel ist siebzehn mal drei? Antworte nur mit der Zahl." "(^|[^a-z])(51|einundf.nfzig)" "korrektur|moment"
  ask "Verlauf" "Und das Doppelte davon? Nur die Zahl." "(^|[^a-z])(102|einhundertzwei|hundertzwei)" "zweihundert|korrektur|moment"
  # Streaming: Eine Antwort aus zwei Sätzen muss Satz für Satz an die Sprachausgabe gehen („satz:“ im Protokoll),
  # nicht erst am Ende am Stück. Prüft das echte Datenformat der Claude-API, nicht nur nachgebaute Testdaten.
  saetze_vorher=$(adb logcat -d -s JarvisTest:I 2>/dev/null | grep -c "satz:" || true)
  ask "Streaming" "Erklaere in genau zwei kurzen Saetzen, was ein Toaster ist." "\\." "korrektur|moment"
  saetze=$(( $(adb logcat -d -s JarvisTest:I 2>/dev/null | grep -c "satz:" || true) - saetze_vorher ))
  if [ "$saetze" -ge 2 ]; then
    say "  Streaming: $saetze Saetze einzeln an die Sprachausgabe"
  else
    say "  FEHLER Streaming: nur $saetze Satz/Saetze einzeln gesprochen (erwartet mindestens 2)"; gfail=1
  fi
  # Prompt-Caching: Ab der zweiten Frage muss die Grundanweisung aus dem Zwischenspeicher kommen.
  if echo "${LAST_LINE:-}" | grep -q "gelesen"; then
    say "  Cache: greift"
  else
    say "  Hinweis: Cache griff bei der zweiten Frage nicht"
  fi
  adb exec-out screencap -p > shots/05_gespraech.png
  # Proaktives Coaching: Der Phasen-Wächter (Haiku) bekommt einen gemalten Einstellungs-Bildschirm – richtig ist
  # „ANDERES“. Prüft Modellname und Anfrageformat; ob er echte TFT-Bilder richtig einordnet, zeigt nur das Handy.
  phases() { adb logcat -d -s JarvisTest:I 2>/dev/null | grep -E "phase( fehler)?:" || true; }
  pvor=$(phases | wc -l)
  adb shell "am start -n $ACT --ez testwaechter true" > /dev/null 2>&1
  for _ in $(seq 1 30); do
    sleep 2
    [ "$(phases | wc -l)" -gt "$pvor" ] && break
  done
  pline=$(phases | tail -1)
  if [ "$(phases | wc -l)" -le "$pvor" ]; then
    say "  FEHLER Phasen-Wächter: keine Antwort nach 60 s"; gfail=1
  elif echo "$pline" | grep -q "phase: ANDERES"; then
    say "  Phasen-Wächter: ${pline#*JarvisTest: }"
  else
    say "  FEHLER Phasen-Wächter (erwartet ANDERES): ${pline#*JarvisTest: }"; gfail=1
  fi
else
  say "Gesprächstest übersprungen: kein API-Schlüssel (GitHub-Secret ANTHROPIC_API_KEY fehlt)"
fi

# ---------------------------------------------------------------- Auswertung
fail=$gfail
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
  [ -n "${JARVIS_TESTKEY:-}" ] && say "BESTANDEN: Gespräch mit Claude (Rechnen, Verlauf, Streaming)."
fi
exit "$fail"
