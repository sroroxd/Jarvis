<#
  Tuning für Arts Galaxy S24 Ultra (SM-S928B, Android 17, One UI 9) über USB.

  Gemessen am 8. Nov. 2026, bevor dieses Skript geschrieben wurde:
    - Bildwiederholrate: adaptiv (refresh_rate_mode = 1)           -> bleibt, ist schon richtig
    - Animationen: 0,5                                              -> bleibt, ist schon schnell
    - Adaptiver Akku: an, Thermik: Status 0, AP 31–49 °C            -> in Ordnung
    - App-Zwischenspeicher: 10,9 GB, Videos: 83 GB, frei: 280 GB    -> Cache lässt sich freiräumen
    - Jarvis Test: Akku-Ausnahme ja, Standby-Gruppe „exempt“        -> in Ordnung

  Was das Skript tut (jede Stufe fragt vorher, alles ist umkehrbar):
    1. Sichert alle Werte, die es anfasst, nach handy\sicherung-<Datum>.txt
    2. Jarvis vorab vollständig übersetzen (ART „speed“) – schnellerer Start und weniger Ruckler im Dienst
    3. App-Zwischenspeicher aller Apps leeren (nur Cache, keine Daten; Apps bauen ihn bei Bedarf neu auf)
    4. Animationen auf 0,35 (wirkt flinker; zurück mit -Zurueck)

  Aufruf (PowerShell, Handy per USB, USB-Debugging an):
      .\handy\tuning.ps1             # Schritt für Schritt mit Rückfrage
      .\handy\tuning.ps1 -Zurueck    # Animationen wieder auf die gesicherten Werte
#>
param([switch]$Zurueck)

$ErrorActionPreference = 'Stop'
$pt = Join-Path $env:LOCALAPPDATA 'Android\platform-tools'
$adb = Join-Path $pt 'adb.exe'
if (-not (Test-Path $adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source } else {
        Write-Host "adb fehlt – lade die Android Platform-Tools von Google (ca. 8 MB) ..."
        $zip = Join-Path $env:TEMP 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
        Expand-Archive $zip -DestinationPath (Split-Path $pt) -Force
        Remove-Item $zip
        if ((Get-AuthenticodeSignature $adb).SignerCertificate.Subject -notmatch 'Google LLC') { throw 'adb.exe ist nicht von Google signiert – Abbruch.' }
    }
}

function Sh([string]$c) { (& $adb shell $c) -join "`n" }
function Frage([string]$text) { (Read-Host "$text [j/N]") -match '^[jJyY]' }

$state = (& $adb get-state 2>$null)
if ($state -ne 'device') { throw "Kein freigegebenes Handy gefunden (Zustand: '$state'). USB-Debugging zulassen und neu starten." }
Write-Host ("Verbunden: " + (Sh 'getprop ro.product.model') + ", Android " + (Sh 'getprop ro.build.version.release'))

$keys = 'window_animation_scale','transition_animation_scale','animator_duration_scale'
$sicherungen = Get-ChildItem (Join-Path $PSScriptRoot 'sicherung-*.txt') -ErrorAction SilentlyContinue | Sort-Object Name

if ($Zurueck) {
    if (-not $sicherungen) { throw 'Keine Sicherung gefunden.' }
    $erste = $sicherungen[0].FullName
    Get-Content $erste | Where-Object { $_ -match '^global (\S+)=(.+)$' } | ForEach-Object {
        Sh "settings put global $($Matches[1]) $($Matches[2])" | Out-Null
        Write-Host "zurück: $($Matches[1]) = $($Matches[2])"
    }
    return
}

# 1. Sicherung
$datei = Join-Path $PSScriptRoot ("sicherung-" + (Get-Date -Format 'yyyy-MM-dd_HHmm') + ".txt")
$zeilen = foreach ($k in $keys) { "global $k=" + (Sh "settings get global $k") }
$zeilen | Set-Content -Encoding utf8 $datei
Write-Host "Gesichert: $datei"

# 2. Jarvis vorab übersetzen
$jarvis = (Sh 'pm list packages de.art.jarvis') -split "`n" | ForEach-Object { $_ -replace '^package:','' } | Where-Object { $_ }
foreach ($p in $jarvis) {
    if (Frage "$p vollständig vorab übersetzen (dauert ~30 s)?") {
        Write-Host (Sh "cmd package compile -m speed -f $p")
    }
}

# 3. Cache leeren
$vorher = Sh 'df /data | tail -1'
if (Frage 'App-Zwischenspeicher aller Apps leeren (rund 10 GB, keine Daten)?') {
    Sh 'pm trim-caches 1000G' | Out-Null
    Write-Host "Vorher: $vorher"
    Write-Host ("Nachher: " + (Sh 'df /data | tail -1'))
}

# 4. Animationen
if (Frage 'Animationen von 0,5 auf 0,35 (flinker)?') {
    foreach ($k in $keys) { Sh "settings put global $k 0.35" | Out-Null }
    Write-Host 'Animationen 0,35. Zurück: .\handy\tuning.ps1 -Zurueck'
}
Write-Host 'Fertig.'
