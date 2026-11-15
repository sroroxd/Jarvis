#!/usr/bin/env bash
# Signaturmigration: signiert eine unsignierte APK mit alter/neuer Signatur (v3.1).
# KEINE privaten Schluessel oder Kennwoerter in Git/Actions-Artefakte schreiben.
set -Eeuo pipefail
umask 077

fail() { printf 'FEHLER: %s\n' "$*" >&2; exit 1; }
need() { [[ -n "$(printenv "$1" 2>/dev/null)" ]] || fail "Umgebungsvariable $1 fehlt"; }
for key in JARVIS_OLD_KEYSTORE JARVIS_NEW_KEYSTORE JARVIS_OLD_ALIAS JARVIS_NEW_ALIAS \
           JARVIS_OLD_KS_PASS JARVIS_NEW_KS_PASS JARVIS_OLD_KEY_PASS JARVIS_NEW_KEY_PASS \
           JARVIS_UNSIGNED_APK JARVIS_OUTPUT_APK JARVIS_LINEAGE; do need "$key"; done
command -v apksigner >/dev/null || fail 'Android SDK Build Tools (apksigner) fehlen'
[[ -f "$JARVIS_OLD_KEYSTORE" ]] || fail 'Alter Keystore nicht vorhanden'
[[ -f "$JARVIS_NEW_KEYSTORE" ]] || fail 'Neuer Keystore nicht vorhanden'
[[ -f "$JARVIS_UNSIGNED_APK" ]] || fail 'Unsigned APK nicht vorhanden'
[[ ! -e "$JARVIS_OUTPUT_APK" ]] || fail 'Ziel-APK existiert bereits (kein Ueberschreiben)'
[[ ! -e "$JARVIS_LINEAGE" ]] || fail 'Lineage existiert bereits (kein Ueberschreiben)'

script_dir="$(cd -- "$(dirname -- "$0")" && pwd -P)"
repo_root="$(cd -- "$script_dir/../.." && pwd -P)"
new_key_path="$(realpath -m -- "$JARVIS_NEW_KEYSTORE")"
lineage_path="$(realpath -m -- "$JARVIS_LINEAGE")"
case "$new_key_path" in "$repo_root"/*) fail 'Neuer privater Schluessel darf nicht im Repository liegen';; esac
case "$lineage_path" in "$repo_root"/*) fail 'Lineage nur ausserhalb des Repositories ablegen';; esac
[[ "$(realpath -m -- "$JARVIS_UNSIGNED_APK")" != "$(realpath -m -- "$JARVIS_OUTPUT_APK")" ]] || fail 'Eingabe und Ausgabe duerfen nicht identisch sein'

work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT

apksigner rotate --out "$work/lineage.bin" \
  --old-signer --ks "$JARVIS_OLD_KEYSTORE" --ks-key-alias "$JARVIS_OLD_ALIAS" \
  --ks-pass env:JARVIS_OLD_KS_PASS --key-pass env:JARVIS_OLD_KEY_PASS \
  --new-signer --ks "$JARVIS_NEW_KEYSTORE" --ks-key-alias "$JARVIS_NEW_ALIAS" \
  --ks-pass env:JARVIS_NEW_KS_PASS --key-pass env:JARVIS_NEW_KEY_PASS

# API 30–32: alter Schluessel; API 33+: neuer Schluessel.
apksigner sign \
  --ks "$JARVIS_OLD_KEYSTORE" --ks-key-alias "$JARVIS_OLD_ALIAS" \
  --ks-pass env:JARVIS_OLD_KS_PASS --key-pass env:JARVIS_OLD_KEY_PASS \
  --next-signer --ks "$JARVIS_NEW_KEYSTORE" --ks-key-alias "$JARVIS_NEW_ALIAS" \
  --ks-pass env:JARVIS_NEW_KS_PASS --key-pass env:JARVIS_NEW_KEY_PASS \
  --lineage "$work/lineage.bin" --rotation-min-sdk-version 33 \
  --out "$work/rotated.apk" "$JARVIS_UNSIGNED_APK"

apksigner verify --min-sdk-version 30 --max-sdk-version 32 --verbose "$work/rotated.apk"
apksigner verify --min-sdk-version 33 --max-sdk-version 35 --verbose "$work/rotated.apk"
mkdir -p -- "$(dirname -- "$JARVIS_OUTPUT_APK")" "$(dirname -- "$JARVIS_LINEAGE")"
mv -- "$work/lineage.bin" "$JARVIS_LINEAGE"
mv -- "$work/rotated.apk" "$JARVIS_OUTPUT_APK"
printf 'Signaturchecks bestanden. APK: %s\nLineage: %s\n' "$JARVIS_OUTPUT_APK" "$JARVIS_LINEAGE"
printf 'Noch KEIN Nachweis fuer ein erfolgreiches Update einer installierten APK. Nicht veroeffentlichen.\n'
