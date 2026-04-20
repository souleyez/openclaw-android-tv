param(
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adbPath = "C:\Users\soulzyn\develop\android-sdk\platform-tools\adb.exe"
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$packageName = "com.openclaw.tv"
$activityName = "com.openclaw.tv/.MainActivity"
$remoteCapturePath = "/sdcard/rs_aitv_home.png"
$captureDir = Join-Path $repoRoot "artifacts\device-captures"

if (-not (Test-Path $adbPath)) {
    throw "adb not found: $adbPath"
}

if (-not (Test-Path $apkPath)) {
    throw "APK not found: $apkPath"
}

New-Item -ItemType Directory -Force -Path $captureDir | Out-Null

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $allArgs = @()
    if ($script:SelectedSerial) {
        $allArgs += @("-s", $script:SelectedSerial)
    }
    $allArgs += $Arguments
    & $script:AdbExe @allArgs
}

$script:AdbExe = $adbPath

& $adbPath start-server | Out-Null

$deviceOutput = (& $adbPath devices) -join "`n"
$deviceLines = ($deviceOutput -split "\r?\n" | Select-Object -Skip 1 | Where-Object {
    $_.Trim() -and ($_ -notmatch "^\*")
})

$onlineDevices = @()
foreach ($line in $deviceLines) {
    $parts = ($line -split "\s+") | Where-Object { $_ }
    if ($parts.Count -ge 2 -and $parts[1] -eq "device") {
        $onlineDevices += $parts[0]
    }
}

if ($Serial) {
    if ($onlineDevices -notcontains $Serial) {
        throw "Requested device is not online: $Serial"
    }
    $script:SelectedSerial = $Serial
} elseif ($onlineDevices.Count -eq 1) {
    $script:SelectedSerial = $onlineDevices[0]
} elseif ($onlineDevices.Count -gt 1) {
    throw "Multiple online devices detected. Use -Serial. Online devices: $($onlineDevices -join ', ')"
} else {
    throw "No online adb devices found."
}

Write-Host ("Using device: {0}" -f $script:SelectedSerial)
Write-Host "Installing APK: $apkPath"
Invoke-Adb -Arguments @("install", "-r", $apkPath) | Out-Host

Write-Host "Launching app: $activityName"
Invoke-Adb -Arguments @("shell", "am", "start", "-n", $activityName) | Out-Host

Start-Sleep -Seconds 4

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$localCapturePath = Join-Path $captureDir "home-$($script:SelectedSerial)-$timestamp.png"

Write-Host "Capturing screenshot..."
Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteCapturePath) | Out-Null
Invoke-Adb -Arguments @("shell", "screencap", "-p", $remoteCapturePath) | Out-Null
Invoke-Adb -Arguments @("pull", $remoteCapturePath, $localCapturePath) | Out-Host
Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteCapturePath) | Out-Null

Write-Host ("Screenshot saved to: {0}" -f $localCapturePath)
