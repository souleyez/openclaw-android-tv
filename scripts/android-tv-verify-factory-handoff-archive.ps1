param(
    [Parameter(Mandatory = $true)]
    [string]$ZipPath,
    [string]$Sha256SidecarPath = "",
    [string]$OutputRoot = "",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [int]$ExpectedTargetVersionCode = 2026070101,
    [string[]]$AcceptedOtaSnapshotStatuses = @("PASS", "PENDING", "RECOVERABLE_FAILURE")
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

function Get-StreamSha256 {
    param([System.IO.Stream]$Stream)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha.ComputeHash($Stream)
        return ([BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Read-ZipEntryText {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)
    $stream = $Entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Test-SafeZipEntryName {
    param([string]$Name)

    $normalized = ([string]$Name).Replace("\", "/")
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return $false
    }
    if ($normalized.StartsWith("/") -or $normalized -match "^[a-zA-Z]:") {
        return $false
    }
    $segments = @($normalized -split "/" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    return -not ($segments | Where-Object { $_ -eq ".." })
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$zipFile = Resolve-Path -Path $ZipPath
if ([string]::IsNullOrWhiteSpace($Sha256SidecarPath)) {
    $Sha256SidecarPath = "$($zipFile.Path).sha256.txt"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-archive-verification\archive-check-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$requiredEntries = @(
    "apk/OpenClawTV-0.1.14.apk",
    "feedback/android-tv-factory-feedback.json",
    "feedback/android-tv-vendor-system-permission.json",
    "feedback/return-package-checklist.json",
    "feedback/README-return-package.md",
    "docs/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md",
    "docs/2026-06-30-android-tv-production-readiness.md",
    "docs/2026-06-30-next-stage-production-development-plan.md",
    "evidence/production-services/summary.txt",
    "evidence/production-services/operator-ota-snapshot.log",
    "evidence/production-services/operator-ota-snapshot/summary.txt",
    "evidence/production-services/operator-ota-snapshot/target-ota-report.json",
    "evidence/factory-pilot-gate/summary.txt",
    "evidence/factory-pilot-gate/factory-apk-signature.txt",
    "evidence/factory-pilot-gate/ota-apk-signature.txt",
    "evidence/factory-return/logs/no-adb-diagnostic-manifest.json",
    "handoff-manifest.json",
    "handoff-files.sha256.txt",
    "README-factory-pilot.md",
    "summary.txt"
)

$issues = @()
$zipSha = Get-Sha256 -Path $zipFile.Path
$sidecarExists = Test-Path -LiteralPath $Sha256SidecarPath
$sidecarMatches = $false
if ($sidecarExists) {
    $sidecarText = (Get-Content -Raw -LiteralPath $Sha256SidecarPath).Trim()
    $sidecarMatches = $sidecarText.StartsWith($zipSha, [System.StringComparison]::OrdinalIgnoreCase)
    if (-not $sidecarMatches) {
        $issues += "zip sidecar does not match zip SHA-256"
    }
} else {
    $issues += "missing zip SHA-256 sidecar: $Sha256SidecarPath"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$entryMap = @{}
$manifest = $null
$manifestParseOk = $false
$otaSnapshot = $null
$otaSnapshotParseOk = $false
$otaSnapshotStatus = ""
$otaSnapshotReleaseId = ""
$otaSnapshotTargetDeviceUuid = ""
$otaSnapshotVersionCode = 0
$otaSnapshotArtifactSha256 = ""
$returnChecklist = $null
$returnChecklistParseOk = $false
$returnChecklistRequiredFiles = @()
$returnChecklistRequiredEvidenceDirectories = @()
$sourceRemoteMatchesHead = $false
$sourceRemoteName = ""
$sourceRemoteBranch = ""
$sourceRemoteHeadFull = ""
$factoryApkEntrySha256 = ""
$hashManifestChecked = 0
$hashManifestIssues = @()
$missingEntries = @()
$unsafeEntries = @()
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipFile.Path)
try {
    foreach ($entry in $archive.Entries) {
        if (-not (Test-SafeZipEntryName -Name $entry.FullName)) {
            $unsafeEntries += $entry.FullName
        }
        $normalized = $entry.FullName.Replace("\", "/").TrimStart("/")
        if (-not [string]::IsNullOrWhiteSpace($normalized)) {
            $entryMap[$normalized] = $entry
        }
    }

    foreach ($unsafeEntry in $unsafeEntries) {
        $issues += "unsafe archive entry: $unsafeEntry"
    }

    $missingEntries = @($requiredEntries | Where-Object { -not $entryMap.ContainsKey($_) })
    foreach ($missingEntry in $missingEntries) {
        $issues += "missing archive entry: $missingEntry"
    }

    if ($entryMap.ContainsKey("handoff-manifest.json")) {
        try {
            $manifest = Read-ZipEntryText -Entry $entryMap["handoff-manifest.json"] | ConvertFrom-Json
            $manifestParseOk = $true
        } catch {
            $issues += "handoff-manifest.json is invalid JSON"
        }
    }

    if ($manifestParseOk) {
        if ($manifest.installApk.copied -ne $true) {
            $issues += "manifest install APK is not marked copied"
        }
        if ([string]$manifest.installApk.sha256 -ne $ExpectedFactoryApkSha256.ToLowerInvariant()) {
            $issues += "manifest factory APK SHA mismatch"
        }
        if ([string]$manifest.otaCanary.releaseId -ne $ExpectedOtaReleaseId) {
            $issues += "manifest OTA release id mismatch"
        }
        if ([string]$manifest.otaCanary.targetDeviceUuid -ne $TargetDeviceUuid) {
            $issues += "manifest target device UUID mismatch"
        }
        if ([string]$manifest.otaCanary.sha256 -ne $ExpectedOtaApkSha256.ToLowerInvariant()) {
            $issues += "manifest OTA APK SHA mismatch"
        }
        if ([int]$manifest.otaCanary.versionCode -ne $ExpectedTargetVersionCode) {
            $issues += "manifest OTA versionCode mismatch"
        }
        $manifestRemoteProperty = $manifest.source.PSObject.Properties["remote"]
        if (-not $manifestRemoteProperty) {
            $issues += "manifest source remote is missing"
        } else {
            $sourceRemoteName = [string]$manifest.source.remote.name
            $sourceRemoteBranch = [string]$manifest.source.remote.branch
            $sourceRemoteHeadFull = ([string]$manifest.source.remote.headFull).ToLowerInvariant()
            $sourceRemoteMatchesHead = $manifest.source.remote.matchesHead -eq $true
            if ($sourceRemoteName -ne "origin") {
                $issues += "manifest source remote name mismatch"
            }
            if ($sourceRemoteBranch -ne [string]$manifest.source.branch) {
                $issues += "manifest source remote branch mismatch"
            }
            if (-not $sourceRemoteMatchesHead) {
                $issues += "manifest source remote does not match source HEAD"
            }
            if ([string]$manifest.source.headFull -ne $sourceRemoteHeadFull) {
                $issues += "manifest source remote head mismatch"
            }
        }
    }

    if ($entryMap.ContainsKey("apk/OpenClawTV-0.1.14.apk")) {
        $apkStream = $entryMap["apk/OpenClawTV-0.1.14.apk"].Open()
        try {
            $factoryApkEntrySha256 = Get-StreamSha256 -Stream $apkStream
        } finally {
            $apkStream.Dispose()
        }
        if ($factoryApkEntrySha256 -ne $ExpectedFactoryApkSha256.ToLowerInvariant()) {
            $issues += "factory APK archive entry SHA mismatch"
        }
    }

    $otaSnapshotEntryPath = "evidence/production-services/operator-ota-snapshot/target-ota-report.json"
    if ($entryMap.ContainsKey($otaSnapshotEntryPath)) {
        try {
            $otaSnapshot = Read-ZipEntryText -Entry $entryMap[$otaSnapshotEntryPath] | ConvertFrom-Json
            $otaSnapshotParseOk = $true
            $otaSnapshotStatus = [string]$otaSnapshot.status
            $otaSnapshotReleaseId = [string]$otaSnapshot.release.id
            $otaSnapshotTargetDeviceUuid = [string]$otaSnapshot.targetDeviceUuid
            $otaSnapshotVersionCode = [int]$otaSnapshot.release.versionCode
            $otaSnapshotArtifactSha256 = ([string]$otaSnapshot.release.artifactSha256).ToLowerInvariant()
        } catch {
            $issues += "$otaSnapshotEntryPath is invalid JSON"
        }
    }

    if ($otaSnapshotParseOk) {
        $acceptedSnapshotStatusMap = @{}
        foreach ($acceptedStatus in $AcceptedOtaSnapshotStatuses) {
            if (-not [string]::IsNullOrWhiteSpace($acceptedStatus)) {
                $acceptedSnapshotStatusMap[$acceptedStatus.Trim().ToUpperInvariant()] = $true
            }
        }
        if (-not $acceptedSnapshotStatusMap.ContainsKey($otaSnapshotStatus.Trim().ToUpperInvariant())) {
            $issues += "operator OTA snapshot status is not accepted: $otaSnapshotStatus"
        }
        if ([string]$otaSnapshot.expectedOtaReleaseId -ne $ExpectedOtaReleaseId) {
            $issues += "operator OTA snapshot expected release id mismatch"
        }
        if ($otaSnapshotReleaseId -ne $ExpectedOtaReleaseId) {
            $issues += "operator OTA snapshot release id mismatch"
        }
        if ($otaSnapshotTargetDeviceUuid -ne $TargetDeviceUuid) {
            $issues += "operator OTA snapshot target device UUID mismatch"
        }
        if ([int]$otaSnapshot.expectedTargetVersionCode -ne $ExpectedTargetVersionCode -or $otaSnapshotVersionCode -ne $ExpectedTargetVersionCode) {
            $issues += "operator OTA snapshot target versionCode mismatch"
        }
        if ($otaSnapshotArtifactSha256 -ne $ExpectedOtaApkSha256.ToLowerInvariant()) {
            $issues += "operator OTA snapshot artifact SHA mismatch"
        }
        if ([string]$otaSnapshot.release.targetScope -ne "deviceUuid:$TargetDeviceUuid") {
            $issues += "operator OTA snapshot targetScope mismatch"
        }
    }

    $returnChecklistEntryPath = "feedback/return-package-checklist.json"
    if ($entryMap.ContainsKey($returnChecklistEntryPath)) {
        try {
            $returnChecklist = Read-ZipEntryText -Entry $entryMap[$returnChecklistEntryPath] | ConvertFrom-Json
            $returnChecklistParseOk = $true
            $returnChecklistRequiredFiles = @($returnChecklist.requiredFiles | ForEach-Object { [string]$_ })
            $returnChecklistRequiredEvidenceDirectories = @($returnChecklist.requiredEvidenceDirectories | ForEach-Object { [string]$_ })
        } catch {
            $issues += "$returnChecklistEntryPath is invalid JSON"
        }
    }

    if ($returnChecklistParseOk) {
        if ([string]$returnChecklist.schema -ne "openclaw.android-tv.factory-return-checklist.v1") {
            $issues += "return package checklist schema mismatch"
        }
        foreach ($requiredFeedbackFile in @("feedback/android-tv-factory-feedback.json", "feedback/android-tv-vendor-system-permission.json")) {
            if (-not ($returnChecklistRequiredFiles -contains $requiredFeedbackFile)) {
                $issues += "return package checklist missing required file: $requiredFeedbackFile"
            }
        }
        foreach ($requiredEvidenceDirectory in @("evidence/factory-return/", "evidence/factory-return/screenshots/", "evidence/factory-return/logs/")) {
            if (-not ($returnChecklistRequiredEvidenceDirectories -contains $requiredEvidenceDirectory)) {
                $issues += "return package checklist missing required evidence directory: $requiredEvidenceDirectory"
                continue
            }
            $hasDirectoryEntry = $entryMap.ContainsKey($requiredEvidenceDirectory.TrimEnd("/") + "/")
            $hasNestedEntry = @($entryMap.Keys | Where-Object { $_.StartsWith($requiredEvidenceDirectory, [System.StringComparison]::OrdinalIgnoreCase) } | Select-Object -First 1).Count -gt 0
            if (-not $hasDirectoryEntry -and -not $hasNestedEntry) {
                $issues += "return package checklist required evidence directory not present in archive: $requiredEvidenceDirectory"
            }
        }
        if ([string]$returnChecklist.otaCanary.releaseId -ne $ExpectedOtaReleaseId) {
            $issues += "return package checklist OTA release id mismatch"
        }
        if ([string]$returnChecklist.otaCanary.targetDeviceUuid -ne $TargetDeviceUuid) {
            $issues += "return package checklist target device UUID mismatch"
        }
        if ([int]$returnChecklist.otaCanary.versionCode -ne $ExpectedTargetVersionCode) {
            $issues += "return package checklist OTA versionCode mismatch"
        }
        foreach ($requiredFactoryField in @("screenshotOrVideoPath", "logsPath")) {
            if (-not (@($returnChecklist.factoryFeedbackRequiredFields | ForEach-Object { [string]$_ }) -contains $requiredFactoryField)) {
                $issues += "return package checklist missing factory field: $requiredFactoryField"
            }
        }
        if (-not (@($returnChecklist.vendorPermissionRequiredFields | ForEach-Object { [string]$_ }) -contains "evidencePath")) {
            $issues += "return package checklist missing vendor field: evidencePath"
        }
        if ([string]$returnChecklist.optionalNoAdbDiagnosticManifest -ne "evidence/factory-return/logs/no-adb-diagnostic-manifest.json") {
            $issues += "return package checklist missing no-ADB diagnostic manifest path"
        }
        if ([string]::IsNullOrWhiteSpace([string]$returnChecklist.noAdbDiagnosticCommand) -or
            [string]$returnChecklist.noAdbDiagnosticCommand -notmatch "android-tv-check-no-adb-diagnostic-package\.ps1") {
            $issues += "return package checklist missing no-ADB diagnostic validation command"
        }
    }

    if ($entryMap.ContainsKey("handoff-files.sha256.txt")) {
        $hashManifestText = Read-ZipEntryText -Entry $entryMap["handoff-files.sha256.txt"]
        foreach ($line in ($hashManifestText -split "`r?`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            if ($line -notmatch "^([0-9a-fA-F]{64})\s+(.+)$") {
                $hashManifestIssues += "invalidLine=$line"
                continue
            }
            $expectedHash = $Matches[1].ToLowerInvariant()
            $relativePath = $Matches[2].Trim().Replace("\", "/").TrimStart("/")
            if (-not $entryMap.ContainsKey($relativePath)) {
                $hashManifestIssues += "missing=$relativePath"
                continue
            }
            $stream = $entryMap[$relativePath].Open()
            try {
                $actualHash = Get-StreamSha256 -Stream $stream
            } finally {
                $stream.Dispose()
            }
            if ($actualHash -ne $expectedHash) {
                $hashManifestIssues += "hashMismatch=$relativePath"
                continue
            }
            $hashManifestChecked += 1
        }
        foreach ($issue in $hashManifestIssues) {
            $issues += "hash manifest issue: $issue"
        }
        if ($hashManifestChecked -eq 0) {
            $issues += "hash manifest checked zero files"
        }
    }
} finally {
    $archive.Dispose()
}

$status = if ($issues.Count -eq 0) { "PASS" } else { "FAIL" }
$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    zipPath = $zipFile.Path
    zipSha256 = $zipSha
    sidecarPath = $Sha256SidecarPath
    sidecarExists = $sidecarExists
    sidecarMatches = $sidecarMatches
    archiveEntryCount = $entryMap.Count
    requiredEntryCount = $requiredEntries.Count
    missingEntries = $missingEntries
    unsafeEntries = $unsafeEntries
    manifestParseOk = $manifestParseOk
    otaSnapshotParseOk = $otaSnapshotParseOk
    otaSnapshotStatus = $otaSnapshotStatus
    otaSnapshotReleaseId = $otaSnapshotReleaseId
    otaSnapshotTargetDeviceUuid = $otaSnapshotTargetDeviceUuid
    otaSnapshotVersionCode = $otaSnapshotVersionCode
    otaSnapshotArtifactSha256 = $otaSnapshotArtifactSha256
    returnChecklistParseOk = $returnChecklistParseOk
    returnChecklistRequiredFiles = $returnChecklistRequiredFiles
    returnChecklistRequiredEvidenceDirectories = $returnChecklistRequiredEvidenceDirectories
    sourceRemoteName = $sourceRemoteName
    sourceRemoteBranch = $sourceRemoteBranch
    sourceRemoteHeadFull = $sourceRemoteHeadFull
    sourceRemoteMatchesHead = $sourceRemoteMatchesHead
    factoryApkEntrySha256 = $factoryApkEntrySha256
    hashManifestChecked = $hashManifestChecked
    hashManifestIssues = $hashManifestIssues
    issues = $issues
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-handoff-archive-verification.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$outputDir
zipPath=$($zipFile.Path)
zipSha256=$zipSha
sidecarPath=$Sha256SidecarPath
sidecarExists=$sidecarExists
sidecarMatches=$sidecarMatches
archiveEntryCount=$($entryMap.Count)
requiredEntryCount=$($requiredEntries.Count)
missingEntries=$($missingEntries -join ",")
unsafeEntries=$($unsafeEntries -join ",")
manifestParseOk=$manifestParseOk
otaSnapshotParseOk=$otaSnapshotParseOk
otaSnapshotStatus=$otaSnapshotStatus
otaSnapshotReleaseId=$otaSnapshotReleaseId
otaSnapshotTargetDeviceUuid=$otaSnapshotTargetDeviceUuid
otaSnapshotVersionCode=$otaSnapshotVersionCode
otaSnapshotArtifactSha256=$otaSnapshotArtifactSha256
returnChecklistParseOk=$returnChecklistParseOk
returnChecklistRequiredFiles=$($returnChecklistRequiredFiles -join ",")
returnChecklistRequiredEvidenceDirectories=$($returnChecklistRequiredEvidenceDirectories -join ",")
sourceRemoteName=$sourceRemoteName
sourceRemoteBranch=$sourceRemoteBranch
sourceRemoteHeadFull=$sourceRemoteHeadFull
sourceRemoteMatchesHead=$sourceRemoteMatchesHead
factoryApkEntrySha256=$factoryApkEntrySha256
hashManifestChecked=$hashManifestChecked
hashManifestIssues=$($hashManifestIssues -join ",")
issues=$($issues -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
