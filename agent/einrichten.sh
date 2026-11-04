#!/data/data/com.termux/files/usr/bin/bash
#
# Jarvis-Agent auf dem Handy einrichten – Teil 1, läuft in Termux.
#
# Start in Termux (eine Zeile):
#   curl -fsSL https://raw.githubusercontent.com/sroroxd/Jarvis/main/agent/einrichten.sh | bash
#
# Solange der Sammelstand noch nicht in main ist, mit Zweig davor:
#   JARVIS_REF=sammeltest bash -c "$(curl -fsSL https://raw.githubusercontent.com/sroroxd/Jarvis/sammeltest/agent/einrichten.sh)"
#
# Was das Skript tut:
#   1. Termux-Pakete nachziehen (proot-distro, curl)
#   2. Ubuntu im Container anlegen, falls nicht vorhanden
#   3. Teil 2 im Container ausführen: Node, Claude Code, Agent-Dienst
# Es ist gefahrlos wiederholbar: Vorhandenes wird übersprungen.

set -euo pipefail

REF="${JARVIS_REF:-main}"
ROH="https://raw.githubusercontent.com/sroroxd/Jarvis/${REF}/agent"
DISTRO=ubuntu

sag() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\n\033[1;33m!! %s\033[0m\n' "$*"; }

if [ ! -d /data/data/com.termux ]; then
  warn "Das läuft nicht in Termux. Teil 1 muss in Termux gestartet werden."
  exit 1
fi

sag "Termux-Pakete prüfen"
pkg update -y >/dev/null 2>&1 || true
for p in proot-distro curl termux-api; do
  command -v "${p%%-*}" >/dev/null 2>&1 || pkg install -y "$p" || warn "$p ließ sich nicht installieren (weiter)"
done
command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro

sag "Ubuntu-Container prüfen"
if proot-distro list --installed 2>/dev/null | grep -q "$DISTRO"; then
  echo "Ubuntu ist schon da."
else
  echo "Ubuntu wird installiert – das dauert ein paar Minuten."
  proot-distro install "$DISTRO"
fi

sag "Teil 2 in den Container holen"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
curl -fsSL "$ROH/einrichten-container.sh" -o "$TMP/einrichten-container.sh"
curl -fsSL "$ROH/jarvis-agent.mjs" -o "$TMP/jarvis-agent.mjs"

ZIEL="$PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO/root"
mkdir -p "$ZIEL/jarvis-agent"
cp "$TMP/einrichten-container.sh" "$TMP/jarvis-agent.mjs" "$ZIEL/jarvis-agent/"

sag "Teil 2 ausführen (Node, Claude Code, Dienst)"
proot-distro login "$DISTRO" -- /bin/bash /root/jarvis-agent/einrichten-container.sh

sag "Startbefehl anlegen"
mkdir -p "$HOME/.shortcuts" "$HOME/bin"
cat > "$HOME/bin/jarvis-agent" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# Hält das Handy für den Agenten wach und startet ihn im Container.
termux-wake-lock 2>/dev/null || true
echo "Jarvis-Agent startet. Fenster offen lassen oder Termux-Benachrichtigung behalten."
proot-distro login ubuntu -- /bin/bash /root/jarvis-agent/starten.sh
termux-wake-unlock 2>/dev/null || true
EOF
chmod +x "$HOME/bin/jarvis-agent"
cp "$HOME/bin/jarvis-agent" "$HOME/.shortcuts/Jarvis-Agent" 2>/dev/null || true
chmod +x "$HOME/.shortcuts/Jarvis-Agent" 2>/dev/null || true

TOKEN_DATEI="$ZIEL/.jarvis-agent/token"
sag "Fertig"
echo "Agent starten:   jarvis-agent      (oder ~/bin/jarvis-agent)"
echo
if [ -f "$TOKEN_DATEI" ]; then
  echo "Token für die Jarvis-Einstellungen:"
  echo
  cat "$TOKEN_DATEI"
  echo
else
  echo "Das Token steht nach dem ersten Start in ~/.jarvis-agent/token im Container."
fi
echo "Noch offen: einmal 'proot-distro login ubuntu -- claude' aufrufen und mit"
echo "dem Claude-Abo anmelden. Ohne Anmeldung lehnt der Agent jeden Auftrag ab."
