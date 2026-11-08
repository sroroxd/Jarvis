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

# Der langsame CI-Emulator zeigt manchmal „Pixel Launcher isn't responding“ – ein Dialog einer FREMDEN App, der
# dann alle Fingertipps abfängt (so am 8. Nov. in Build 74: Die Bilder „Einstellungen“ zeigten nur den Dialog).
# Auf „Warten“ tippen. Hängt Jarvis selbst, fällt das weiter unten über „ANR in <Paket>“ auf.
dialoge_weg() {
  local xml b x1 y1 x2 y2
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 0
    xml=$(adb shell cat /sdcard/ui.xml 2>/dev/null)
    echo "$xml" | grep -qE "isn[^ ]{1,6}t responding|reagiert nicht" || return 0
    b=$(echo "$xml" | grep -oE 'text="(Wait|Warten)"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
          | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && return 0
    read -r x1 y1 x2 y2 <<< "$(echo "$b" | tr -c '0-9' ' ')"
    adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
    say "Hinweis: Systemdialog „App reagiert nicht“ einer fremden App weggetippt"
    sleep 2
  done
}
vorne() { adb shell dumpsys activity activities 2>/dev/null | grep -E "topResumedActivity|mResumedActivity" | head -1; }

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
dialoge_weg
adb exec-out screencap -p > shots/01_start.png

# „Starten“ in der Mitte des Reaktors (Pixel-6-Profil, 1080 × 2400). Ohne API-Schlüssel meldet die App
# das nur – entscheidend ist, dass sie dabei nicht abstürzt.
adb shell input tap 540 1140
sleep 8
adb exec-out screencap -p > shots/01b_nach_starten.png

# Zahnrad oben rechts → Einstellungen, einmal ganz durchscrollen. Nachsehen, ob sie wirklich vorne sind.
dialoge_weg
adb shell input tap 989 228
sleep 4
if ! vorne | grep -q SettingsActivity; then
  dialoge_weg
  adb shell input tap 989 228
  sleep 4
fi
if vorne | grep -q SettingsActivity; then
  einstellungen="Einstellungen geöffnet"
  adb exec-out screencap -p > shots/02_einstellungen_1.png
  for i in 2 3 4 5 6 7 8; do
    adb shell input swipe 540 1900 540 750 600
    sleep 2
    adb exec-out screencap -p > shots/02_einstellungen_$i.png
  done
  adb shell input keyevent KEYCODE_BACK
  sleep 2
else
  einstellungen="Einstellungen NICHT geprüft"
  say "WARNUNG: Einstellungen ließen sich nicht öffnen – vorne: $(vorne | tr -s ' ')"
  adb exec-out screencap -p > shots/02_einstellungen_fehlt.png
fi

# Zustände des Startbildschirms (nur die Debug-Variante kennt das Extra „demo“)
for st in IDLE LISTENING THINKING SPEAKING; do
  adb shell am start -n "$ACT" --es demo "$st" > /dev/null 2>&1
  sleep 3
  adb exec-out screencap -p > shots/03_zustand_$st.png
done

# Noch eine halbe Minute laufen lassen: Dienst, Wachwort-Engine (ONNX) und Zeitgeber arbeiten weiter.
adb shell am start -n "$ACT" > /dev/null 2>&1
sleep 30
dialoge_weg
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

# ---------------------------------------------------------------- Selbst-Update: Download-Weg
# Die App lädt neue Builds zuerst über die GitHub-API (der normale Link antwortete am 8. Nov. meist mit 504).
# Hier ohne Anmeldung geprüft, wie auf dem Handy – mit der APK, die gerade als Test-App veröffentlicht ist.
if [ "${GITHUB_REF:-}" != "refs/heads/main" ] && [ -n "${GITHUB_REF_NAME:-}" ]; then
  rel="https://api.github.com/repos/${GITHUB_REPOSITORY:-sroroxd/Jarvis}/releases/tags/test-${GITHUB_REF_NAME}"
  asset=$(curl -sS --max-time 20 -A Jarvis-App "$rel" 2>/dev/null | python3 -c '
import json, sys
try:
    a = [x for x in json.load(sys.stdin).get("assets", []) if x.get("name", "").endswith(".apk")]
    print(a[0]["url"] if a else "")
except Exception:
    print("")' || true)
  if [ -n "$asset" ]; then
    code=$(curl -sS -L --max-time 120 -A Jarvis-App -H "Accept: application/octet-stream" \
             -o /tmp/update_test.apk -w "%{http_code}" "$asset" 2>/dev/null || echo 000)
    size=$(stat -c %s /tmp/update_test.apk 2>/dev/null || echo 0)
    if [ "$code" = "200" ] && [ "$size" -gt 1000000 ]; then
      say "Update-Weg (GitHub-API, ohne Anmeldung): HTTP 200, $((size / 1048576)) MB"
    else
      say "WARNUNG Update-Weg (GitHub-API, ohne Anmeldung): HTTP $code, $size Bytes – die App nimmt dann den normalen Link"
    fi
    rm -f /tmp/update_test.apk
  else
    say "Hinweis: Update-Weg nicht geprüft (noch keine Test-App für diesen Zweig veröffentlicht)"
  fi
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
  say "BESTANDEN: installiert, gestartet, $einstellungen, 30 s gelaufen – kein Absturz, kein Hänger."
  [ -n "${JARVIS_TESTKEY:-}" ] && say "BESTANDEN: Gespräch mit Claude (Rechnen, Verlauf, Streaming)."
fi
exit "$fail"
