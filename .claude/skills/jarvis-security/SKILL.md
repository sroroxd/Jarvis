---
name: jarvis-security
description: "Sicherheitsprobleme in Jarvis erkennen und prüfen – Geheimnisse im Repo und Verlauf, CI-Rechte und kostenpflichtige Secrets, Android-Berechtigungen, Signaturmigration, Backups und Datenabfluss. Nutzen vor jedem PR mit Build-, Manifest- oder Netzwerkänderungen und vor jedem Release."
argument-hint: "[bereich: secrets | ci | permissions | signatur | backup | diff]"
---

# /jarvis-security – Sicherheitsprüfung

Bereich (optional, sonst alles): $ARGUMENTS

Nur prüfen und berichten. **Geheimniswerte niemals ausgeben**, nur Fundort und Art.

## Schnellprüfung (direkt selbst, wenige Befehle)

1. **Geheimnisse im Arbeitsstand**:
   `git grep -nIE "sk-ant-[A-Za-z0-9_-]{10,}|sk-[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}|github_pat_|xi-api-key|storePassword|keyPassword" -- . ':!*.md' | cut -d: -f1,2`
   Das `cut` gibt nur `Datei:Zeile` aus – nie die Zeile selbst (Schwärzen per `sed` ist unzuverlässig).
   Treffer danach einzeln ansehen; erwartbar harmlos sind Header-Namen (`xi-api-key` in
   `ElevenTts`), Präfix-Erkennung in `Text` und Platzhalter in Tests.
2. **Verlauf** (bei Verdacht): `git log --oneline -S "<Muster>" --all | head`.
3. **CI**: in `.github/workflows/*.yml` `permissions:`, Verwendung von `secrets.*` und ob
   `ANTHROPIC_API_KEY` bei normalem Push gesetzt wird (soll nur bei `cloud_tests` per Hand).
4. **Manifest**: `allowBackup`, `fullBackupContent`, `dataExtractionRules`, `android:exported`,
   Berechtigungen gegen Nutzung im Code (`git grep -n <Berechtigung/API>`).
5. **Signatur**: `app/build.gradle` ohne Passwörter; der kompromittierte `app/jarvis.keystore`
   darf nicht allein signieren. Stand von PR #95 (`docs/SIGNATUR_MIGRATION.md`) beachten.
6. **Diff-Modus** (`diff`): nur `git diff origin/main...HEAD` prüfen.

## Tiefe Prüfung

Für eine unabhängige, vollständige Prüfung den Agent `security-auditor` beauftragen
(liefert Befundtabelle, ändert nichts). Fixes danach selbst umsetzen und mit `/jarvis-build`
belegen.

## Regeln

- Keine Geheimnisse an externe Dienste (auch nicht an Context7 oder in Issues/PRs).
- Keine Schlüssel rotieren, Secrets anlegen oder Konten ändern ohne Art – das ist Arts Entscheidung.
- Bekannte offene Punkte (Issue #85, Klartext-Schlüssel in `Prefs`/eigener Sicherung) als
  „bekannt“ markieren, nicht als neuen Fund.

## Ausgabe

Tabelle: Schweregrad (kritisch/hoch/mittel/niedrig) · Befund · Fundort · Empfehlung.
Darunter „Nicht geprüft“ mit Grund.
