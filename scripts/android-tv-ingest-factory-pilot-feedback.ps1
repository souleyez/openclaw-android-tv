param(
    [Parameter(Mandatory = $true)]
    [string]$FactoryFeedbackPath,
    [Parameter(Mandatory = $true)]
    [string]$VendorPermissionPath,
    [string]$OutputRoot = "",
    [switch]$AllowPending
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
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-intake\intake-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$factoryFeedback = Resolve-Path -Path $FactoryFeedbackPath
$vendorPermission = Resolve-Path -Path $VendorPermissionPath

$inputDir = Join-Path $outputDir "input"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -LiteralPath $factoryFeedback.Path -Destination (Join-Path $inputDir "android-tv-factory-feedback.json") -Force
Copy-Item -LiteralPath $vendorPermission.Path -Destination (Join-Path $inputDir "android-tv-vendor-system-permission.json") -Force

$factoryOutputRoot = Join-Path $outputDir "factory-feedback"
$factoryCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-factory-feedback.ps1") `
    -Arguments @("-FeedbackPath", $factoryFeedback.Path, "-OutputRoot", $factoryOutputRoot) `
    -LogPath (Join-Path $outputDir "factory-feedback.log")

$vendorOutputRoot = Join-Path $outputDir "vendor-permission"
$vendorCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-vendor-permission.ps1") `
    -Arguments @("-FeedbackPath", $vendorPermission.Path, "-OutputRoot", $vendorOutputRoot) `
    -LogPath (Join-Path $outputDir "vendor-permission.log")

$gateOutputRoot = Join-Path $outputDir "factory-pilot-gate"
$gateArgs = @(
    "-OutputRoot", $gateOutputRoot,
    "-FactoryFeedbackPath", $factoryFeedback.Path,
    "-VendorPermissionPath", $vendorPermission.Path
)
if ($AllowPending) {
    $gateArgs += "-AllowPending"
}
$gateCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-factory-pilot-gates.ps1") `
    -Arguments $gateArgs `
    -LogPath (Join-Path $outputDir "factory-pilot-gate.log")

$factorySummary = Get-SummaryMap -Path (Join-Path $factoryOutputRoot "summary.txt")
$vendorSummary = Get-SummaryMap -Path (Join-Path $vendorOutputRoot "summary.txt")
$gateSummary = Get-SummaryMap -Path (Join-Path $gateOutputRoot "summary.txt")

$factoryConclusion = if ($factorySummary.ContainsKey("recommendedConclusion")) { $factorySummary["recommendedConclusion"] } else { "" }
$factoryAction = if ($factorySummary.ContainsKey("requiredFactoryAction")) { $factorySummary["requiredFactoryAction"] } else { "" }
$vendorDecision = if ($vendorSummary.ContainsKey("recommendedDecision")) { $vendorSummary["recommendedDecision"] } else { "" }
$vendorAction = if ($vendorSummary.ContainsKey("requiredAction")) { $vendorSummary["requiredAction"] } else { "" }
$gateStatus = if ($gateSummary.ContainsKey("status")) { $gateSummary["status"] } else { "FAIL" }
$failedCount = if ($gateSummary.ContainsKey("failedCount")) { $gateSummary["failedCount"] } else { "" }
$pendingCount = if ($gateSummary.ContainsKey("pendingCount")) { $gateSummary["pendingCount"] } else { "" }

$childFailures = @()
if ($factoryCheck.exitCode -ne 0) {
    $childFailures += "factory-feedback exit=$($factoryCheck.exitCode)"
}
if ($vendorCheck.exitCode -ne 0) {
    $childFailures += "vendor-permission exit=$($vendorCheck.exitCode)"
}
if ($gateCheck.exitCode -ne 0 -and -not ($gateCheck.exitCode -eq 2 -and $gateStatus -eq "PENDING")) {
    $childFailures += "factory-pilot-gate exit=$($gateCheck.exitCode)"
}

$overallStatus = if ($childFailures.Count -gt 0) {
    "FAIL"
} elseif ($gateStatus -eq "FAIL") {
    "FAIL"
} elseif ($gateStatus -eq "PENDING") {
    "PENDING"
} elseif ($gateStatus -eq "PASS") {
    "PASS"
} else {
    "FAIL"
}

$result = [pscustomobject]@{
    status = $overallStatus
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    factoryFeedbackPath = $factoryFeedback.Path
    vendorPermissionPath = $vendorPermission.Path
    copiedInputDir = $inputDir
    factoryConclusion = $factoryConclusion
    factoryAction = $factoryAction
    vendorDecision = $vendorDecision
    vendorAction = $vendorAction
    gateStatus = $gateStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    childFailures = $childFailures
    evidence = [pscustomobject]@{
        factoryFeedback = $factoryOutputRoot
        vendorPermission = $vendorOutputRoot
        factoryPilotGate = $gateOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-intake.json") -Encoding utf8

$summary = @"
status=$overallStatus
checkedAt=$($result.checkedAt)
outputDir=$outputDir
factoryFeedbackPath=$($factoryFeedback.Path)
vendorPermissionPath=$($vendorPermission.Path)
factoryConclusion=$factoryConclusion
factoryAction=$factoryAction
vendorDecision=$vendorDecision
vendorAction=$vendorAction
gateStatus=$gateStatus
failedCount=$failedCount
pendingCount=$pendingCount
childFailures=$($childFailures -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($overallStatus -eq "FAIL") {
    exit 1
}
if ($overallStatus -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
