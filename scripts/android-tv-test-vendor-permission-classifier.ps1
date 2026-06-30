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

function New-BaseVendorPermission {
    return [ordered]@{
        date = "2026-07-01"
        vendorContact = "vendor-classifier-regression"
        deviceModel = "RK3128-regression"
        firmwareVersion = "factory-fw-regression"
        buildFingerprint = "openclaw/vendor/regression"
        apkOnlyFreshInstallOk = "yes"
        apkOnlyDefaultHomePersists = "no"
        apkOnlyColdBootHomeOk = "no"
        openclawPrivAppSupported = "no"
        defaultHomeFirmwareSupported = "no"
        installPackagesWhitelisted = "no"
        bootCompletedWhitelisted = "no"
        restoreFactoryPreservesOpenClaw = "no"
        restoreFactoryReinstallsOpenClaw = "yes"
        leboWhitelisted = "yes"
        vendorCastingReplacementAvailable = "no"
        noAdbLogExportAvailable = "yes"
        systemOtaPathAvailable = "no"
        factoryProvisioningToolAvailable = "no"
        vendorApiAvailable = "no"
        evidencePath = "evidence/vendor.txt"
        notes = "vendor permission classifier regression fixture"
        vendorDecision = ""
    }
}

function New-VendorPermission {
    param([hashtable]$Overrides = @{})
    $value = New-BaseVendorPermission
    foreach ($key in $Overrides.Keys) {
        $value[$key] = $Overrides[$key]
    }
    return $value
}

function Invoke-VendorCase {
    param(
        [string]$Name,
        [object]$Feedback,
        [int]$ExpectedExitCode,
        [string]$ExpectedDecision,
        [int]$MinimumEvidencePathIssues = 0,
        [switch]$FailOnIncomplete,
        [switch]$SkipEvidenceFile
    )

    $caseRoot = Join-Path $runRoot $Name
    $evidenceDir = Join-Path $caseRoot "evidence"
    New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
    if (-not $SkipEvidenceFile) {
        Write-TextFile -Path (Join-Path $evidenceDir "vendor.txt") -Content "vendor permission classifier regression evidence"
    }

    $feedbackPath = Join-Path $caseRoot "vendor-permission.json"
    Write-JsonFile -Path $feedbackPath -Value $Feedback
    $outputRoot = Join-Path $caseRoot "out"

    $args = @(
        "-FeedbackPath", $feedbackPath,
        "-OutputRoot", $outputRoot,
        "-EvidenceRoot", $caseRoot,
        "-RequireEvidenceRoot"
    )
    if ($FailOnIncomplete) {
        $args += "-FailOnIncomplete"
    }

    $scriptOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-classify-vendor-permission.ps1") @args 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $caseRoot "stdout.txt") -Content (($scriptOutput | Out-String).Trim())

    $summaryPath = Join-Path $outputRoot "summary.txt"
    $summary = Get-SummaryMap -Path $summaryPath
    Assert-Equal -Failures $failures -CaseName $Name -Name "exitCode" -Expected $ExpectedExitCode -Actual $exitCode
    Assert-Equal -Failures $failures -CaseName $Name -Name "recommendedDecision" -Expected $ExpectedDecision -Actual $summary["recommendedDecision"]
    Assert-AtLeast -Failures $failures -CaseName $Name -Name "evidencePathIssueCount" -Minimum $MinimumEvidencePathIssues -Actual $summary["evidencePathIssueCount"]

    [void]$caseResults.Add([pscustomobject]@{
        name = $Name
        exitCode = $exitCode
        expectedExitCode = $ExpectedExitCode
        recommendedDecision = $summary["recommendedDecision"]
        expectedDecision = $ExpectedDecision
        evidencePathIssueCount = $summary["evidencePathIssueCount"]
        outputRoot = $outputRoot
        summaryPath = $summaryPath
    })
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\vendor-permission-classifier-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$failures = New-Object System.Collections.ArrayList
$caseResults = New-Object System.Collections.ArrayList

Invoke-VendorCase -Name "apk-only-acceptable" -Feedback (New-VendorPermission -Overrides @{
    apkOnlyDefaultHomePersists = "yes"
    apkOnlyColdBootHomeOk = "yes"
    installPackagesWhitelisted = "yes"
    bootCompletedWhitelisted = "yes"
    restoreFactoryPreservesOpenClaw = "yes"
    restoreFactoryReinstallsOpenClaw = "no"
    systemOtaPathAvailable = "yes"
    vendorDecision = "APK-only acceptable"
}) -ExpectedExitCode 0 -ExpectedDecision "APK-only acceptable"

Invoke-VendorCase -Name "factory-provisioning-required" -Feedback (New-VendorPermission -Overrides @{
    restoreFactoryReinstallsOpenClaw = "no"
    factoryProvisioningToolAvailable = "yes"
    vendorDecision = "factory provisioning required"
}) -ExpectedExitCode 0 -ExpectedDecision "factory provisioning required"

Invoke-VendorCase -Name "system-image-preinstall-required" -Feedback (New-VendorPermission -Overrides @{
    openclawPrivAppSupported = "yes"
    defaultHomeFirmwareSupported = "yes"
    installPackagesWhitelisted = "yes"
    bootCompletedWhitelisted = "yes"
    vendorDecision = "system image preinstall required"
}) -ExpectedExitCode 0 -ExpectedDecision "system image preinstall required"

Invoke-VendorCase -Name "vendor-api-required" -Feedback (New-VendorPermission -Overrides @{
    vendorApiAvailable = "yes"
    vendorDecision = "vendor API required"
}) -ExpectedExitCode 0 -ExpectedDecision "vendor API required"

Invoke-VendorCase -Name "blocked-no-default-home" -Feedback (New-VendorPermission -Overrides @{
    apkOnlyColdBootHomeOk = "yes"
    vendorDecision = "blocked"
}) -ExpectedExitCode 0 -ExpectedDecision "blocked"

Invoke-VendorCase -Name "incomplete-unknown-template" -Feedback (Get-Content -Raw -LiteralPath (Join-Path $repoRoot "docs\ops\templates\android-tv-vendor-system-permission.template.json") | ConvertFrom-Json) -ExpectedExitCode 2 -ExpectedDecision "INCOMPLETE" -FailOnIncomplete

Invoke-VendorCase -Name "missing-evidence-path" -Feedback (New-VendorPermission -Overrides @{
    apkOnlyDefaultHomePersists = "yes"
    apkOnlyColdBootHomeOk = "yes"
    restoreFactoryPreservesOpenClaw = "yes"
    restoreFactoryReinstallsOpenClaw = "no"
    evidencePath = "evidence/missing-vendor.txt"
    vendorDecision = "APK-only acceptable"
}) -ExpectedExitCode 0 -ExpectedDecision "INCOMPLETE" -MinimumEvidencePathIssues 1 -SkipEvidenceFile

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
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "vendor-permission-classifier-test.json") -Encoding utf8

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
