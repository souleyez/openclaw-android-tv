param(
    [string]$FactoryFeedbackPath = "",
    [string]$FactoryFeedbackEvidenceRoot = "",
    [string]$VendorPermissionPath = "",
    [string]$VendorPermissionEvidenceRoot = "",
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
    if (-not [string]::IsNullOrWhiteSpace($FactoryFeedbackEvidenceRoot)) {
        $gateArgs += @("-FactoryFeedbackEvidenceRoot", (Resolve-Path -Path $FactoryFeedbackEvidenceRoot).Path)
    }
    if (-not [string]::IsNullOrWhiteSpace($VendorPermissionPath)) {
        $gateArgs += @("-VendorPermissionPath", (Resolve-Path -Path $VendorPermissionPath).Path)
    }
    if (-not [string]::IsNullOrWhiteSpace($VendorPermissionEvidenceRoot)) {
        $gateArgs += @("-VendorPermissionEvidenceRoot", (Resolve-Path -Path $VendorPermissionEvidenceRoot).Path)
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

$requiredGateNames = @(
    "factory apk hash",
    "ota apk hash",
    "next apk candidate hash",
    "factory apk signature",
    "ota apk signature",
    "next apk candidate signature",
    "factory handoff export",
    "production services",
    "production readiness ledger",
    "ota installed report",
    "adb online device",
    "home deployment",
    "factory fresh feedback",
    "vendor permission decision"
)
$passableStatuses = @{ PASS = $true }
$gateJsonStatus = ""
$missingRequiredGates = @()
$requiredGateChecks = @()
$blockingGates = @()
$failedGates = @()
if (Test-Path -LiteralPath $gateJsonPath) {
    $gateJson = Get-Content -Raw -LiteralPath $gateJsonPath | ConvertFrom-Json
    $gateJsonStatus = [string]$gateJson.status
    if (-not [string]::IsNullOrWhiteSpace($gateStatus) -and -not [string]::IsNullOrWhiteSpace($gateJsonStatus) -and $gateStatus -ne $gateJsonStatus) {
        $failedGates += [pscustomobject]@{
            name = "factory pilot gate status consistency"
            status = "FAIL"
            detail = "summaryStatus=$gateStatus; jsonStatus=$gateJsonStatus"
            evidencePath = $gateJsonPath
        }
    }

    foreach ($requiredGateName in $requiredGateNames) {
        $gate = @($gateJson.gates | Where-Object { $_.name -eq $requiredGateName } | Select-Object -First 1)
        if ($gate.Count -eq 0) {
            $missingRequiredGates += $requiredGateName
            $failedGates += [pscustomobject]@{
                name = $requiredGateName
                status = "FAIL"
                detail = "required gate missing"
                evidencePath = $gateJsonPath
            }
            continue
        }

        $gateStatusValue = [string]$gate[0].status
        $requiredGateChecks += [pscustomobject]@{
            name = $requiredGateName
            status = $gateStatusValue
            detail = $gate[0].detail
            evidencePath = $gate[0].evidencePath
        }
        if ($passableStatuses.ContainsKey($gateStatusValue)) {
            continue
        }
        if ($gateStatusValue -eq "FAIL") {
            $failedGates += [pscustomobject]@{
                name = $gate[0].name
                status = $gateStatusValue
                detail = $gate[0].detail
                evidencePath = $gate[0].evidencePath
            }
        } else {
            $blockingGates += [pscustomobject]@{
                name = $gate[0].name
                status = $gateStatusValue
                detail = $gate[0].detail
                evidencePath = $gate[0].evidencePath
            }
        }
    }
} else {
    $failedGates += [pscustomobject]@{
        name = "factory pilot gate evidence"
        status = "FAIL"
        detail = "missing factory-pilot-gates.json"
        evidencePath = $gateJsonPath
    }
}

$allRequiredGatesPass = $requiredGateChecks.Count -eq $requiredGateNames.Count -and $missingRequiredGates.Count -eq 0 -and $blockingGates.Count -eq 0 -and $failedGates.Count -eq 0

$status = if ($gateStatus -eq "PASS" -and $allRequiredGatesPass) {
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
    factoryFeedbackEvidenceRoot = $FactoryFeedbackEvidenceRoot
    vendorPermissionPath = $VendorPermissionPath
    vendorPermissionEvidenceRoot = $VendorPermissionEvidenceRoot
    gateStatus = $gateStatus
    gateJsonStatus = $gateJsonStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    requiredGateCount = $requiredGateNames.Count
    requiredGateCheckCount = $requiredGateChecks.Count
    missingRequiredGates = $missingRequiredGates
    requiredGateChecks = $requiredGateChecks
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
factoryFeedbackPath=$FactoryFeedbackPath
factoryFeedbackEvidenceRoot=$FactoryFeedbackEvidenceRoot
vendorPermissionPath=$VendorPermissionPath
vendorPermissionEvidenceRoot=$VendorPermissionEvidenceRoot
gateStatus=$gateStatus
gateJsonStatus=$gateJsonStatus
failedCount=$failedCount
pendingCount=$pendingCount
requiredGateCount=$($requiredGateNames.Count)
requiredGateCheckCount=$($requiredGateChecks.Count)
missingRequiredGates=$($missingRequiredGates -join "; ")
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
