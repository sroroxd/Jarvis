#!/usr/bin/env bash
# Signiert eine unsignierte Jarvis-APK mit dem NEUEN Schlüssel und einem Rotationsnachweis (APK Signature Scheme
# v3.1), damit sie sich als Update über eine Installation mit dem alten, kompromittierten Schlüssel legen lässt.
#
#  - Android 13+ (API 33+, also das Galaxy S24 Ultra): Es gilt der neue Schlüssel. Der alte darf danach kein
#    Update mehr liefern (kein Rollback) und verliert Signatur-Rechte (gemeinsame UID, Berechtigungen, Auth).
#  - Android 11–12L (API 30–32): Dort gilt weiter die alte Signatur – Rotation ist auf diesen Versionen laut
#    apksigner-Doku nicht verlässlich. Restrisiko, siehe docs/SIGNATUR_MIGRATION.md.
#
# Alle Angaben kommen aus Umgebungsvariablen; Passwörter nie auf der Befehlszeile, nie im Protokoll.
# Der neue private Schlüssel darf nicht im Repository liegen. Ursprung: PR #89.
set -Eeuo pipefail
umask 077

# Fingerabdruck (SHA-256) des Zertifikats von app/jarvis.keystore – öffentlich bekannt, kompromittiert.
KOMPROMITTIERT=548a724b0101b6666476d4ecc75abf3f101dbe10e8b3a06d0c2b378d94f2f9b6

fail() { printf 'FEHLER: %s\n' "$*" >&2; exit 1; }
need() { [[ -n "$(printenv "$1" 2>/dev/null)" ]] || fail "Umgebungsvariable $1 fehlt"; }
for key in JARVIS_OLD_KEYSTORE JARVIS_NEW_KEYSTORE JARVIS_OLD_ALIAS JARVIS_NEW_ALIAS \
           JARVIS_OLD_KS_PASS JARVIS_NEW_KS_PASS JARVIS_OLD_KEY_PASS JARVIS_NEW_KEY_PASS \
           JARVIS_UNSIGNED_APK JARVIS_OUTPUT_APK JARVIS_LINEAGE; do need "$key"; done
command -v apksigner >/dev/null || fail 'Android SDK Build Tools (apksigner) fehlen'
[[ -f "$JARVIS_OLD_KEYSTORE" ]] || fail 'Alter Keystore nicht vorhanden'
[[ -f "$JARVIS_NEW_KEYSTORE" ]] || fail 'Neuer Keystore nicht vorhanden'
[[ -f "$JARVIS_UNSIGNED_APK" ]] || fail 'Unsignierte APK nicht vorhanden'
[[ ! -e "$JARVIS_OUTPUT_APK" ]] || fail 'Ziel-APK existiert bereits (kein Überschreiben)'
[[ ! -e "$JARVIS_LINEAGE" ]] || fail 'Rotationsnachweis existiert bereits (kein Überschreiben)'

script_dir="$(cd -- "$(dirname -- "$0")" && pwd -P)"
repo_root="$(cd -- "$script_dir/../.." && pwd -P)"
new_key_path="$(realpath -m -- "$JARVIS_NEW_KEYSTORE")"
case "$new_key_path" in "$repo_root"/*) fail 'Neuer privater Schlüssel darf nicht im Repository liegen';; esac
[[ "$(realpath -m -- "$JARVIS_UNSIGNED_APK")" != "$(realpath -m -- "$JARVIS_OUTPUT_APK")" ]] || fail 'Eingabe und Ausgabe dürfen nicht identisch sein'

# Vorab: Der „neue“ Schlüssel darf nicht der kompromittierte sein (Passwort über die Umgebung, nicht als Argument).
if command -v keytool >/dev/null; then
  fp="$(keytool -list -keystore "$JARVIS_NEW_KEYSTORE" -alias "$JARVIS_NEW_ALIAS" -storepass:env JARVIS_NEW_KS_PASS 2>/dev/null \
        | grep -i 'SHA-256' | sed 's/.*: //' | tr -d ':' | tr 'A-F' 'a-f' || true)"
  [[ -n "$fp" ]] || fail 'Neuer Keystore lässt sich nicht lesen (Passwort oder Alias falsch?)'
  [[ "$fp" != "$KOMPROMITTIERT" ]] || fail 'Als neuer Schlüssel wurde der kompromittierte angegeben'
fi

work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT

# Rotationsnachweis alt → neu. Der alte Schlüssel behält nur „installed data“ (ohne das ginge kein Update über die
# bestehende Installation); Rollback, gemeinsame UID, Signatur-Berechtigungen und Auth werden ihm entzogen.
apksigner rotate --out "$work/lineage.bin" \
  --old-signer --ks "$JARVIS_OLD_KEYSTORE" --ks-key-alias "$JARVIS_OLD_ALIAS" \
  --ks-pass env:JARVIS_OLD_KS_PASS --key-pass env:JARVIS_OLD_KEY_PASS \
  --set-installed-data true --set-shared-uid false --set-permission false --set-rollback false --set-auth false \
  --new-signer --ks "$JARVIS_NEW_KEYSTORE" --ks-key-alias "$JARVIS_NEW_ALIAS" \
  --ks-pass env:JARVIS_NEW_KS_PASS --key-pass env:JARVIS_NEW_KEY_PASS

apksigner sign \
  --ks "$JARVIS_OLD_KEYSTORE" --ks-key-alias "$JARVIS_OLD_ALIAS" \
  --ks-pass env:JARVIS_OLD_KS_PASS --key-pass env:JARVIS_OLD_KEY_PASS \
  --next-signer --ks "$JARVIS_NEW_KEYSTORE" --ks-key-alias "$JARVIS_NEW_ALIAS" \
  --ks-pass env:JARVIS_NEW_KS_PASS --key-pass env:JARVIS_NEW_KEY_PASS \
  --lineage "$work/lineage.bin" --rotation-min-sdk-version 33 \
  --out "$work/rotated.apk" "$JARVIS_UNSIGNED_APK"

apksigner verify --min-sdk-version 30 --max-sdk-version 32 "$work/rotated.apk" >/dev/null
apksigner verify --min-sdk-version 33 --max-sdk-version 35 "$work/rotated.apk" >/dev/null

# Ab Android 13 muss ein anderer als der kompromittierte Schlüssel gelten – sonst wäre die Rotation wirkungslos
# (z. B. wenn versehentlich der alte Keystore als „neuer“ angegeben wurde).
neu="$(apksigner verify --min-sdk-version 33 --print-certs "$work/rotated.apk" \
        | grep -E 'minSdkVersion=33.*SHA-256 digest' | awk '{print $NF}')"
[[ -n "$neu" ]] || fail 'Kein Signierer für Android 13+ gefunden'
[[ "$neu" != "$KOMPROMITTIERT" ]] || fail 'Für Android 13+ gilt weiter der kompromittierte Schlüssel'

mkdir -p -- "$(dirname -- "$JARVIS_OUTPUT_APK")" "$(dirname -- "$JARVIS_LINEAGE")"
mv -- "$work/lineage.bin" "$JARVIS_LINEAGE"
mv -- "$work/rotated.apk" "$JARVIS_OUTPUT_APK"
printf 'Signiert mit Rotationsnachweis. Android 13+: Schlüssel %s\n' "$neu"
