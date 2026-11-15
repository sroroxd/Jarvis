---
name: jarvis-audit
description: "Projektzustand von Jarvis untersuchen – offene Issues („Wunsch: …“), offene PRs und deren CI-Status, Zweige, letzter Gerätetest, bekannte Fehler und Widersprüche zwischen PRs. Nutzen zu Sitzungsbeginn, vor größeren Änderungen oder wenn Art nach dem Stand fragt."
argument-hint: "[Schwerpunkt, z. B. pr 95 | issues | ci]"
---

# /jarvis-audit – Projektzustand und Fehler

Schwerpunkt (optional): $ARGUMENTS

Ziel: ein kompakter, belegter Lagebericht, kein Roman. Direkt selbst erledigen, keine
Subagents nötig. Nur lesen, nichts ändern.

## Schritte

1. **Lokaler Stand**: `git status`, `git branch -a`, `git log --oneline -10 origin/main`.
   `CLAUDE.md` (Regeln) und `STATUS.md` (Gedächtnis) kurz lesen.
2. **Offene Issues**: GitHub-MCP `list_issues` (Repo `sroroxd/Jarvis`, state open) bzw.
   `gh api repos/sroroxd/Jarvis/issues`. „Wunsch: …“-Issues sind Aufträge.
3. **Offene PRs**: `list_pull_requests` (open), pro PR Zweig, Entwurf ja/nein, letzter Stand.
   Für die relevanten PRs `pull_request_read` → `get_check_runs` (Build · Gerätetest ·
   Veröffentlichung).
4. **Überschneidungen**: Welche PRs ändern dieselben Dateien (vor allem
   `.github/workflows/build.yml`, `app/build.gradle`, `AndroidManifest.xml`, `CLAUDE.md`)?
   Bekannt: PR #95 ersetzt #84 und #89 und verändert Build und Signatur grundlegend.
5. **Letzter Gerätetest**: ohne PR #95 `releases/download/vorschau/geraetetest.txt`, mit PR #95
   das Artefakt „geraetetest-bericht“ am letzten Lauf. Bericht und Bilder ansehen.
6. **Fehlerbild**: fehlgeschlagene Läufe → `get_job_logs` (nur fehlgeschlagene Jobs), erste Ursache.

## Ausgabe (höchstens ~30 Zeilen)

- **Zustand main**: Version, letzter Merge, CI grün/rot
- **Offene Aufträge**: Issues mit Nummer, kurz
- **Offene PRs**: Nummer · Titel · CI · Konfliktrisiko
- **Fehler/Risiken**: belegt mit Link oder Datei:Zeile
- **Empfehlung**: nächster sinnvoller Schritt nach den Prioritäten in `CLAUDE.md`
