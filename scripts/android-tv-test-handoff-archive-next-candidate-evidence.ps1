param(
    [string]$ZipPath = "",
    [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Get-SummaryMap {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $index = $line.IndexOf("=")
        if ($index -gt 0) {
            $key = $line.Substring(0, $index).Trim()
            $value = $line.Substring($index + 1).Trim()
            $map[$key] = $value
        }
    }
    return $map
}

function Add-Failure {
    param(
        [System.Collections.ArrayList]$Failures,
        [string]$Message
    )
    [void]$Failures.Add($Message)
}

function Find-LatestHandoffZip {
    param([string]$RepoRoot)

    $handoffRoot = Join-Path $RepoRoot "artifacts\factory-pilot-handoff"
    if (-not (Test-Path -LiteralPath $handoffRoot)) {
        return ""
    }

    $latest = Get-ChildItem -LiteralPath $handoffRoot -Directory -Filter "handoff-*" |
        Sort-Object LastWriteTime -Descending |
        Where-Object {
            $summary = Get-SummaryMap -Path (Join-Path $_.FullName "summary.txt")
            $zipCandidate = "$($_.FullName).zip"
            $summary.ContainsKey("status") -and $summary["status"] -eq "EXPORTED" -and (Test-Path -LiteralPath $zipCandidate)
        } |
        Select-Object -First 1

    if ($latest) {
        return "$($latest.FullName).zip"
    }
    return ""
}

function Test-ZipEntryExists {
    param(
        [string]$ArchivePath,
        [string]$EntryName
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        foreach ($entry in $archive.Entries) {
            $normalized = $entry.FullName.Replace("\", "/").TrimStart("/")
            if ($normalized -eq $EntryName) {
                return $true
            }
        }
    } finally {
        $archive.Dispose()
    }
    return $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\handoff-archive-next-candidate-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$failures = New-Object System.Collections.ArrayList

if ([string]::IsNullOrWhiteSpace($ZipPath)) {
    $ZipPath = Find-LatestHandoffZip -RepoRoot $repoRoot
}
if ([string]::IsNullOrWhiteSpace($ZipPath) -or -not (Test-Path -LiteralPath $ZipPath)) {
    Add-Failure -Failures $failures -Message "handoff zip not found"
    $resolvedZipPath = ""
} else {
    $resolvedZipPath = (Resolve-Path -LiteralPath $ZipPath).Path
}

$requiredNextCandidateEntry = "evidence/factory-pilot-gate/next-apk-candidate-signature.txt"
$verifierOutputRoot = Join-Path $runRoot "archive-verification"
if ($resolvedZipPath) {
    $verifyOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-verify-factory-handoff-archive.ps1") -ZipPath $resolvedZipPath -OutputRoot $verifierOutputRoot 2>&1
    $verifyExitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $runRoot "archive-verification.stdout.txt") -Content (($verifyOutput | Out-String).Trim())
    $verifySummary = Get-SummaryMap -Path (Join-Path $verifierOutputRoot "summary.txt")

    if ($verifyExitCode -ne 0) {
        Add-Failure -Failures $failures -Message "archive verifier exitCode expected 0 but got $verifyExitCode"
    }
    if (-not $verifySummary.ContainsKey("status") -or $verifySummary["status"] -ne "PASS") {
        Add-Failure -Failures $failures -Message "archive verifier status expected PASS but got '$($verifySummary["status"])'"
    }
    if (-not (Test-ZipEntryExists -ArchivePath $resolvedZipPath -EntryName $requiredNextCandidateEntry)) {
        Add-Failure -Failures $failures -Message "missing archive entry: $requiredNextCandidateEntry"
    }
    $requiredEntryCount = if ($verifySummary.ContainsKey("requiredEntryCount")) { [int]$verifySummary["requiredEntryCount"] } else { 0 }
    if ($requiredEntryCount -lt 21) {
        Add-Failure -Failures $failures -Message "requiredEntryCount expected at least 21 but got $requiredEntryCount"
    }
} else {
    $verifyExitCode = -1
    $verifySummary = @{}
    $requiredEntryCount = 0
}

$status = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $runRoot
    zipPath = $resolvedZipPath
    requiredNextCandidateEntry = $requiredNextCandidateEntry
    verifierOutputRoot = $verifierOutputRoot
    verifierExitCode = $verifyExitCode
    verifierStatus = if ($verifySummary.ContainsKey("status")) { $verifySummary["status"] } else { "" }
    requiredEntryCount = $requiredEntryCount
    failureCount = $failures.Count
    failures = @($failures)
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "handoff-archive-next-candidate-test.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$runRoot
zipPath=$resolvedZipPath
requiredNextCandidateEntry=$requiredNextCandidateEntry
verifierOutputRoot=$verifierOutputRoot
verifierExitCode=$verifyExitCode
verifierStatus=$($result.verifierStatus)
requiredEntryCount=$requiredEntryCount
failureCount=$($failures.Count)
failures=$($failures -join "; ")
"@
Write-TextFile -Path (Join-Path $runRoot "summary.txt") -Content $summary
Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
