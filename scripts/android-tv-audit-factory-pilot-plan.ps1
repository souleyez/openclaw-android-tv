param(
    [string]$RefreshRoot = "",
    [string]$OutputRoot = "",
    [switch]$AllowIncomplete
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

function Add-Requirement {
    param(
        [System.Collections.ArrayList]$List,
        [string]$Name,
        [string]$Status,
        [string]$Evidence,
        [string]$Detail
    )
    [void]$List.Add([pscustomobject]@{
        name = $Name
        status = $Status
        evidence = $Evidence
        detail = $Detail
    })
}

function Get-Gate {
    param(
        [object]$GateJson,
        [string]$Name
    )
    $gate = @($GateJson.gates | Where-Object { $_.name -eq $Name } | Select-Object -First 1)
    if ($gate.Count -eq 0) {
        return [pscustomobject]@{
            name = $Name
            status = "MISSING"
            detail = "gate not found"
            evidencePath = ""
        }
    }
    return $gate[0]
}

function Convert-GateToRequirementStatus {
    param(
        [object]$Gate,
        [string[]]$PassStatuses = @("PASS")
    )
    if ($PassStatuses -contains $Gate.status) {
        return "PASS"
    }
    if ($Gate.status -eq "FAIL" -or $Gate.status -eq "MISSING") {
        return "FAIL"
    }
    return "PENDING"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($RefreshRoot)) {
    $latestRefresh = Get-ChildItem -LiteralPath (Join-Path $repoRoot "artifacts\factory-pilot-refresh") -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "refresh-*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latestRefresh) {
        throw "No factory pilot refresh evidence found. Run scripts\android-tv-refresh-factory-pilot-evidence.ps1 first."
    }
    $RefreshRoot = $latestRefresh.FullName
}
$refreshDir = Resolve-Path -Path $RefreshRoot

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-plan-audits\plan-audit-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$refreshSummary = Get-SummaryMap -Path (Join-Path $refreshDir.Path "summary.txt")
$gateJsonPath = Join-Path $refreshDir.Path "factory-pilot-gate\factory-pilot-gates.json"
$expansionSummaryPath = Join-Path $refreshDir.Path "factory-pilot-expansion\summary.txt"

if (-not (Test-Path -LiteralPath $gateJsonPath)) {
    throw "Refresh evidence is missing factory-pilot-gates.json: $gateJsonPath"
}
$gateJson = Get-Content -Raw -LiteralPath $gateJsonPath | ConvertFrom-Json
$expansionSummary = Get-SummaryMap -Path $expansionSummaryPath

$factoryApkGate = Get-Gate -GateJson $gateJson -Name "factory apk hash"
$otaApkGate = Get-Gate -GateJson $gateJson -Name "ota apk hash"
$handoffGate = Get-Gate -GateJson $gateJson -Name "factory handoff export"
$productionGate = Get-Gate -GateJson $gateJson -Name "production services"
$homeDeploymentGate = Get-Gate -GateJson $gateJson -Name "home deployment"
$factoryFeedbackGate = Get-Gate -GateJson $gateJson -Name "factory fresh feedback"
$otaInstalledGate = Get-Gate -GateJson $gateJson -Name "ota installed report"
$readinessLedgerGate = Get-Gate -GateJson $gateJson -Name "production readiness ledger"

$requirements = New-Object System.Collections.ArrayList

$sourceStatus = if ($factoryApkGate.status -eq "PASS" -and $handoffGate.status -eq "PASS" -and (Test-Path -LiteralPath (Join-Path $repoRoot "docs\ops\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md"))) {
    "PASS"
} elseif ($factoryApkGate.status -eq "FAIL" -or $handoffGate.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "0.1.14 source and SOP are committed" `
    -Status $sourceStatus `
    -Evidence $handoffGate.evidencePath `
    -Detail "factoryApk=$($factoryApkGate.status); handoff=$($handoffGate.status); sopExists=$(Test-Path -LiteralPath (Join-Path $repoRoot 'docs\ops\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md'))"

Add-Requirement `
    -List $requirements `
    -Name "Factory fresh-machine feedback is recorded" `
    -Status (Convert-GateToRequirementStatus -Gate $factoryFeedbackGate) `
    -Evidence $factoryFeedbackGate.evidencePath `
    -Detail $factoryFeedbackGate.detail

$otaDeliveryStatus = if ($otaApkGate.status -eq "PASS" -and $productionGate.status -eq "PASS") {
    "PASS"
} elseif ($otaApkGate.status -eq "FAIL" -or $productionGate.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "One signed 0.1.15 OTA is delivered via home to one test device" `
    -Status $otaDeliveryStatus `
    -Evidence $productionGate.evidencePath `
    -Detail "otaApk=$($otaApkGate.status); productionServices=$($productionGate.status); productionDetail=$($productionGate.detail)"

Add-Requirement `
    -List $requirements `
    -Name "The device reports installed or a clear recoverable failure" `
    -Status (Convert-GateToRequirementStatus -Gate $otaInstalledGate) `
    -Evidence $otaInstalledGate.evidencePath `
    -Detail $otaInstalledGate.detail

$homeOperatorStatus = if ($homeDeploymentGate.status -eq "PASS" -and $productionGate.status -eq "PASS") {
    "PASS"
} elseif ($homeDeploymentGate.status -eq "FAIL" -or $productionGate.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "home operator UI can create/check OTA releases without raw JSON edits" `
    -Status $homeOperatorStatus `
    -Evidence $homeDeploymentGate.evidencePath `
    -Detail "homeDeployment=$($homeDeploymentGate.status); productionServices=$($productionGate.status)"

Add-Requirement `
    -List $requirements `
    -Name "A production readiness ledger exists with pass/fail evidence for required rows" `
    -Status (Convert-GateToRequirementStatus -Gate $readinessLedgerGate) `
    -Evidence $readinessLedgerGate.evidencePath `
    -Detail $readinessLedgerGate.detail

$failedRequirements = @($requirements | Where-Object { $_.status -eq "FAIL" })
$pendingRequirements = @($requirements | Where-Object { $_.status -eq "PENDING" })
$expansionStatus = if ($expansionSummary.ContainsKey("status")) { $expansionSummary["status"] } elseif ($refreshSummary.ContainsKey("expansionStatus")) { $refreshSummary["expansionStatus"] } else { "" }
$expansionDecision = if ($expansionSummary.ContainsKey("decision")) { $expansionSummary["decision"] } elseif ($refreshSummary.ContainsKey("expansionDecision")) { $refreshSummary["expansionDecision"] } else { "" }

$status = if ($failedRequirements.Count -gt 0 -or $gateJson.status -eq "FAIL") {
    "FAIL"
} elseif ($pendingRequirements.Count -gt 0 -or $gateJson.status -ne "PASS" -or $expansionStatus -ne "PASS") {
    "INCOMPLETE"
} else {
    "PASS"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    refreshRoot = $refreshDir.Path
    gateStatus = $gateJson.status
    expansionStatus = $expansionStatus
    expansionDecision = $expansionDecision
    requirements = @($requirements)
    failedCount = $failedRequirements.Count
    pendingCount = $pendingRequirements.Count
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-plan-audit.json") -Encoding utf8

$pendingNames = @($pendingRequirements | ForEach-Object { $_.name })
$failedNames = @($failedRequirements | ForEach-Object { $_.name })
$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$outputDir
refreshRoot=$($refreshDir.Path)
gateStatus=$($gateJson.status)
expansionStatus=$expansionStatus
expansionDecision=$expansionDecision
failedCount=$($failedRequirements.Count)
pendingCount=$($pendingRequirements.Count)
pendingRequirements=$($pendingNames -join "; ")
failedRequirements=$($failedNames -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "INCOMPLETE" -and -not $AllowIncomplete) {
    exit 2
}
