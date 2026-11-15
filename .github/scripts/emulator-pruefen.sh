#!/usr/bin/env bash
set -euo pipefail
if [ "$GITHUB_REF" = refs/heads/main ]; then pkg=de.art.jarvis; else pkg=de.art.jarvis.test; fi
if [ "$GITHUB_REF" = refs/heads/feature/openai-realtime-tft-vision ]; then pkg=de.art.jarvis.openaitest; fi
bash .github/scripts/sicherheitstest.sh "$pkg" "$(find apks -name app-debug.apk | head -1)" "$(find apks -name app-debug-androidTest.apk | head -1)"
bash .github/scripts/geraetetest.sh "$pkg" "$(find apks -name app-debug.apk | head -1)"
if [ "$GITHUB_REF" = refs/heads/feature/openai-realtime-tft-vision ]; then
  bash .github/scripts/release-pruefen.sh "$pkg" "$(find apks -name app-release.apk | head -1)"
fi
