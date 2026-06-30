param(
    [string]$FactoryFeedbackPath = "",
    [string]$VendorPermissionPath = "",
    [string]$OutputRoot = "",
    [string]$ExistingGateRoot = "",
    [switch]$AllowBlocked
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

function Invoke-ChildScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$LogPath
    )
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    Write-TextFile -Path $LogPath -Content $text
    return [pscustomobject]@{
        exitCode = $exitCode
        output = $text
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-expansion-checks\expansion-check-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$gateOutputRoot = Join-Path $outputDir "factory-pilot-gate"
if (-not [string]::IsNullOrWhiteSpace($ExistingGateRoot)) {
    $gateOutputRoot = (Resolve-Path -Path $ExistingGateRoot).Path
    $gateCheck = [pscustomobject]@{
        exitCode = 0
        output = "reused existing factory pilot gate evidence: $gateOutputRoot"
    }
    Write-TextFile -Path (Join-Path $outputDir "factory-pilot-gate.log") -Content $gateCheck.output
} else {
    $gateArgs = @("-OutputRoot", $gateOutputRoot, "-AllowPending")
    if (-not [string]::IsNullOrWhiteSpace($FactoryFeedbackPath)) {
        $gateArgs += @("-FactoryFeedbackPath", (Resolve-Path -Path $FactoryFeedbackPath).Path)
    }
    if (-not [string]::IsNullOrWhiteSpace($VendorPermissionPath)) {
        $gateArgs += @("-VendorPermissionPath", (Resolve-Path -Path $VendorPermissionPath).Path)
    }

    $gateCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-factory-pilot-gates.ps1") `
        -Arguments $gateArgs `
        -LogPath (Join-Path $outputDir "factory-pilot-gate.log")
}

$gateSummary = Get-SummaryMap -Path (Join-Path $gateOutputRoot "summary.txt")
$gateJsonPath = Join-Path $gateOutputRoot "factory-pilot-gates.json"
$gateStatus = if ($gateSummary.ContainsKey("status")) { $gateSummary["status"] } elseif ($gateCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$failedCount = if ($gateSummary.ContainsKey("failedCount")) { $gateSummary["failedCount"] } else { "" }
$pendingCount = if ($gateSummary.ContainsKey("pendingCount")) { $gateSummary["pendingCount"] } else { "" }

$blockingGates = @()
$failedGates = @()
if (Test-Path -LiteralPath $gateJsonPath) {
    $gateJson = Get-Content -Raw -LiteralPath $gateJsonPath | ConvertFrom-Json
    $blockingGates = @($gateJson.gates | Where-Object { $_.status -eq "PENDING" -or $_.status -eq "AUTH_REQUIRED" -or $_.status -eq "RECOVERABLE_FAILURE" } | ForEach-Object {
        [pscustomobject]@{
            name = $_.name
            status = $_.status
            detail = $_.detail
            evidencePath = $_.evidencePath
        }
    })
    $failedGates = @($gateJson.gates | Where-Object { $_.status -eq "FAIL" } | ForEach-Object {
        [pscustomobject]@{
            name = $_.name
            status = $_.status
            detail = $_.detail
            evidencePath = $_.evidencePath
        }
    })
}

$status = if ($gateStatus -eq "PASS") {
    "PASS"
} elseif ($gateStatus -eq "FAIL" -or $failedGates.Count -gt 0) {
    "FAIL"
} else {
    "BLOCKED"
}

$decision = if ($status -eq "PASS") {
    "expand_allowed"
} else {
    "do_not_expand"
}

$nextAction = if ($status -eq "PASS") {
    "Expansion may proceed only under the current documented rollout scope and rollback rules."
} elseif ($status -eq "FAIL") {
    "Fix failed gates before any expansion decision."
} else {
    "Keep rollout at factory pilot scope until every pending gate has dated evidence."
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    decision = $decision
    nextAction = $nextAction
    outputDir = $outputDir
    factoryFeedbackPath = $FactoryFeedbackPath
    vendorPermissionPath = $VendorPermissionPath
    gateStatus = $gateStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    blockingGates = $blockingGates
    failedGates = $failedGates
    evidence = [pscustomobject]@{
        factoryPilotGate = $gateOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-expansion-readiness.json") -Encoding utf8

$blockingNames = @($blockingGates | ForEach-Object { "$($_.name)=$($_.status)" })
$failedNames = @($failedGates | ForEach-Object { "$($_.name)=$($_.status)" })
$summary = @"
status=$status
checkedAt=$($result.checkedAt)
decision=$decision
nextAction=$nextAction
outputDir=$outputDir
gateStatus=$gateStatus
failedCount=$failedCount
pendingCount=$pendingCount
blockingGates=$($blockingNames -join "; ")
failedGates=$($failedNames -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "BLOCKED" -and -not $AllowBlocked) {
    exit 2
}
