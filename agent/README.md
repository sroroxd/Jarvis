# Jarvis-Agent – das Rückgrat für lange Aufgaben

Der Agent läuft **auf dem Handy** in Termux und erledigt alles, was Sekunden bis
Minuten braucht: Dateien, Recherche, Zeitpläne, mehrstufige Aufgaben. Dafür ruft
er Claude Code auf – und das zählt gegen das **Claude-Abo**, nicht gegen das
API-Guthaben.

Die App bleibt die Oberfläche. Stimme, Gespräch und Spielmodus laufen weiter über
die Claude-API, weil der Agent fürs Sprechtempo und für Spielbilder zu träge ist.

## Einrichten

In Termux eine Zeile:

```sh
curl -fsSL https://raw.githubusercontent.com/sroroxd/Jarvis/main/agent/einrichten.sh | bash
```

Solange der Zweig noch nicht in `main` ist:

```sh
JARVIS_REF=agent-ruckgrat bash -c "$(curl -fsSL https://raw.githubusercontent.com/sroroxd/Jarvis/agent-ruckgrat/agent/einrichten.sh)"
```

Das Skript legt Ubuntu über `proot-distro` an, installiert Node und Claude Code,
richtet den Dienst ein und zeigt am Ende das **Token**.

Danach zweimal von Hand:

1. **Anmelden** (einmalig, braucht Arts Konto):
   `proot-distro login ubuntu -- claude` → mit dem Abo anmelden, dann beenden.
2. **Token** aus der Skriptausgabe in die Jarvis-Einstellungen eintragen,
   Abschnitt „Agent-Rückgrat", und den Schalter einschalten.

## Starten

```sh
jarvis-agent
```

Hält über `termux-wake-lock` das Handy für den Dienst wach. Termux-Fenster offen
lassen oder die Termux-Benachrichtigung behalten – schließt Art Termux, ist der
Agent weg und Jarvis meldet den Auftrag als nicht zustellbar.

## Benutzen

Aufträge gibt Art per Sprache ab:

- „Übernimm das: sortier meine Downloads nach Datum"
- „Auftrag: schreib mir eine Einkaufsliste aus dem Kühlschrankfoto"
- „Kümmere dich um die Rechnungen vom Oktober"
- „Erledige das Backup"
- „Arbeite daran, räum den Ordner auf"

Alles andere geht weiter den schnellen Weg. Im Spielmodus ist der Agent aus.
Jarvis bestätigt sofort („Ich kümmere mich darum, Sir.") und spricht das Ergebnis,
sobald es da ist – auch wenn Art zwischenzeitlich etwas anderes gefragt hat.

## Technik

| Teil | Was |
|---|---|
| Schnittstelle | HTTP auf `127.0.0.1:8777`, nur lokal, Token im `Authorization`-Kopf |
| `GET /status` | läuft der Agent? (ohne Token) |
| `POST /auftrag` | `{"text": "…", "neu": false}` → `{"antwort", "sitzung", "kosten", "dauer"}` |
| `POST /neu` | Sitzung zurücksetzen, nächster Auftrag fängt frisch an |
| KI | `claude -p --output-format json --permission-mode bypassPermissions` |
| Arbeitsordner | `~/jarvis-arbeit` im Container |
| Zeitlimit | 10 Minuten pro Auftrag, danach Abbruch |

**Kein `--bare`:** Dieser Modus liest ausdrücklich keine OAuth-Anmeldung und
verlangt einen API-Schlüssel – das wäre Guthaben statt Abo.

**Warum ein Token:** Auf Android darf jede App auf `127.0.0.1` verbinden. Ohne
Token könnte jede andere App auf dem Handy dem Agenten Aufträge geben.

**`bypassPermissions`:** Ohne das bliebe jeder Auftrag an einer Rückfrage hängen,
die niemand beantwortet. Der Schaden ist auf den proot-Container begrenzt – das
Android-System selbst liegt außerhalb.

## Wenn etwas nicht geht

| Meldung | Ursache |
|---|---|
| „Ich erreiche den Agenten nicht" | Dienst läuft nicht → `jarvis-agent` in Termux starten |
| „falsches Token" | Token in den Einstellungen stimmt nicht mit `~/.jarvis-agent/token` überein |
| „Authentication error" | Claude Code ist nicht angemeldet → `proot-distro login ubuntu -- claude` |
| „arbeitet noch am letzten Auftrag" | ein Auftrag läuft; der Agent nimmt einen zur Zeit |
