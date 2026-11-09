<#
  Kostenlose deutsche Premium-Neuralstimme für Jarvis auf Arts Galaxy S24 Ultra (über USB).

  Hintergrund (Wunsch #57 „natürlichere Stimme", #64 „tiefe Stimme"):
  Jarvis bevorzugt automatisch die TTS-Engine „SherpaTTS" (org.woheller69.ttsengine), die
  offline Piper-Neuralstimmen spricht – gratis, ohne Netz, ohne API-Guthaben. Dieses Skript
  installiert SherpaTTS und spielt die deutsche Piper-Stimme „de_DE-thorsten-high" ein,
  ganz ohne die SherpaTTS-Oberfläche: Es legt das Modell in den Ordner modelDir, aus dem
  SherpaTTS beim nächsten Start selbst übernimmt.

  Danach spricht Jarvis mit dieser Stimme, sobald in den Jarvis-Einstellungen
    - der Schalter „Jarvis spricht mit der Offline-Stimme" AUS ist und
    - kein ElevenLabs-Schlüssel eingetragen ist (leer = Handy-Engine = SherpaTTS).

  Erstmals ausgeführt am 9. Nov. 2026; die Stimme lief danach im Gerätetest
  (Jarvis „Probe hören" → Audio aus org.woheller69.ttsengine, 22050 Hz).

  Aufruf (PowerShell, Handy per USB, USB-Debugging an):
      .\handy\premium-stimme.ps1
      .\handy\premium-stimme.ps1 -Stimme de_DE-thorsten-medium   # kleinere/schnellere Variante
#>
param([string]$Stimme = 'de_DE-thorsten-high')

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# --- adb finden (wie handy\tuning.ps1) -------------------------------------------------
$pt = Join-Path $env:LOCALAPPDATA 'Android\platform-tools'
$adb = Join-Path $pt 'adb.exe'
if (-not (Test-Path $adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
    else {
        Write-Host 'adb fehlt – lade die Android Platform-Tools von Google (ca. 8 MB) ...'
        $zip = Join-Path $env:TEMP 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
        Expand-Archive $zip -DestinationPath $env:LOCALAPPDATA\Android -Force
        $adb = Join-Path $pt 'adb.exe'
    }
}

function Adb { & $adb @args }

$geraet = (Adb devices) | Where-Object { $_ -match '\tdevice$' }
if (-not $geraet) { Write-Host 'Kein Handy per USB gefunden (USB-Debugging an?).' -ForegroundColor Red; exit 1 }

$pkg = 'org.woheller69.ttsengine'
$tmp = Join-Path $env:TEMP 'jarvis-stimme'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# --- 1) SherpaTTS installieren (falls noch nicht da) -----------------------------------
$da = (Adb shell pm list packages) | Select-String $pkg
if ($da) {
    Write-Host "SherpaTTS ist schon installiert." -ForegroundColor Green
} else {
    Write-Host 'Lade SherpaTTS (F-Droid, ca. 86 MB) ...'
    $apk = Join-Path $tmp 'sherpatts.apk'
    Invoke-WebRequest 'https://f-droid.org/repo/org.woheller69.ttsengine_34.apk' -OutFile $apk
    Write-Host 'Installiere SherpaTTS ...'
    Adb install -r $apk | Out-Host
}

# --- 2) Deutsches Piper-Modell laden und einspielen ------------------------------------
$dest = "/sdcard/Android/data/$pkg/files"
# Schon ein deutsches Modell da? Dann nichts weiter tun.
$schon = (Adb shell "ls $dest/deu/model.onnx 2>/dev/null")
if ($schon -match 'model.onnx') {
    Write-Host "Deutsches Modell liegt schon in $dest/deu – fertig." -ForegroundColor Green
} else {
    $url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$Stimme.tar.bz2"
    $tar = Join-Path $tmp 'modell.tar.bz2'
    Write-Host "Lade Stimme $Stimme (ca. 60–110 MB) ..."
    Invoke-WebRequest $url -OutFile $tar
    Write-Host 'Entpacke ...'
    tar -xjf $tar -C $tmp
    $mdir = Join-Path $tmp "vits-piper-$Stimme"
    "deu`n$Stimme" | Out-File -FilePath (Join-Path $mdir 'lang') -Encoding ascii -NoNewline

    Write-Host 'Spiele Modell in SherpaTTS ein (modelDir) ...'
    Adb shell "rm -rf $dest/modelDir; mkdir -p $dest/modelDir" | Out-Null
    Adb push (Join-Path $mdir "$Stimme.onnx") "$dest/modelDir/model.onnx" | Out-Host
    Adb push (Join-Path $mdir 'tokens.txt')   "$dest/modelDir/tokens.txt" | Out-Host
    Adb push (Join-Path $mdir 'lang')         "$dest/modelDir/lang"       | Out-Host

    Write-Host 'Starte SherpaTTS einmal, damit es das Modell übernimmt ...'
    Adb shell am force-stop $pkg | Out-Null
    Adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep 8
    $fertig = (Adb shell "ls $dest/deu/model.onnx 2>/dev/null")
    if ($fertig -match 'model.onnx') { Write-Host "Stimme $Stimme eingespielt." -ForegroundColor Green }
    else { Write-Host 'Modell wurde noch nicht übernommen – SherpaTTS einmal von Hand öffnen.' -ForegroundColor Yellow }
}

Write-Host ''
Write-Host 'Fertig. Noch in den Jarvis-Einstellungen prüfen:' -ForegroundColor Cyan
Write-Host '  - „Jarvis spricht mit der Offline-Stimme" AUS (sonst gilt die Kyutai-Stimme davor)'
Write-Host '  - ElevenLabs-Schlüssel leer (leer = Handy-Engine = SherpaTTS)'
Write-Host '  - „Probe hören" antippen: Jarvis spricht jetzt mit de_DE-thorsten.'
