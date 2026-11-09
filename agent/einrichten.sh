#!/data/data/com.termux/files/usr/bin/bash
#
# Jarvis-Agent auf dem Handy einrichten – Teil 1, läuft in Termux.
#
# Start in Termux (eine Zeile):
#   curl -fsSL https://raw.githubusercontent.com/sroroxd/Jarvis/main/agent/einrichten.sh | bash
#
# (Von einem anderen Zweig testen: JARVIS_REF=<zweig> davorsetzen.)
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
# Auch den Rootfs-Ordner prüfen: „list --installed“ zeigt einen vorhandenen Container nicht immer an,
# „install“ bricht dann aber mit „already exists“ ab (unter set -e stürbe das ganze Skript). Dann lieber
# den vorhandenen Container nehmen und mit Teil 2 weitermachen.
if proot-distro list --installed 2>/dev/null | grep -qw "$DISTRO" \
   || [ -d "$PREFIX/var/lib/proot-distro/installed-rootfs/$DISTRO" ]; then
  echo "Ubuntu ist schon da."
else
  echo "Ubuntu wird installiert – das dauert ein paar Minuten."
  proot-distro install "$DISTRO" || echo "Ubuntu war schon vorhanden – weiter mit Teil 2."
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

sag "Startbefehl (Aufseher) anlegen"
mkdir -p "$HOME/.shortcuts" "$HOME/bin"
# Aufseher statt Einmalstart: hält das Handy wach und startet den Agenten nach Absturz oder Beenden
# automatisch neu, damit er durchgehend läuft (Arts Vorgabe 9. Nov. 2026). Startet keinen zweiten
# Agenten, wenn der Port 8777 schon bedient wird – so stören sich Fenster-Start und Boot-Start nicht.
cat > "$HOME/bin/jarvis-agent" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# Hält den Jarvis-Agenten durchgehend am Laufen: Wake-Lock und automatischer Neustart.
# Läuft im Termux-Fenster oder headless über Termux:Boot. Protokoll: ~/.jarvis-agent.log
set -u
LOG="$HOME/.jarvis-agent.log"
termux-wake-lock 2>/dev/null || true
trap 'termux-wake-unlock 2>/dev/null || true' EXIT
echo "$(date '+%F %T') Aufseher gestartet" >>"$LOG"
echo "Jarvis-Agent läuft (Aufseher). Termux-Benachrichtigung behalten, dann bleibt er durchgehend an."
while true; do
  # Bedient schon jemand den Port? Dann keinen zweiten Agenten starten, nur wachen.
  if curl -s -m 2 http://127.0.0.1:8777/status >/dev/null 2>&1; then
    sleep 15
    continue
  fi
  echo "$(date '+%F %T') Agent startet" >>"$LOG"
  proot-distro login ubuntu -- /bin/bash /root/jarvis-agent/starten.sh >>"$LOG" 2>&1
  echo "$(date '+%F %T') Agent beendet (Code $?), Neustart in 5 s" >>"$LOG"
  sleep 5
done
EOF
chmod +x "$HOME/bin/jarvis-agent"
cp "$HOME/bin/jarvis-agent" "$HOME/.shortcuts/Jarvis-Agent" 2>/dev/null || true
chmod +x "$HOME/.shortcuts/Jarvis-Agent" 2>/dev/null || true

sag "Automatischer Start beim Hochfahren (Termux:Boot) anlegen"
# Termux:Boot führt ausführbare Dateien in ~/.termux/boot/ beim Einschalten des Handys aus. Die Datei
# ist ohne die App „Termux:Boot“ wirkungslos – sie schadet also nicht, falls Art sie (noch) nicht hat.
mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/jarvis-agent" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# Startet den Jarvis-Agenten automatisch beim Hochfahren (braucht die App „Termux:Boot“).
exec "$HOME/bin/jarvis-agent"
EOF
chmod +x "$HOME/.termux/boot/jarvis-agent"

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
echo
echo "Durchgehend laufen: 'jarvis-agent' startet jetzt einen Aufseher, der den Agenten"
echo "nach Absturz oder Beenden automatisch neu startet. Für den Start nach einem"
echo "Handy-Neustart einmalig die App 'Termux:Boot' (F-Droid) installieren und einmal"
echo "öffnen – die Boot-Datei dafür ist schon angelegt."
