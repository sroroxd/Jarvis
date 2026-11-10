#!/data/data/com.termux/files/usr/bin/bash
# Ergänzt den privaten lokalen OpenAI-Dienst; erstellt keinen Provider-Schlüssel.
set -euo pipefail
REF="${JARVIS_REF:-main}"
BASE="https://raw.githubusercontent.com/sroroxd/Jarvis/$REF/agent"
command -v proot-distro >/dev/null || { echo 'Vorhandenen Ubuntu-Container zuerst einrichten.'; exit 1; }
# Nur bereits vorhandenen Container verwenden, keine neue Distribution auf Verdacht installieren.
proot-distro login ubuntu -- /bin/sh -c 'command -v node >/dev/null' || {
  echo 'Node im vorhandenen Ubuntu-Container fehlt.'; exit 1;
}
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
curl -fsSL "$BASE/openai-bridge.mjs" -o "$stage/openai-bridge.mjs"
proot-distro login --bind "$stage:/mnt/jarvis-openai-install" ubuntu -- /bin/bash -c '
  set -e
  mkdir -p /root/jarvis-agent /root/.jarvis-openai
  chmod 700 /root/.jarvis-openai
  node --check /mnt/jarvis-openai-install/openai-bridge.mjs
  cp /mnt/jarvis-openai-install/openai-bridge.mjs /root/jarvis-agent/openai-bridge.mjs
'
mkdir -p "$HOME/bin"
cat > "$HOME/bin/jarvis-openai" <<'START'
#!/data/data/com.termux/files/usr/bin/bash
set -eu
# Vordergrund in Termux; kein versteckter Hilfsdienst und keine automatische Akku-Ausnahme.
exec proot-distro login ubuntu -- node /root/jarvis-agent/openai-bridge.mjs
START
chmod 700 "$HOME/bin/jarvis-openai"
echo 'Programm eingerichtet. Start: ~/bin/jarvis-openai'
echo 'Schlüssel bleibt privat im Container: /root/.jarvis-openai/api-key (Dateirechte 600).'
echo 'Kein Schlüssel erstellt, ausgegeben oder übertragen. Die KI benötigt Internet.'
