#!/usr/bin/env bash
# Prüft eine gebaute Jarvis-APK, bevor sie weitergegeben wird, und schreibt einen kurzen Bericht auf die Ausgabe.
# Bricht mit Fehler ab, wenn die APK mit dem kompromittierten Schlüssel signiert ist oder Androids Sicherung
# eingeschaltet hat.
#
# Aufruf: apk-pruefen.sh <APK> <debug|unsigniert|release>
#   debug      – muss gültig signiert sein, nicht mit dem kompromittierten Schlüssel; Paket-ID endet auf „.debug“
#   unsigniert – darf KEINE Signatur tragen (Release aus Gradle, vor dem Signieren in der CI)
#   release    – muss für Android 13+ mit einem anderen als dem kompromittierten Schlüssel signiert sein
set -Eeuo pipefail

# Fingerabdruck (SHA-256) des Zertifikats von app/jarvis.keystore – öffentlich bekannt, kompromittiert.
KOMPROMITTIERT=548a724b0101b6666476d4ecc75abf3f101dbe10e8b3a06d0c2b378d94f2f9b6

apk="${1:?APK fehlt}"
modus="${2:?Modus fehlt}"
fail() { printf 'FEHLER (%s): %s\n' "$(basename "$apk")" "$*" >&2; exit 1; }
[[ -s "$apk" ]] || fail 'Datei fehlt oder ist leer'

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
tool() { command -v "$1" 2>/dev/null || find "$sdk/build-tools" -maxdepth 2 -name "$1" -type f 2>/dev/null | sort -V | tail -1; }
apksigner_bin="$(tool apksigner)"; aapt2_bin="$(tool aapt2)"
[[ -n "$apksigner_bin" && -n "$aapt2_bin" ]] || fail 'apksigner oder aapt2 nicht gefunden (Android SDK Build Tools)'

badging="$("$aapt2_bin" dump badging "$apk")"
paket="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")"
vcode="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")"
vname="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<< "$badging")"
manifest="$("$aapt2_bin" dump xmltree --file AndroidManifest.xml "$apk")"

printf '%s\n' "== $(basename "$apk") ($modus)"
printf '  Paket: %s · Version %s (%s) · %s Bytes\n' "$paket" "$vname" "$vcode" "$(stat -c %s "$apk")"
printf '  SHA-256 der Datei: %s\n' "$(sha256sum "$apk" | cut -d' ' -f1)"

# Androids Sicherung muss aus sein – in den Einstellungen liegen API-Schlüssel und Tokens.
grep -q 'allowBackup(0x01010280)=false' <<< "$manifest" || fail 'allowBackup ist nicht false'
grep -q 'dataExtractionRules' <<< "$manifest" || fail 'dataExtractionRules fehlt (Gerätewechsel würde Schlüssel kopieren)'
printf '  Android-Sicherung: aus (allowBackup=false, Regeln für Cloud und Gerätewechsel vorhanden)\n'

case "$modus" in
  unsigniert)
    if "$apksigner_bin" verify "$apk" >/dev/null 2>&1; then fail 'trägt eine Signatur, sollte unsigniert sein'; fi
    printf '  Signatur: keine (wird erst in der CI mit dem neuen Schlüssel signiert)\n'
    ;;
  debug)
    certs="$("$apksigner_bin" verify --print-certs "$apk")" || fail 'Signatur ungültig'
    digests="$(grep 'SHA-256 digest' <<< "$certs" | awk '{print $NF}')"
    [[ -n "$digests" ]] || fail 'kein Signierer gefunden'
    grep -qx "$KOMPROMITTIERT" <<< "$digests" && fail 'mit dem KOMPROMITTIERTEN Schlüssel signiert'
    [[ "$paket" == *.debug ]] || fail "Paket-ID $paket endet nicht auf .debug"
    printf '  Signatur: gültig, Debug-Schlüssel %s (nicht der kompromittierte)\n' "$(head -1 <<< "$digests")"
    ;;
  release)
    certs="$("$apksigner_bin" verify --min-sdk-version 33 --print-certs "$apk")" || fail 'Signatur für Android 13+ ungültig'
    neu="$(grep -E 'minSdkVersion=33.*SHA-256 digest|^Signer #1 certificate SHA-256 digest' <<< "$certs" | head -1 | awk '{print $NF}')"
    [[ -n "$neu" ]] || fail 'kein Signierer für Android 13+ gefunden'
    [[ "$neu" != "$KOMPROMITTIERT" ]] || fail 'für Android 13+ gilt der KOMPROMITTIERTE Schlüssel'
    [[ "$paket" != *.debug ]] || fail "Release trägt Debug-Paket-ID $paket"
    printf '  Signatur: gültig, ab Android 13 Schlüssel %s (nicht der kompromittierte)\n' "$neu"
    ;;
  *) fail "unbekannter Modus $modus" ;;
esac
