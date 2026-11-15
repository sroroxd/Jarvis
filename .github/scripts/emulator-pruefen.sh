#!/usr/bin/env bash
set -euo pipefail
if [ "$GITHUB_REF" = refs/heads/main ]; then pkg=de.art.jarvis; else pkg=de.art.jarvis.test; fi
if [ "$GITHUB_REF" = refs/heads/feature/openai-realtime-tft-vision ]; then
  pkg=de.art.jarvis.openaitest
  # Diese Variante hat keine Claude-Abhängigkeit und verwendet keine echten API-Schlüssel in der CI.
  unset JARVIS_TESTKEY
  export JARVIS_SKIP_POCKET=1
fi
bash .github/scripts/sicherheitstest.sh "$pkg" "$(find apks -name app-debug.apk | head -1)" "$(find apks -name app-debug-androidTest.apk | head -1)"
bash .github/scripts/geraetetest.sh "$pkg" "$(find apks -name app-debug.apk | head -1)"
if [ "$GITHUB_REF" = refs/heads/feature/openai-realtime-tft-vision ]; then
  bash .github/scripts/release-pruefen.sh "$pkg" "$(find apks -name app-release.apk | head -1)"
fi
