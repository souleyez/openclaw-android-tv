param(
    [string]$RefreshRoot = "",
    [string]$OutputRoot = "",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [int]$ExpectedTargetVersionCode = 2026070101,
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
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

function Test-OperatorOtaSnapshot {
    param(
        [string]$ProductionServicesPath,
        [string]$ExpectedOtaReleaseId,
        [string]$TargetDeviceUuid,
        [int]$ExpectedTargetVersionCode,
        [string]$ExpectedOtaApkSha256
    )

    if ([string]::IsNullOrWhiteSpace($ProductionServicesPath)) {
        return [pscustomobject]@{
            ok = $false
            status = "FAIL"
            evidencePath = "operator-ota-snapshot\target-ota-report.json"
            detail = "operator OTA snapshot root missing"
        }
    }

    $snapshotPath = Join-Path $ProductionServicesPath "operator-ota-snapshot\target-ota-report.json"
    if (-not (Test-Path -LiteralPath $snapshotPath)) {
        return [pscustomobject]@{
            ok = $false
            status = "FAIL"
            evidencePath = $snapshotPath
            detail = "operator OTA snapshot file missing"
        }
    }

    try {
        $snapshot = Get-Content -Raw -LiteralPath $snapshotPath | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{
            ok = $false
            status = "FAIL"
            evidencePath = $snapshotPath
            detail = "operator OTA snapshot invalid JSON: $($_.Exception.Message)"
        }
    }

    $issues = @()
    $status = [string]$snapshot.status
    $releaseId = [string]$snapshot.release.id
    $target = [string]$snapshot.targetDeviceUuid
    $versionCode = [int]$snapshot.release.versionCode
    $artifactSha = ([string]$snapshot.release.artifactSha256).ToLowerInvariant()
    $targetScope = [string]$snapshot.release.targetScope
    $acceptedSnapshotStatuses = @{ PASS = $true; PENDING = $true; RECOVERABLE_FAILURE = $true }

    if (-not $acceptedSnapshotStatuses.ContainsKey($status)) {
        $issues += "status=$status"
    }
    if ($releaseId -ne $ExpectedOtaReleaseId) {
        $issues += "releaseId=$releaseId"
    }
    if ($target -ne $TargetDeviceUuid) {
        $issues += "targetDeviceUuid=$target"
    }
    if ($versionCode -ne $ExpectedTargetVersionCode) {
        $issues += "versionCode=$versionCode"
    }
    if ($artifactSha -ne $ExpectedOtaApkSha256.ToLowerInvariant()) {
        $issues += "artifactSha256=$artifactSha"
    }
    if ($targetScope -ne "deviceUuid:$TargetDeviceUuid") {
        $issues += "targetScope=$targetScope"
    }

    $detail = "operatorSnapshotStatus=$status; releaseId=$releaseId; targetDeviceUuid=$target; versionCode=$versionCode; artifactSha256=$artifactSha; targetScope=$targetScope"
    if ($issues.Count -gt 0) {
        $detail = "$detail; issues=$($issues -join ',')"
    }

    return [pscustomobject]@{
        ok = $issues.Count -eq 0
        status = if ($issues.Count -eq 0) { "PASS" } else { "FAIL" }
        evidencePath = $snapshotPath
        detail = $detail
    }
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
$factoryApkSignatureGate = Get-Gate -GateJson $gateJson -Name "factory apk signature"
$otaApkSignatureGate = Get-Gate -GateJson $gateJson -Name "ota apk signature"
$handoffGate = Get-Gate -GateJson $gateJson -Name "factory handoff export"
$productionGate = Get-Gate -GateJson $gateJson -Name "production services"
$homeDeploymentGate = Get-Gate -GateJson $gateJson -Name "home deployment"
$factoryFeedbackGate = Get-Gate -GateJson $gateJson -Name "factory fresh feedback"
$otaInstalledGate = Get-Gate -GateJson $gateJson -Name "ota installed report"
$readinessLedgerGate = Get-Gate -GateJson $gateJson -Name "production readiness ledger"
$operatorOtaSnapshot = Test-OperatorOtaSnapshot `
    -ProductionServicesPath $productionGate.evidencePath `
    -ExpectedOtaReleaseId $ExpectedOtaReleaseId `
    -TargetDeviceUuid $TargetDeviceUuid `
    -ExpectedTargetVersionCode $ExpectedTargetVersionCode `
    -ExpectedOtaApkSha256 $ExpectedOtaApkSha256

$requirements = New-Object System.Collections.ArrayList

$sourceStatus = if ($factoryApkGate.status -eq "PASS" -and $factoryApkSignatureGate.status -eq "PASS" -and $handoffGate.status -eq "PASS" -and (Test-Path -LiteralPath (Join-Path $repoRoot "docs\ops\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md"))) {
    "PASS"
} elseif ($factoryApkGate.status -eq "FAIL" -or $factoryApkSignatureGate.status -eq "FAIL" -or $handoffGate.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "0.1.14 source and SOP are committed" `
    -Status $sourceStatus `
    -Evidence $handoffGate.evidencePath `
    -Detail "factoryApk=$($factoryApkGate.status); factorySignature=$($factoryApkSignatureGate.status); handoff=$($handoffGate.status); sopExists=$(Test-Path -LiteralPath (Join-Path $repoRoot 'docs\ops\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md'))"

Add-Requirement `
    -List $requirements `
    -Name "Factory fresh-machine feedback is recorded" `
    -Status (Convert-GateToRequirementStatus -Gate $factoryFeedbackGate) `
    -Evidence $factoryFeedbackGate.evidencePath `
    -Detail $factoryFeedbackGate.detail

$otaDeliveryStatus = if ($otaApkGate.status -eq "PASS" -and $otaApkSignatureGate.status -eq "PASS" -and $productionGate.status -eq "PASS" -and $operatorOtaSnapshot.ok) {
    "PASS"
} elseif ($otaApkGate.status -eq "FAIL" -or $otaApkSignatureGate.status -eq "FAIL" -or $productionGate.status -eq "FAIL" -or $operatorOtaSnapshot.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "One signed 0.1.15 OTA is delivered via home to one test device" `
    -Status $otaDeliveryStatus `
    -Evidence $operatorOtaSnapshot.evidencePath `
    -Detail "otaApk=$($otaApkGate.status); otaSignature=$($otaApkSignatureGate.status); productionServices=$($productionGate.status); productionDetail=$($productionGate.detail); $($operatorOtaSnapshot.detail)"

Add-Requirement `
    -List $requirements `
    -Name "The device reports installed or a clear recoverable failure" `
    -Status (Convert-GateToRequirementStatus -Gate $otaInstalledGate -PassStatuses @("PASS", "RECOVERABLE_FAILURE")) `
    -Evidence $otaInstalledGate.evidencePath `
    -Detail $otaInstalledGate.detail

$homeOperatorStatus = if ($homeDeploymentGate.status -eq "PASS" -and $productionGate.status -eq "PASS" -and $operatorOtaSnapshot.ok) {
    "PASS"
} elseif ($homeDeploymentGate.status -eq "FAIL" -or $productionGate.status -eq "FAIL" -or $operatorOtaSnapshot.status -eq "FAIL") {
    "FAIL"
} else {
    "PENDING"
}
Add-Requirement `
    -List $requirements `
    -Name "home operator UI can create/check OTA releases without raw JSON edits" `
    -Status $homeOperatorStatus `
    -Evidence $operatorOtaSnapshot.evidencePath `
    -Detail "homeDeployment=$($homeDeploymentGate.status); productionServices=$($productionGate.status); $($operatorOtaSnapshot.detail)"

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
