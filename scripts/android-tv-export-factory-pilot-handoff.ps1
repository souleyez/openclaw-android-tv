param(
    [string]$FactoryApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk",
    [string]$OtaApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.15.apk",
    [string]$OutputRoot = "",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [switch]$SkipApkCopy
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
}

function Copy-HandoffFile {
    param(
        [string]$Source,
        [string]$Destination
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-handoff\handoff-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$factoryApk = Resolve-Path -Path $FactoryApkPath
$otaApk = Resolve-Path -Path $OtaApkPath
$factoryApkInfo = Get-Item -LiteralPath $factoryApk.Path
$otaApkInfo = Get-Item -LiteralPath $otaApk.Path
$factorySha = Get-Sha256 -Path $factoryApk.Path
$otaSha = Get-Sha256 -Path $otaApk.Path

$factoryTemplate = Join-Path $repoRoot "docs\ops\templates\android-tv-factory-feedback.template.json"
$vendorTemplate = Join-Path $repoRoot "docs\ops\templates\android-tv-vendor-system-permission.template.json"
$factorySop = Join-Path $repoRoot "docs\ops\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md"
$readinessLedger = Join-Path $repoRoot "docs\testing\2026-06-30-android-tv-production-readiness.md"

foreach ($requiredPath in @($factoryTemplate, $vendorTemplate, $factorySop, $readinessLedger)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required handoff source file not found: $requiredPath"
    }
}

if ($factorySha -ne $ExpectedFactoryApkSha256.ToLowerInvariant()) {
    throw "Factory APK SHA mismatch. expected=$ExpectedFactoryApkSha256 actual=$factorySha"
}
if ($otaSha -ne $ExpectedOtaApkSha256.ToLowerInvariant()) {
    throw "OTA APK SHA mismatch. expected=$ExpectedOtaApkSha256 actual=$otaSha"
}

if (-not $SkipApkCopy) {
    Copy-HandoffFile -Source $factoryApk.Path -Destination (Join-Path $outputDir "apk\OpenClawTV-0.1.14.apk")
}
Copy-HandoffFile -Source $factoryTemplate -Destination (Join-Path $outputDir "feedback\android-tv-factory-feedback.json")
Copy-HandoffFile -Source $vendorTemplate -Destination (Join-Path $outputDir "feedback\android-tv-vendor-system-permission.json")
Copy-HandoffFile -Source $factorySop -Destination (Join-Path $outputDir "docs\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md")
Copy-HandoffFile -Source $readinessLedger -Destination (Join-Path $outputDir "docs\2026-06-30-android-tv-production-readiness.md")

$manifest = [pscustomobject]@{
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    packagePurpose = "OpenClaw Android TV factory pilot handoff"
    installApk = [pscustomobject]@{
        fileName = "OpenClawTV-0.1.14.apk"
        copied = -not $SkipApkCopy
        sourcePath = $factoryApk.Path
        sha256 = $factorySha
        size = $factoryApkInfo.Length
        versionName = "0.1.14"
        versionCode = 2026062401
    }
    otaCanary = [pscustomobject]@{
        fileName = "OpenClawTV-0.1.15.apk"
        sourcePath = $otaApk.Path
        sha256 = $otaSha
        size = $otaApkInfo.Length
        versionName = "0.1.15"
        versionCode = 2026070101
        releaseId = $ExpectedOtaReleaseId
        targetDeviceUuid = $TargetDeviceUuid
    }
    feedbackFiles = @(
        "feedback/android-tv-factory-feedback.json",
        "feedback/android-tv-vendor-system-permission.json"
    )
    localVerificationCommands = @(
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <factory-feedback.json>',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-permission.json>',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -AllowPending'
    )
}
$manifest | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "handoff-manifest.json") -Encoding utf8

$readme = @"
# OpenClaw Android TV Factory Pilot Handoff

Use this folder to validate a fresh factory unit against the current pilot scope.

## Install

Install only this APK on the fresh unit:

```
OpenClawTV-0.1.14.apk
```

Expected SHA-256:

```
$factorySha
```

## Required Factory Feedback

Fill:

```
feedback/android-tv-factory-feedback.json
```

Required evidence:

1. Device model, serial number, firmware version, Android version, API level, and build fingerprint.
2. Install method and install result.
3. Whether OpenClaw can be set as default Home.
4. Whether cold boot returns to OpenClaw Home.
5. Whether factory reset preserves, removes, or reinstalls OpenClaw.
6. Whether iPhone and Xiaomi casting can discover and connect on the same Wi-Fi.
7. Whether the device receives the one-device OTA canary and reports status to home.
8. Screenshot or video path for the final Home screen.

If ADB is available, capture:

```
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell dumpsys package com.openclaw.tv
adb shell getprop ro.build.fingerprint
adb shell dumpsys meminfo com.openclaw.tv
adb shell dumpsys meminfo com.hpplay.happyplay.aw
adb shell ps -A
```

## Required Vendor/System Feedback

Fill:

```
feedback/android-tv-vendor-system-permission.json
```

This decides whether production can stay APK-only or needs factory provisioning, system image preinstall, or vendor API support.

## OpenClaw Verification

After filled feedback files return, run from the repo root:

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <factory-feedback.json>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-permission.json>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -AllowPending
```

## OTA Canary

Current one-device OTA target:

```
releaseId=$ExpectedOtaReleaseId
targetDeviceUuid=$TargetDeviceUuid
targetVersion=0.1.15 / 2026070101
```

Do not expand rollout until the target device reports `verified`, `installed`, or a clear recoverable failure.
"@
Write-TextFile -Path (Join-Path $outputDir "README-factory-pilot.md") -Content $readme

$summary = @"
status=EXPORTED
createdAt=$((Get-Date).ToUniversalTime().ToString("o"))
outputDir=$outputDir
factoryApkCopied=$(-not $SkipApkCopy)
factoryApkSha256=$factorySha
factoryApkSize=$($factoryApkInfo.Length)
otaApkSha256=$otaSha
otaApkSize=$($otaApkInfo.Length)
otaReleaseId=$ExpectedOtaReleaseId
targetDeviceUuid=$TargetDeviceUuid
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
Write-Host $summary.Trim()
