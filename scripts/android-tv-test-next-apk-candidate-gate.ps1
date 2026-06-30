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

function Assert-GatePass {
    param(
        [System.Collections.ArrayList]$Failures,
        [object]$GateJson,
        [string]$Name,
        [string]$RequiredDetailPattern = ""
    )

    $gate = @($GateJson.gates | Where-Object { $_.name -eq $Name } | Select-Object -First 1)
    if ($gate.Count -eq 0) {
        Add-Failure -Failures $Failures -Message "missing gate: $Name"
        return
    }
    if ([string]$gate[0].status -ne "PASS") {
        Add-Failure -Failures $Failures -Message "$Name status expected PASS but got $($gate[0].status): $($gate[0].detail)"
    }
    if (-not [string]::IsNullOrWhiteSpace($RequiredDetailPattern) -and [string]$gate[0].detail -notmatch $RequiredDetailPattern) {
        Add-Failure -Failures $Failures -Message "$Name detail missing pattern '$RequiredDetailPattern': $($gate[0].detail)"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\next-apk-candidate-gate-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$gateRoot = Join-Path $runRoot "factory-pilot-gate"
$failures = New-Object System.Collections.ArrayList

$gateArgs = @(
    "-OutputRoot", $gateRoot,
    "-SkipHandoffExportCheck",
    "-SkipProductionServicesCheck",
    "-SkipReadinessLedgerCheck",
    "-SkipAdbCheck",
    "-SkipHomeDeploymentCheck",
    "-SkipRemoteCanaryCheck",
    "-AllowPending"
)
$gateOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-check-factory-pilot-gates.ps1") @gateArgs 2>&1
$gateExitCode = $LASTEXITCODE
Write-TextFile -Path (Join-Path $runRoot "factory-pilot-gate.stdout.txt") -Content (($gateOutput | Out-String).Trim())

if ($gateExitCode -ne 0) {
    Add-Failure -Failures $failures -Message "factory gate exitCode expected 0 but got $gateExitCode"
}

$summaryPath = Join-Path $gateRoot "summary.txt"
$summary = Get-SummaryMap -Path $summaryPath
if (-not $summary.ContainsKey("status")) {
    Add-Failure -Failures $failures -Message "factory gate summary missing status"
}

$gateJsonPath = Join-Path $gateRoot "factory-pilot-gates.json"
if (-not (Test-Path -LiteralPath $gateJsonPath)) {
    Add-Failure -Failures $failures -Message "factory gate json missing: $gateJsonPath"
} else {
    $gateJson = Get-Content -Raw -LiteralPath $gateJsonPath | ConvertFrom-Json
    Assert-GatePass -Failures $failures -GateJson $gateJson -Name "next apk candidate hash" -RequiredDetailPattern "2206353e7f653a52ecaab125c91b761b144d275ee2deccda6d4b59c7133385ac"
    Assert-GatePass -Failures $failures -GateJson $gateJson -Name "next apk candidate signature" -RequiredDetailPattern "2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522"
    $signatureEvidence = Join-Path $gateRoot "next-apk-candidate-signature.txt"
    if (-not (Test-Path -LiteralPath $signatureEvidence)) {
        Add-Failure -Failures $failures -Message "next candidate signature evidence missing: $signatureEvidence"
    }
}

$status = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $runRoot
    gateRoot = $gateRoot
    gateExitCode = $gateExitCode
    gateSummaryStatus = if ($summary.ContainsKey("status")) { $summary["status"] } else { "" }
    failureCount = $failures.Count
    failures = @($failures)
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "next-apk-candidate-gate-test.json") -Encoding utf8

$summaryText = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$runRoot
gateRoot=$gateRoot
gateExitCode=$gateExitCode
gateSummaryStatus=$($result.gateSummaryStatus)
failureCount=$($failures.Count)
failures=$($failures -join "; ")
"@
Write-TextFile -Path (Join-Path $runRoot "summary.txt") -Content $summaryText
Write-Host $summaryText.Trim()

if ($status -eq "FAIL") {
    exit 1
}
