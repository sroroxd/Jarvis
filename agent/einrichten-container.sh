#!/bin/bash
#
# Jarvis-Agent einrichten – Teil 2, läuft im Ubuntu-Container (proot-distro).
# Wird von einrichten.sh aufgerufen, kann aber auch einzeln laufen:
#   proot-distro login ubuntu -- /bin/bash /root/jarvis-agent/einrichten-container.sh
#
# Wiederholbar: Vorhandenes wird übersprungen.

set -euo pipefail

ORDNER=/root/jarvis-agent
BASIS=/root/.jarvis-agent
ARBEIT=/root/jarvis-arbeit

sag() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\n\033[1;33m!! %s\033[0m\n' "$*"; }

export DEBIAN_FRONTEND=noninteractive
export PATH="/root/.local/bin:$PATH"

sag "Grundpakete"
apt-get update -qq
apt-get install -y -qq curl ca-certificates git ripgrep unzip >/dev/null

sag "Node prüfen"
# Der Agent-Dienst braucht Node 18 oder neuer. Ubuntus eigenes Paket ist
# teils älter, deshalb notfalls über NodeSource nachziehen.
NODE_OK=0
if command -v node >/dev/null 2>&1; then
  MAJOR=$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)
  [ "$MAJOR" -ge 18 ] 2>/dev/null && NODE_OK=1
fi
if [ "$NODE_OK" -eq 0 ]; then
  apt-get install -y -qq nodejs >/dev/null 2>&1 || true
  if command -v node >/dev/null 2>&1; then
    MAJOR=$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)
    [ "$MAJOR" -ge 18 ] 2>/dev/null && NODE_OK=1
  fi
fi
if [ "$NODE_OK" -eq 0 ]; then
  echo "Node ist zu alt oder fehlt – hole Node 22 von NodeSource."
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash - >/dev/null
  apt-get install -y -qq nodejs >/dev/null
fi
echo "Node: $(node --version)"

sag "Claude Code prüfen"
if command -v claude >/dev/null 2>&1; then
  echo "Claude Code ist schon da: $(claude --version 2>&1 | head -1)"
else
  # Offizieller Installer. Auf reinem Termux bricht er ab (kein Android-Build),
  # im Ubuntu-Container läuft er.
  curl -fsSL https://claude.ai/install.sh | bash
  if ! command -v claude >/dev/null 2>&1; then
    warn "Der Installer hat claude nicht in den PATH gelegt – versuche npm."
    npm install -g @anthropic-ai/claude-code >/dev/null 2>&1 || true
  fi
fi
command -v claude >/dev/null 2>&1 || { warn "Claude Code fehlt weiterhin. Abbruch."; exit 1; }
claude --version 2>&1 | head -1

sag "Ordner und Startskript"
mkdir -p "$BASIS" "$ARBEIT" "$ORDNER"
chmod 700 "$BASIS"

cat > "$ORDNER/starten.sh" <<'EOF'
#!/bin/bash
# Startet den Jarvis-Agenten im Container. Läuft bis Strg-C.
export PATH="/root/.local/bin:$PATH"
cd /root/jarvis-agent
exec node jarvis-agent.mjs
EOF
chmod +x "$ORDNER/starten.sh"

# Einmal kurz anwerfen, damit das Token entsteht und die App es anzeigen kann.
if [ ! -f "$BASIS/token" ]; then
  sag "Token erzeugen"
  ( cd "$ORDNER" && timeout 5 node jarvis-agent.mjs >/dev/null 2>&1 || true )
fi

sag "Anmeldung prüfen"
if claude -p "Antworte nur mit OK." --output-format json 2>/dev/null | grep -q '"is_error":false'; then
  echo "Claude Code ist angemeldet und antwortet."
else
  warn "Claude Code antwortet noch nicht. Einmal 'claude' aufrufen und mit dem Abo anmelden."
fi

sag "Teil 2 fertig"
if [ -f "$BASIS/token" ]; then
  echo "Token: $(cat "$BASIS/token")"
fi
echo "Arbeitsordner des Agenten: $ARBEIT"
