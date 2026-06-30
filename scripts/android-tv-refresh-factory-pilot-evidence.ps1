param(
    [string]$OutputRoot = "",
    [string]$FactoryFeedbackPath = "",
    [string]$VendorPermissionPath = ""
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
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-refresh\refresh-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$handoffCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-export-factory-pilot-handoff.ps1") `
    -Arguments @() `
    -LogPath (Join-Path $outputDir "factory-handoff-export.log")

$latestHandoff = Get-ChildItem -LiteralPath (Join-Path $repoRoot "artifacts\factory-pilot-handoff") -Directory |
    Where-Object { $_.Name -like "handoff-*" -and $_.Name -notlike "*smoke*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$handoffSummaryPath = if ($latestHandoff) { Join-Path $latestHandoff.FullName "summary.txt" } else { "" }
$handoffSummary = Get-SummaryMap -Path $handoffSummaryPath
$handoffStatus = if ($handoffSummary.ContainsKey("status")) { $handoffSummary["status"] } elseif ($handoffCheck.exitCode -eq 0) { "EXPORTED" } else { "FAIL" }
$handoffOutputDir = if ($handoffSummary.ContainsKey("outputDir")) { $handoffSummary["outputDir"] } elseif ($latestHandoff) { $latestHandoff.FullName } else { "" }
$handoffArchivePath = if ($handoffSummary.ContainsKey("archivePath")) { $handoffSummary["archivePath"] } else { "" }

$archiveVerificationOutputRoot = Join-Path $outputDir "factory-handoff-archive-verification"
$archiveVerificationStatus = "FAIL"
$archiveVerificationSha = ""
if (-not [string]::IsNullOrWhiteSpace($handoffArchivePath)) {
    $archiveVerification = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-verify-factory-handoff-archive.ps1") `
        -Arguments @("-ZipPath", $handoffArchivePath, "-OutputRoot", $archiveVerificationOutputRoot) `
        -LogPath (Join-Path $outputDir "factory-handoff-archive-verification.log")
    $archiveVerificationSummary = Get-SummaryMap -Path (Join-Path $archiveVerificationOutputRoot "summary.txt")
    $archiveVerificationStatus = if ($archiveVerificationSummary.ContainsKey("status")) { $archiveVerificationSummary["status"] } elseif ($archiveVerification.exitCode -eq 0) { "PASS" } else { "FAIL" }
    $archiveVerificationSha = if ($archiveVerificationSummary.ContainsKey("zipSha256")) { $archiveVerificationSummary["zipSha256"] } else { "" }
} else {
    $archiveVerification = [pscustomobject]@{
        exitCode = 1
        output = "missing handoff archive path"
    }
    Write-TextFile -Path (Join-Path $outputDir "factory-handoff-archive-verification.log") -Content $archiveVerification.output
}

$gateOutputRoot = Join-Path $outputDir "factory-pilot-gate"
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
$gateSummary = Get-SummaryMap -Path (Join-Path $gateOutputRoot "summary.txt")
$gateStatus = if ($gateSummary.ContainsKey("status")) { $gateSummary["status"] } elseif ($gateCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$gateFailedCount = if ($gateSummary.ContainsKey("failedCount")) { $gateSummary["failedCount"] } else { "" }
$gatePendingCount = if ($gateSummary.ContainsKey("pendingCount")) { $gateSummary["pendingCount"] } else { "" }

$expansionOutputRoot = Join-Path $outputDir "factory-pilot-expansion"
$expansionArgs = @("-OutputRoot", $expansionOutputRoot, "-AllowBlocked", "-ExistingGateRoot", $gateOutputRoot)
if (-not [string]::IsNullOrWhiteSpace($FactoryFeedbackPath)) {
    $expansionArgs += @("-FactoryFeedbackPath", (Resolve-Path -Path $FactoryFeedbackPath).Path)
}
if (-not [string]::IsNullOrWhiteSpace($VendorPermissionPath)) {
    $expansionArgs += @("-VendorPermissionPath", (Resolve-Path -Path $VendorPermissionPath).Path)
}
$expansionCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-factory-pilot-expansion-readiness.ps1") `
    -Arguments $expansionArgs `
    -LogPath (Join-Path $outputDir "factory-pilot-expansion.log")
$expansionSummary = Get-SummaryMap -Path (Join-Path $expansionOutputRoot "summary.txt")
$expansionStatus = if ($expansionSummary.ContainsKey("status")) { $expansionSummary["status"] } elseif ($expansionCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$expansionDecision = if ($expansionSummary.ContainsKey("decision")) { $expansionSummary["decision"] } else { "" }
$expansionBlockingGates = if ($expansionSummary.ContainsKey("blockingGates")) { $expansionSummary["blockingGates"] } else { "" }

$planAuditOutputRoot = Join-Path $outputDir "factory-pilot-plan-audit"
$planAudit = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-audit-factory-pilot-plan.ps1") `
    -Arguments @("-RefreshRoot", $outputDir, "-OutputRoot", $planAuditOutputRoot, "-AllowIncomplete") `
    -LogPath (Join-Path $outputDir "factory-pilot-plan-audit.log")
$planAuditSummary = Get-SummaryMap -Path (Join-Path $planAuditOutputRoot "summary.txt")
$planAuditStatus = if ($planAuditSummary.ContainsKey("status")) { $planAuditSummary["status"] } elseif ($planAudit.exitCode -eq 0) { "PASS" } else { "FAIL" }
$planAuditPendingCount = if ($planAuditSummary.ContainsKey("pendingCount")) { $planAuditSummary["pendingCount"] } else { "" }

$childFailures = @()
if ($handoffCheck.exitCode -ne 0) {
    $childFailures += "handoff export exit=$($handoffCheck.exitCode)"
}
if ($archiveVerification.exitCode -ne 0) {
    $childFailures += "handoff archive verification exit=$($archiveVerification.exitCode)"
}
if ($gateCheck.exitCode -ne 0) {
    $childFailures += "factory pilot gate exit=$($gateCheck.exitCode)"
}
if ($expansionCheck.exitCode -ne 0) {
    $childFailures += "factory pilot expansion exit=$($expansionCheck.exitCode)"
}
if ($planAudit.exitCode -ne 0 -and -not ($planAudit.exitCode -eq 2 -and $planAuditStatus -eq "INCOMPLETE")) {
    $childFailures += "factory pilot plan audit exit=$($planAudit.exitCode)"
}

$status = if ($childFailures.Count -gt 0 -or $handoffStatus -ne "EXPORTED" -or $gateStatus -eq "FAIL" -or $expansionStatus -eq "FAIL" -or $planAuditStatus -eq "FAIL") {
    "FAIL"
} elseif ($expansionStatus -eq "PASS" -and $gateStatus -eq "PASS" -and $planAuditStatus -eq "PASS") {
    "PASS"
} else {
    "BLOCKED"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    factoryFeedbackPath = $FactoryFeedbackPath
    vendorPermissionPath = $VendorPermissionPath
    handoffStatus = $handoffStatus
    handoffOutputDir = $handoffOutputDir
    handoffArchivePath = $handoffArchivePath
    archiveVerificationStatus = $archiveVerificationStatus
    archiveVerificationSha256 = $archiveVerificationSha
    gateStatus = $gateStatus
    gateFailedCount = $gateFailedCount
    gatePendingCount = $gatePendingCount
    expansionStatus = $expansionStatus
    expansionDecision = $expansionDecision
    expansionBlockingGates = $expansionBlockingGates
    planAuditStatus = $planAuditStatus
    planAuditPendingCount = $planAuditPendingCount
    childFailures = $childFailures
    evidence = [pscustomobject]@{
        handoff = $handoffOutputDir
        handoffArchiveVerification = $archiveVerificationOutputRoot
        factoryPilotGate = $gateOutputRoot
        factoryPilotExpansion = $expansionOutputRoot
        factoryPilotPlanAudit = $planAuditOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-evidence-refresh.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$outputDir
handoffStatus=$handoffStatus
handoffOutputDir=$handoffOutputDir
handoffArchivePath=$handoffArchivePath
archiveVerificationStatus=$archiveVerificationStatus
archiveVerificationSha256=$archiveVerificationSha
gateStatus=$gateStatus
gateFailedCount=$gateFailedCount
gatePendingCount=$gatePendingCount
expansionStatus=$expansionStatus
expansionDecision=$expansionDecision
expansionBlockingGates=$expansionBlockingGates
planAuditStatus=$planAuditStatus
planAuditPendingCount=$planAuditPendingCount
childFailures=$($childFailures -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
