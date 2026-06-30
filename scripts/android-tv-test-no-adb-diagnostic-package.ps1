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

function Assert-AtLeast {
    param(
        [System.Collections.ArrayList]$Failures,
        [string]$CaseName,
        [string]$Name,
        [int]$Minimum,
        [object]$Actual
    )
    $number = 0
    if (-not [int]::TryParse([string]$Actual, [ref]$number) -or $number -lt $Minimum) {
        Add-Failure -Failures $Failures -CaseName $CaseName -Message "$Name expected at least $Minimum but got '$Actual'"
    }
}

function New-NoAdbDiagnosticManifest {
    param([hashtable]$Overrides = @{})
    $manifest = [ordered]@{
        schema = "openclaw.android-tv.no-adb-diagnostic.v1"
        collectedAt = "2026-07-01T10:00:00Z"
        collector = "factory-diagnostic-smoke"
        diagnosticSource = "factory_tool"
        deviceModel = "RK3128-smoke"
        firmwareVersion = "factory-fw-smoke"
        buildFingerprint = "openclaw/factory/smoke"
        packageName = "com.openclaw.tv"
        packageVersionCode = "2026062401"
        packageVersionName = "0.1.14"
        wifiSsid = "Soulzy"
        installLogPath = "evidence/factory-return/logs/install.log"
        homeStatePath = "evidence/factory-return/logs/home-state.txt"
        castingLogPath = "evidence/factory-return/logs/casting.txt"
        otaStatePath = "evidence/factory-return/logs/ota-state.txt"
        crashOrAnrLogPath = "evidence/factory-return/logs/crash-anr.txt"
        processSnapshotPath = "evidence/factory-return/logs/processes.txt"
        memorySnapshotPath = "evidence/factory-return/logs/memory.txt"
        notes = "no-ADB diagnostic package regression fixture"
    }
    foreach ($key in $Overrides.Keys) {
        $manifest[$key] = $Overrides[$key]
    }
    return $manifest
}

function New-NoAdbCasePackage {
    param(
        [string]$Root,
        [object]$Manifest,
        [switch]$SkipOtaStateFile
    )
    $logsRoot = Join-Path $Root "evidence\factory-return\logs"
    New-Item -ItemType Directory -Force -Path $logsRoot | Out-Null
    foreach ($file in @(
        "install.log",
        "home-state.txt",
        "casting.txt",
        "ota-state.txt",
        "crash-anr.txt",
        "processes.txt",
        "memory.txt"
    )) {
        if ($SkipOtaStateFile -and $file -eq "ota-state.txt") {
            continue
        }
        Write-TextFile -Path (Join-Path $logsRoot $file) -Content "fake $file no-ADB diagnostic evidence"
    }
    $manifestPath = Join-Path $logsRoot "no-adb-diagnostic-manifest.json"
    Write-JsonFile -Path $manifestPath -Value $Manifest
    return $manifestPath
}

function Invoke-NoAdbCase {
    param(
        [string]$Name,
        [object]$Manifest,
        [int]$ExpectedExitCode,
        [string]$ExpectedStatus,
        [int]$MinimumPathIssues = 0,
        [switch]$SkipOtaStateFile
    )

    $caseRoot = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
    $manifestPath = New-NoAdbCasePackage -Root $caseRoot -Manifest $Manifest -SkipOtaStateFile:$SkipOtaStateFile
    $outputRoot = Join-Path $caseRoot "out"

    $scriptOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-check-no-adb-diagnostic-package.ps1") -ManifestPath $manifestPath -EvidenceRoot $caseRoot -OutputRoot $outputRoot -RequireEvidenceRoot -FailOnIncomplete 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $caseRoot "stdout.txt") -Content (($scriptOutput | Out-String).Trim())

    $summaryPath = Join-Path $outputRoot "summary.txt"
    $summary = Get-SummaryMap -Path $summaryPath
    Assert-Equal -Failures $failures -CaseName $Name -Name "exitCode" -Expected $ExpectedExitCode -Actual $exitCode
    Assert-Equal -Failures $failures -CaseName $Name -Name "status" -Expected $ExpectedStatus -Actual $summary["status"]
    Assert-AtLeast -Failures $failures -CaseName $Name -Name "pathIssueCount" -Minimum $MinimumPathIssues -Actual $summary["pathIssueCount"]

    [void]$caseResults.Add([pscustomobject]@{
        name = $Name
        exitCode = $exitCode
        expectedExitCode = $ExpectedExitCode
        status = $summary["status"]
        expectedStatus = $ExpectedStatus
        pathIssueCount = $summary["pathIssueCount"]
        outputRoot = $outputRoot
        summaryPath = $summaryPath
    })
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\no-adb-diagnostic-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$failures = New-Object System.Collections.ArrayList
$caseResults = New-Object System.Collections.ArrayList

Invoke-NoAdbCase -Name "complete-package" -Manifest (New-NoAdbDiagnosticManifest) -ExpectedExitCode 0 -ExpectedStatus "PASS"
Invoke-NoAdbCase -Name "missing-required-field" -Manifest (New-NoAdbDiagnosticManifest -Overrides @{ collector = "" }) -ExpectedExitCode 2 -ExpectedStatus "INCOMPLETE"
Invoke-NoAdbCase -Name "missing-referenced-file" -Manifest (New-NoAdbDiagnosticManifest) -ExpectedExitCode 2 -ExpectedStatus "INCOMPLETE" -MinimumPathIssues 1 -SkipOtaStateFile
Invoke-NoAdbCase -Name "unsafe-absolute-path" -Manifest (New-NoAdbDiagnosticManifest -Overrides @{ installLogPath = "C:\factory\install.log" }) -ExpectedExitCode 2 -ExpectedStatus "INCOMPLETE" -MinimumPathIssues 1
Invoke-NoAdbCase -Name "path-traversal" -Manifest (New-NoAdbDiagnosticManifest -Overrides @{ homeStatePath = "..\outside.txt" }) -ExpectedExitCode 2 -ExpectedStatus "INCOMPLETE" -MinimumPathIssues 1

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
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "no-adb-diagnostic-test.json") -Encoding utf8

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
