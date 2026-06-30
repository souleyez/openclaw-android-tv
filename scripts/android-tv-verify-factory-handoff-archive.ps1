param(
    [Parameter(Mandatory = $true)]
    [string]$ZipPath,
    [string]$Sha256SidecarPath = "",
    [string]$OutputRoot = "",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e"
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
    "feedback/README-return-package.md",
    "docs/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md",
    "docs/2026-06-30-android-tv-production-readiness.md",
    "docs/2026-06-30-next-stage-production-development-plan.md",
    "evidence/production-services/summary.txt",
    "evidence/factory-pilot-gate/summary.txt",
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
$hashManifestChecked = 0
$hashManifestIssues = @()
$missingEntries = @()
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipFile.Path)
try {
    foreach ($entry in $archive.Entries) {
        $normalized = $entry.FullName.Replace("\", "/").TrimStart("/")
        if (-not [string]::IsNullOrWhiteSpace($normalized)) {
            $entryMap[$normalized] = $entry
        }
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
    manifestParseOk = $manifestParseOk
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
manifestParseOk=$manifestParseOk
hashManifestChecked=$hashManifestChecked
hashManifestIssues=$($hashManifestIssues -join ",")
issues=$($issues -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
