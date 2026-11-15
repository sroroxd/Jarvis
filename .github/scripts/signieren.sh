#!/usr/bin/env bash
# Nie auf den öffentlichen Altschlüssel oder einen Debugschlüssel zurückfallen.
set -euo pipefail
if [ "${GITHUB_REF:-}" = 'refs/heads/feature/openai-realtime-tft-vision' ]; then
  # Eigene Paket-ID, eigener temporärer Schlüssel: keinerlei Rotation oder Änderung der Produktivsignatur.
  umask 077
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT
  export JARVIS_TEST_SIGN_PASS
  JARVIS_TEST_SIGN_PASS=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')
  keytool -genkeypair -noprompt -keystore "$tmp/test.p12" -storetype PKCS12 \
    -storepass:env JARVIS_TEST_SIGN_PASS -keypass:env JARVIS_TEST_SIGN_PASS \
    -alias jarvis-openai-test -keyalg RSA -keysize 3072 -validity 365 \
    -dname 'CN=Jarvis OpenAI Test' >/dev/null 2>&1
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK fehlt}}"
  apksigner=$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name apksigner | sort -V | tail -1)
  "$apksigner" sign --ks "$tmp/test.p12" --ks-key-alias jarvis-openai-test \
    --ks-pass env:JARVIS_TEST_SIGN_PASS --key-pass env:JARVIS_TEST_SIGN_PASS \
    --out app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
  "$apksigner" verify --verbose --min-sdk-version 30 app/build/outputs/apk/release/app-release.apk
  echo 'signed=true' >> "$GITHUB_OUTPUT"
  exit 0
fi
if [ -z "${JARVIS_SIGNING_KEYSTORE_B64:-}" ]; then
  if [ -n "${JARVIS_SIGNING_STORE_PASSWORD:-}${JARVIS_SIGNING_KEY_PASSWORD:-}${JARVIS_SIGNING_LINEAGE_B64:-}" ]; then
    echo "Unvollständige Signierungskonfiguration." >&2; exit 1
  fi
  echo "::warning::Release-Veröffentlichung gesperrt: geschützter Signierschlüssel fehlt."
  echo 'signed=false' >> "$GITHUB_OUTPUT"
  exit 0
fi
: "${JARVIS_SIGNING_STORE_PASSWORD:?Signierpasswort fehlt}"
: "${JARVIS_SIGNING_KEY_PASSWORD:?Schlüsselpasswort fehlt}"
: "${JARVIS_SIGNING_LINEAGE_B64:?Signaturhistorie für das Update bestehender Installationen fehlt}"
umask 077
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '%s' "$JARVIS_SIGNING_KEYSTORE_B64" | base64 --decode > "$tmp/release.p12"
printf '%s' "$JARVIS_SIGNING_LINEAGE_B64" | base64 --decode > "$tmp/rotation.lineage"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK fehlt}}"
apksigner=$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name apksigner | sort -V | tail -1)
unsigned=app/build/outputs/apk/release/app-release-unsigned.apk
signed=app/build/outputs/apk/release/app-release.apk
"$apksigner" sign --ks "$tmp/release.p12" --ks-key-alias jarvis-v2 \
  --ks-pass env:JARVIS_SIGNING_STORE_PASSWORD --key-pass env:JARVIS_SIGNING_KEY_PASSWORD \
  --lineage "$tmp/rotation.lineage" --rotation-min-sdk-version 28 \
  --min-sdk-version 30 --v1-signing-enabled false --v2-signing-enabled false --v4-signing-enabled false \
  --out "$signed" "$unsigned"
"$apksigner" verify --verbose --min-sdk-version 30 "$signed"
echo 'signed=true' >> "$GITHUB_OUTPUT"
