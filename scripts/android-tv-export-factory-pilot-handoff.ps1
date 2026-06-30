param(
    [string]$FactoryApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk",
    [string]$OtaApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.15.apk",
    [string]$OutputRoot = "",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [switch]$SkipApkCopy,
    [switch]$SkipZip,
    [string]$ZipPath = ""
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

function Resolve-OutputFilePath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

function Get-HandoffRelativePath {
    param(
        [string]$Root,
        [string]$Path
    )
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $pathFull = [System.IO.Path]::GetFullPath($Path)
    if (-not $pathFull.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -and
        -not $pathFull.StartsWith($rootFull + [System.IO.Path]::AltDirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside handoff root: $Path"
    }
    return $pathFull.Substring($rootFull.Length + 1).Replace("\", "/")
}

function Write-HandoffHashManifest {
    param(
        [string]$Root,
        [string]$ManifestPath
    )
    $manifestFullPath = [System.IO.Path]::GetFullPath($ManifestPath)
    $lines = Get-ChildItem -LiteralPath $Root -File -Recurse |
        Where-Object { [System.IO.Path]::GetFullPath($_.FullName) -ne $manifestFullPath } |
        Sort-Object FullName |
        ForEach-Object {
            $relativePath = Get-HandoffRelativePath -Root $Root -Path $_.FullName
            "$(Get-Sha256 -Path $_.FullName)  $relativePath"
        }
    Write-TextFile -Path $ManifestPath -Content ($lines -join "`n")
}

function Copy-HandoffFile {
    param(
        [string]$Source,
        [string]$Destination
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

function Copy-HandoffDirectory {
    param(
        [string]$Source,
        [string]$Destination
    )
    if (-not (Test-Path -LiteralPath $Source)) {
        return $false
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination -Recurse -Force
    return $true
}

function Get-SummaryStatus {
    param([string]$Directory)

    $summaryPath = Join-Path $Directory "summary.txt"
    if (-not (Test-Path -LiteralPath $summaryPath)) {
        return ""
    }
    foreach ($line in Get-Content -LiteralPath $summaryPath) {
        if ($line -match "^status=(.+)$") {
            return $Matches[1].Trim()
        }
    }
    return ""
}

function Get-LatestSummaryDirectory {
    param(
        [string]$Root,
        [string]$Pattern,
        [string[]]$AcceptedStatuses
    )
    if (-not (Test-Path -LiteralPath $Root)) {
        return $null
    }
    $accepted = @{}
    foreach ($status in $AcceptedStatuses) {
        $accepted[$status.ToUpperInvariant()] = $true
    }
    return Get-ChildItem -LiteralPath $Root -Directory |
        Where-Object { $_.Name -like $Pattern } |
        Where-Object {
            $status = (Get-SummaryStatus -Directory $_.FullName).ToUpperInvariant()
            $accepted.ContainsKey($status)
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Get-GitValue {
    param([string[]]$Arguments)
    $output = & git -C $repoRoot @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    return (($output | Out-String).Trim())
}

function Get-GitRemoteHead {
    param(
        [string]$RemoteName,
        [string]$BranchName
    )
    if ([string]::IsNullOrWhiteSpace($RemoteName) -or [string]::IsNullOrWhiteSpace($BranchName)) {
        return ""
    }
    $output = Get-GitValue -Arguments @("ls-remote", "--heads", $RemoteName, $BranchName)
    if ([string]::IsNullOrWhiteSpace($output)) {
        return ""
    }
    $firstLine = @($output -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
    if ($firstLine.Count -eq 0 -or $firstLine[0] -notmatch "^([0-9a-fA-F]{40})\s+") {
        return ""
    }
    return $Matches[1].ToLowerInvariant()
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-handoff\handoff-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path
$zipFullPath = ""
$zipSha256SidecarPath = ""
if (-not $SkipZip) {
    if ([string]::IsNullOrWhiteSpace($ZipPath)) {
        $ZipPath = "$outputDir.zip"
    }
    $zipFullPath = Resolve-OutputFilePath -Path $ZipPath
    $normalizedOutputDir = [System.IO.Path]::GetFullPath($outputDir).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $normalizedZipPath = [System.IO.Path]::GetFullPath($zipFullPath)
    if ($normalizedZipPath.StartsWith($normalizedOutputDir + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
        $normalizedZipPath.StartsWith($normalizedOutputDir + [System.IO.Path]::AltDirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "ZipPath must be outside the handoff folder: $zipFullPath"
    }
    $zipParent = Split-Path -Parent $zipFullPath
    if ($zipParent) {
        New-Item -ItemType Directory -Force -Path $zipParent | Out-Null
    }
    $zipSha256SidecarPath = "$zipFullPath.sha256.txt"
}

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
$nextStagePlan = Join-Path $repoRoot "docs\plans\2026-06-30-next-stage-production-development-plan.md"

$latestProductionService = Get-LatestSummaryDirectory -Root (Join-Path $repoRoot "artifacts\service-checks") -Pattern "production-services-*" -AcceptedStatuses @("PASS")
$latestFactoryGate = Get-LatestSummaryDirectory -Root (Join-Path $repoRoot "artifacts\factory-pilot-gates") -Pattern "gate-check-*" -AcceptedStatuses @("PASS", "PENDING")
$branch = Get-GitValue -Arguments @("branch", "--show-current")
$head = Get-GitValue -Arguments @("rev-parse", "--short", "HEAD")
$headFull = (Get-GitValue -Arguments @("rev-parse", "HEAD")).ToLowerInvariant()
$headSubject = Get-GitValue -Arguments @("log", "-1", "--format=%s")
$statusShort = Get-GitValue -Arguments @("status", "--short")
$remoteName = "origin"
$remoteHeadFull = Get-GitRemoteHead -RemoteName $remoteName -BranchName $branch
$remoteMatchesHead = -not [string]::IsNullOrWhiteSpace($headFull) -and $remoteHeadFull -eq $headFull

foreach ($requiredPath in @($factoryTemplate, $vendorTemplate, $factorySop, $readinessLedger, $nextStagePlan)) {
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
Copy-HandoffFile -Source $nextStagePlan -Destination (Join-Path $outputDir "docs\2026-06-30-next-stage-production-development-plan.md")

$productionServiceEvidenceCopied = $false
if ($latestProductionService) {
    $productionServiceEvidenceCopied = Copy-HandoffDirectory -Source $latestProductionService.FullName -Destination (Join-Path $outputDir "evidence\production-services")
}
$factoryGateEvidenceCopied = $false
if ($latestFactoryGate) {
    $factoryGateEvidenceCopied = Copy-HandoffDirectory -Source $latestFactoryGate.FullName -Destination (Join-Path $outputDir "evidence\factory-pilot-gate")
}

$manifest = [pscustomobject]@{
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    packagePurpose = "OpenClaw Android TV factory pilot handoff"
    source = [pscustomobject]@{
        branch = $branch
        head = $head
        headFull = $headFull
        headSubject = $headSubject
        clean = [string]::IsNullOrWhiteSpace($statusShort)
        remote = [pscustomobject]@{
            name = $remoteName
            branch = $branch
            headFull = $remoteHeadFull
            matchesHead = $remoteMatchesHead
        }
    }
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
        "feedback/android-tv-vendor-system-permission.json",
        "feedback/README-return-package.md"
    )
    docs = @(
        "docs/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md",
        "docs/2026-06-30-android-tv-production-readiness.md",
        "docs/2026-06-30-next-stage-production-development-plan.md"
    )
    evidence = [pscustomobject]@{
        productionServices = [pscustomobject]@{
            copied = $productionServiceEvidenceCopied
            sourcePath = if ($latestProductionService) { $latestProductionService.FullName } else { "" }
            packagePath = if ($productionServiceEvidenceCopied) { "evidence/production-services" } else { "" }
        }
        factoryPilotGate = [pscustomobject]@{
            copied = $factoryGateEvidenceCopied
            sourcePath = if ($latestFactoryGate) { $latestFactoryGate.FullName } else { "" }
            packagePath = if ($factoryGateEvidenceCopied) { "evidence/factory-pilot-gate" } else { "" }
        }
    }
    fileHashManifest = "handoff-files.sha256.txt"
    archive = [pscustomobject]@{
        planned = -not $SkipZip
        format = if ($SkipZip) { "" } else { "zip" }
        zipPath = $zipFullPath
        sha256SidecarPath = $zipSha256SidecarPath
        contentsRoot = $outputDir
    }
    localVerificationCommands = @(
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-audit-factory-pilot-plan.ps1 -AllowIncomplete',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath <handoff.zip>',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <factory-return.zip-or-folder> -AllowPending',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-refresh-factory-pilot-evidence.ps1',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-factory-pilot-expansion-readiness.ps1 -AllowBlocked',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending',
        'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-ingest-factory-pilot-feedback.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -EvidenceRoot <factory-return-folder> -AllowPending',
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

## Included Evidence

The package includes the latest local production service check and factory pilot gate evidence when available:

```
evidence/production-services
evidence/factory-pilot-gate
handoff-files.sha256.txt
```

The production-services evidence includes `certificates.json` for `oc.goods-editor.com` and `gm.goods-editor.com`, plus `operator-ota-snapshot/target-ota-report.json` so operators can confirm the one-device OTA release/report state without raw database access.

## OpenClaw Verification

After filled feedback files return, run from the repo root:

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-audit-factory-pilot-plan.ps1 -AllowIncomplete
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath <handoff.zip>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <factory-return.zip-or-folder> -AllowPending
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-refresh-factory-pilot-evidence.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-factory-pilot-expansion-readiness.ps1 -AllowBlocked
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-ingest-factory-pilot-feedback.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -EvidenceRoot <factory-return-folder> -AllowPending
```

The plan audit maps the current evidence to the plan's Definition Of Next Milestone Done. The archive verification command validates the transferred handoff zip and sidecar without extracting it. The return-package command accepts a factory-returned zip or folder, locates both feedback JSON files, and runs the existing intake flow. The refresh command updates the handoff package, factory pilot gate, and expansion guard evidence in one run. The expansion-readiness command must report `PASS` before rollout expands beyond the current pilot scope. The readiness command verifies that the production ledger contains all required rows and fields. The intake command copies the returned feedback into one evidence folder, runs both classifiers, and runs the factory pilot gate. To inspect lower-level checks manually:

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

Do not expand rollout until the target device reports `verified`, `installed`, or `reported`. A clear recoverable failure keeps diagnosis actionable but still blocks expansion.

## Archive Transfer

When this handoff is exported with the default settings, transfer the generated `.zip` file and its `.sha256.txt` sidecar together. The APK inside the archive remains the only APK to install on the factory unit.
"@
Write-TextFile -Path (Join-Path $outputDir "README-factory-pilot.md") -Content $readme

$returnReadmeTemplate = @'
# Factory Return Package Instructions

Return the whole handoff folder as a `.zip` after testing, or return a folder with the same file names.

If returning a `.zip`, keep every entry name package-relative. Do not include absolute paths, Windows drive paths, empty entry names, or `..` traversal segments. The OpenClaw intake script rejects unsafe zip entries before extraction.

## Required Files To Fill

Fill these two JSON files in place:

```
feedback/android-tv-factory-feedback.json
feedback/android-tv-vendor-system-permission.json
```

Do not rename these files. The OpenClaw intake script locates these exact file names automatically.

## Evidence Files

Put the required screenshot/video evidence, required logs package, and any ADB outputs under one of these folders:

```
evidence/factory-return/
evidence/factory-return/screenshots/
evidence/factory-return/logs/
```

Then write the relative evidence paths into the JSON fields:

```
screenshotOrVideoPath
logsPath
evidencePath
```

Only package-relative paths are accepted. Absolute paths, URLs, and paths that escape the returned package are rejected. If a path is written in the JSON, the file or folder must exist in the returned zip/folder. `screenshotOrVideoPath` and `logsPath` are required for factory feedback to classify as complete.

## Required Result Fields

Before returning the package, make sure these factory result fields are not left blank or left as `not_tested` unless the test is genuinely unavailable:

```
installMethod
installResult
defaultHomeSettingMethod
defaultHomeResult
resolveActivityOutput
firstLaunchHomeResult
remoteHomeReturnResult
coldBootHomeResult
restoreFactoryApkState
iphoneDiscovery
xiaomiDiscovery
otaReceived
otaInstallResult
homeReportStatus
screenshotOrVideoPath
logsPath
```

## OTA Canary Scope

The current OTA canary remains one-device scoped:

```
releaseId=__EXPECTED_OTA_RELEASE_ID__
targetDeviceUuid=__TARGET_DEVICE_UUID__
targetVersion=0.1.15 / 2026070101
```

If the target device does not receive or report the OTA, keep the actual failure or pending state in the JSON instead of marking it PASS.

## OpenClaw Intake Command

OpenClaw validates the returned zip or folder with:

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <factory-return.zip-or-folder> -AllowPending
```

The package is production-pass eligible only after the intake and factory pilot gate have no failed or pending production rows.
'@
$returnReadme = $returnReadmeTemplate.Replace("__EXPECTED_OTA_RELEASE_ID__", $ExpectedOtaReleaseId).Replace("__TARGET_DEVICE_UUID__", $TargetDeviceUuid)
Write-TextFile -Path (Join-Path $outputDir "feedback\README-return-package.md") -Content $returnReadme

$summary = @"
status=EXPORTED
createdAt=$((Get-Date).ToUniversalTime().ToString("o"))
outputDir=$outputDir
sourceBranch=$branch
sourceHead=$head
sourceHeadFull=$headFull
sourceRemote=$remoteName
sourceRemoteBranch=$branch
sourceRemoteHeadFull=$remoteHeadFull
sourceRemoteMatchesHead=$remoteMatchesHead
factoryApkCopied=$(-not $SkipApkCopy)
factoryApkSha256=$factorySha
factoryApkSize=$($factoryApkInfo.Length)
otaApkSha256=$otaSha
otaApkSize=$($otaApkInfo.Length)
otaReleaseId=$ExpectedOtaReleaseId
targetDeviceUuid=$TargetDeviceUuid
productionServiceEvidenceCopied=$productionServiceEvidenceCopied
factoryGateEvidenceCopied=$factoryGateEvidenceCopied
archivePlanned=$(-not $SkipZip)
archivePath=$zipFullPath
archiveSha256SidecarPath=$zipSha256SidecarPath
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
Write-HandoffHashManifest -Root $outputDir -ManifestPath (Join-Path $outputDir "handoff-files.sha256.txt")

$archiveSummary = ""
if (-not $SkipZip) {
    Compress-Archive -Path (Join-Path $outputDir "*") -DestinationPath $zipFullPath -Force
    $zipSha = Get-Sha256 -Path $zipFullPath
    $zipInfo = Get-Item -LiteralPath $zipFullPath
    Write-TextFile -Path $zipSha256SidecarPath -Content "$zipSha  $(Split-Path -Leaf $zipFullPath)"
    $archiveSummary = @"
archiveCreated=True
archivePath=$zipFullPath
archiveSha256=$zipSha
archiveSize=$($zipInfo.Length)
archiveSha256SidecarPath=$zipSha256SidecarPath
"@
}

if ($archiveSummary) {
    Write-Host ($summary.Trim() + "`n" + $archiveSummary.Trim())
} else {
    Write-Host $summary.Trim()
}
