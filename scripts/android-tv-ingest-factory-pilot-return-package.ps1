param(
    [Parameter(Mandatory = $true)]
    [string]$ReturnPath,
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

function Select-FeedbackFile {
    param(
        [System.IO.FileInfo[]]$Candidates,
        [string]$LeafName
    )
    if ($Candidates.Count -eq 0) {
        return $null
    }
    $preferred = $Candidates |
        Where-Object { $_.FullName -match "[\\/]+feedback[\\/]+$([regex]::Escape($LeafName))$" } |
        Sort-Object FullName |
        Select-Object -First 1
    if ($preferred) {
        return $preferred
    }
    return $Candidates | Sort-Object FullName | Select-Object -First 1
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-return-intake\return-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path
$returnItem = Resolve-Path -Path $ReturnPath

$inputDir = Join-Path $outputDir "return-package"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

$returnItemInfo = Get-Item -LiteralPath $returnItem.Path
if ($returnItemInfo.PSIsContainer) {
    Get-ChildItem -LiteralPath $returnItem.Path -Force |
        Copy-Item -Destination $inputDir -Recurse -Force
} elseif ($returnItemInfo.Extension -ieq ".zip") {
    Expand-Archive -LiteralPath $returnItem.Path -DestinationPath $inputDir -Force
} else {
    throw "ReturnPath must be a directory or .zip archive: $($returnItem.Path)"
}

$factoryCandidates = @(Get-ChildItem -LiteralPath $inputDir -Recurse -File -Filter "android-tv-factory-feedback.json")
$vendorCandidates = @(Get-ChildItem -LiteralPath $inputDir -Recurse -File -Filter "android-tv-vendor-system-permission.json")
$factoryFeedback = Select-FeedbackFile -Candidates $factoryCandidates -LeafName "android-tv-factory-feedback.json"
$vendorPermission = Select-FeedbackFile -Candidates $vendorCandidates -LeafName "android-tv-vendor-system-permission.json"

$issues = @()
if (-not $factoryFeedback) {
    $issues += "missing android-tv-factory-feedback.json"
}
if (-not $vendorPermission) {
    $issues += "missing android-tv-vendor-system-permission.json"
}

$intakeOutputRoot = Join-Path $outputDir "factory-pilot-intake"
$intakeStatus = "NOT_RUN"
$factoryConclusion = ""
$vendorDecision = ""
$gateStatus = ""
$failedCount = ""
$pendingCount = ""
$childFailures = @()

if ($issues.Count -eq 0) {
    $intakeArgs = @(
        "-FactoryFeedbackPath", $factoryFeedback.FullName,
        "-VendorPermissionPath", $vendorPermission.FullName,
        "-OutputRoot", $intakeOutputRoot
    )
    if ($AllowPending) {
        $intakeArgs += "-AllowPending"
    }
    $intakeCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-ingest-factory-pilot-feedback.ps1") `
        -Arguments $intakeArgs `
        -LogPath (Join-Path $outputDir "factory-pilot-intake.log")
    $intakeSummary = Get-SummaryMap -Path (Join-Path $intakeOutputRoot "summary.txt")
    $intakeStatus = if ($intakeSummary.ContainsKey("status")) { $intakeSummary["status"] } elseif ($intakeCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
    $factoryConclusion = if ($intakeSummary.ContainsKey("factoryConclusion")) { $intakeSummary["factoryConclusion"] } else { "" }
    $vendorDecision = if ($intakeSummary.ContainsKey("vendorDecision")) { $intakeSummary["vendorDecision"] } else { "" }
    $gateStatus = if ($intakeSummary.ContainsKey("gateStatus")) { $intakeSummary["gateStatus"] } else { "" }
    $failedCount = if ($intakeSummary.ContainsKey("failedCount")) { $intakeSummary["failedCount"] } else { "" }
    $pendingCount = if ($intakeSummary.ContainsKey("pendingCount")) { $intakeSummary["pendingCount"] } else { "" }
    if ($intakeCheck.exitCode -ne 0 -and -not ($intakeCheck.exitCode -eq 2 -and $intakeStatus -eq "PENDING")) {
        $childFailures += "factory-pilot-intake exit=$($intakeCheck.exitCode)"
    }
}

$status = if ($issues.Count -gt 0 -or $childFailures.Count -gt 0 -or $intakeStatus -eq "FAIL") {
    "FAIL"
} elseif ($intakeStatus -eq "PASS") {
    "PASS"
} else {
    "PENDING"
}

$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    returnPath = $returnItem.Path
    copiedReturnPackageDir = $inputDir
    factoryFeedbackPath = if ($factoryFeedback) { $factoryFeedback.FullName } else { "" }
    vendorPermissionPath = if ($vendorPermission) { $vendorPermission.FullName } else { "" }
    factoryFeedbackCandidateCount = $factoryCandidates.Count
    vendorPermissionCandidateCount = $vendorCandidates.Count
    intakeStatus = $intakeStatus
    factoryConclusion = $factoryConclusion
    vendorDecision = $vendorDecision
    gateStatus = $gateStatus
    failedCount = $failedCount
    pendingCount = $pendingCount
    issues = $issues
    childFailures = $childFailures
    evidence = [pscustomobject]@{
        factoryPilotIntake = $intakeOutputRoot
    }
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-return-package.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$outputDir
returnPath=$($returnItem.Path)
copiedReturnPackageDir=$inputDir
factoryFeedbackPath=$($result.factoryFeedbackPath)
vendorPermissionPath=$($result.vendorPermissionPath)
factoryFeedbackCandidateCount=$($factoryCandidates.Count)
vendorPermissionCandidateCount=$($vendorCandidates.Count)
intakeStatus=$intakeStatus
factoryConclusion=$factoryConclusion
vendorDecision=$vendorDecision
gateStatus=$gateStatus
failedCount=$failedCount
pendingCount=$pendingCount
issues=$($issues -join "; ")
childFailures=$($childFailures -join "; ")
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
