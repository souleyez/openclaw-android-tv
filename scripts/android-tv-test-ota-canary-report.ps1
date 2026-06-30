param(
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

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )
    $Value | ConvertTo-Json -Depth 8 | Out-File -FilePath $Path -Encoding utf8
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
        [string]$CaseName,
        [string]$Message
    )
    [void]$Failures.Add("$CaseName`: $Message")
}

function Assert-Equal {
    param(
        [System.Collections.ArrayList]$Failures,
        [string]$CaseName,
        [string]$Name,
        [object]$Expected,
        [object]$Actual
    )
    if ([string]$Expected -ne [string]$Actual) {
        Add-Failure -Failures $Failures -CaseName $CaseName -Message "$Name expected '$Expected' but got '$Actual'"
    }
}

function New-AdminOtaSnapshot {
    param(
        [string]$ReportStatus = "",
        [string]$ReportNote = "",
        [int]$VersionCode = 2026070101,
        [string]$ReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
        [string]$DeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e"
    )

    $reports = @()
    if (-not [string]::IsNullOrWhiteSpace($ReportStatus)) {
        $reports += [ordered]@{
            releaseId = $ReleaseId
            deviceUuid = $DeviceUuid
            currentVersionCode = 2026062401
            targetVersionCode = 2026070101
            status = $ReportStatus
            progressPercent = if ($ReportStatus -eq "installed") { 100 } else { 50 }
            note = $ReportNote
            reportedAt = "2026-06-30T10:00:00.000Z"
            updatedAt = "2026-06-30T10:01:00.000Z"
        }
    }

    return [ordered]@{
        releases = @(
            [ordered]@{
                id = $ReleaseId
                versionName = "0.1.15"
                versionCode = $VersionCode
                rolloutStatus = "rolling"
                targetScope = "deviceUuid:$DeviceUuid"
                installPolicy = "vendor_silent"
                artifactSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86"
            }
        )
        reports = $reports
        totals = [ordered]@{
            releases = 1
            rolling = 1
            reports = $reports.Count
        }
    }
}

function Invoke-OtaCase {
    param(
        [string]$Name,
        [object]$Snapshot,
        [int]$ExpectedExitCode,
        [string]$ExpectedStatus,
        [switch]$AllowPending
    )

    $caseRoot = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
    $snapshotPath = Join-Path $caseRoot "admin-ota.json"
    Write-JsonFile -Path $snapshotPath -Value $Snapshot
    $outputRoot = Join-Path $caseRoot "out"

    $args = @(
        "-AdminSnapshotPath", $snapshotPath,
        "-OutputRoot", $outputRoot,
        "-SkipRemoteAdminFallback"
    )
    if ($AllowPending) {
        $args += "-AllowPending"
    }

    $scriptOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-check-ota-canary-report.ps1") @args 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $caseRoot "stdout.txt") -Content (($scriptOutput | Out-String).Trim())

    $summaryPath = Join-Path $outputRoot "summary.txt"
    $summary = Get-SummaryMap -Path $summaryPath
    Assert-Equal -Failures $failures -CaseName $Name -Name "exitCode" -Expected $ExpectedExitCode -Actual $exitCode
    Assert-Equal -Failures $failures -CaseName $Name -Name "status" -Expected $ExpectedStatus -Actual $summary["status"]

    [void]$caseResults.Add([pscustomobject]@{
        name = $Name
        exitCode = $exitCode
        expectedExitCode = $ExpectedExitCode
        status = $summary["status"]
        expectedStatus = $ExpectedStatus
        outputRoot = $outputRoot
        summaryPath = $summaryPath
    })
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\ota-canary-report-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$failures = New-Object System.Collections.ArrayList
$caseResults = New-Object System.Collections.ArrayList

Invoke-OtaCase -Name "no-report-pending" -Snapshot (New-AdminOtaSnapshot) -ExpectedExitCode 0 -ExpectedStatus "PENDING" -AllowPending
foreach ($acceptedStatus in @("verified", "installed", "reported")) {
    Invoke-OtaCase -Name "accepted-$acceptedStatus" -Snapshot (New-AdminOtaSnapshot -ReportStatus $acceptedStatus) -ExpectedExitCode 0 -ExpectedStatus "PASS"
}
Invoke-OtaCase -Name "recoverable-failure" -Snapshot (New-AdminOtaSnapshot -ReportStatus "install_failed" -ReportNote "manual install required by system installer") -ExpectedExitCode 0 -ExpectedStatus "RECOVERABLE_FAILURE"
Invoke-OtaCase -Name "hard-failure" -Snapshot (New-AdminOtaSnapshot -ReportStatus "install_failed" -ReportNote "signature mismatch") -ExpectedExitCode 1 -ExpectedStatus "FAIL"
Invoke-OtaCase -Name "wrong-release-version" -Snapshot (New-AdminOtaSnapshot -VersionCode 2026079999) -ExpectedExitCode 1 -ExpectedStatus "FAIL"

$status = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $runRoot
    caseCount = $caseResults.Count
    failureCount = $failures.Count
    failures = @($failures)
    cases = @($caseResults)
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "ota-canary-report-test.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$runRoot
caseCount=$($caseResults.Count)
failureCount=$($failures.Count)
failures=$($failures -join "; ")
"@
Write-TextFile -Path (Join-Path $runRoot "summary.txt") -Content $summary
Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
