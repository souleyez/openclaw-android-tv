param(
    [Parameter(Mandatory = $true)]
    [string]$FeedbackPath,
    [string]$OutputRoot = "",
    [switch]$FailOnIncomplete
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Get-Field {
    param(
        [object]$Object,
        [string]$Name,
        [string]$Fallback = ""
    )
    if ($Object -and $Object.PSObject.Properties.Name -contains $Name) {
        $value = $Object.$Name
        if ($null -ne $value) {
            return ([string]$value).Trim()
        }
    }
    return $Fallback
}

function Normalize-Value {
    param([string]$Value)
    return ([string]$Value).Trim().ToLowerInvariant()
}

function Get-Answer {
    param(
        [object]$Object,
        [string]$Name
    )
    $normalized = Normalize-Value -Value (Get-Field -Object $Object -Name $Name)
    $yesValues = @("yes", "y", "true", "pass", "passed", "ok", "supported", "support", "available", "retain", "retained", "reinstall", "reinstalled")
    $noValues = @("no", "n", "false", "fail", "failed", "unsupported", "not_supported", "unavailable", "deleted", "removed")
    $naValues = @("na", "n/a", "not_applicable", "not applicable")

    if ($yesValues -contains $normalized) {
        return "yes"
    }
    if ($noValues -contains $normalized) {
        return "no"
    }
    if ($naValues -contains $normalized) {
        return "not_applicable"
    }
    return "unknown"
}

function Add-Gate {
    param(
        [System.Collections.ArrayList]$List,
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )
    [void]$List.Add([pscustomobject]@{
        name = $Name
        status = $Status
        detail = $Detail
    })
}

function Test-Yes {
    param([string]$Value)
    return $Value -eq "yes"
}

function Test-No {
    param([string]$Value)
    return $Value -eq "no"
}

function Test-AnyUnknown {
    param([string[]]$Values)
    return @($Values | Where-Object { $_ -eq "unknown" }).Count -gt 0
}

$feedbackFile = Resolve-Path -Path $FeedbackPath
$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\vendor-permissions\classification-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$raw = Get-Content -Raw -Path $feedbackFile
$feedback = $raw | ConvertFrom-Json

$metadataFields = @(
    "date",
    "vendorContact",
    "deviceModel",
    "firmwareVersion",
    "evidencePath"
)

$decisionFields = @(
    "apkOnlyFreshInstallOk",
    "apkOnlyDefaultHomePersists",
    "apkOnlyColdBootHomeOk",
    "openclawPrivAppSupported",
    "defaultHomeFirmwareSupported",
    "installPackagesWhitelisted",
    "bootCompletedWhitelisted",
    "restoreFactoryPreservesOpenClaw",
    "restoreFactoryReinstallsOpenClaw",
    "leboWhitelisted",
    "vendorCastingReplacementAvailable",
    "noAdbLogExportAvailable",
    "systemOtaPathAvailable",
    "factoryProvisioningToolAvailable",
    "vendorApiAvailable"
)

$missingFields = @()
foreach ($field in $metadataFields) {
    if ([string]::IsNullOrWhiteSpace((Get-Field -Object $feedback -Name $field))) {
        $missingFields += $field
    }
}

$answers = [ordered]@{}
$unknownFields = @()
foreach ($field in $decisionFields) {
    $answer = Get-Answer -Object $feedback -Name $field
    $answers[$field] = $answer
    if ($answer -eq "unknown") {
        $unknownFields += $field
    }
}

$freshInstallOk = Test-Yes $answers["apkOnlyFreshInstallOk"]
$apkDefaultHomeOk = Test-Yes $answers["apkOnlyDefaultHomePersists"]
$apkColdBootOk = Test-Yes $answers["apkOnlyColdBootHomeOk"]
$factoryProvisioningOk = Test-Yes $answers["factoryProvisioningToolAvailable"]
$privAppOk = Test-Yes $answers["openclawPrivAppSupported"]
$firmwareHomeOk = Test-Yes $answers["defaultHomeFirmwareSupported"]
$installWhitelistOk = Test-Yes $answers["installPackagesWhitelisted"]
$bootWhitelistOk = Test-Yes $answers["bootCompletedWhitelisted"]
$restorePreserveOk = Test-Yes $answers["restoreFactoryPreservesOpenClaw"]
$restoreReinstallOk = Test-Yes $answers["restoreFactoryReinstallsOpenClaw"]
$leboOk = Test-Yes $answers["leboWhitelisted"]
$vendorCastingOk = Test-Yes $answers["vendorCastingReplacementAvailable"]
$noAdbLogOk = Test-Yes $answers["noAdbLogExportAvailable"]
$systemOtaOk = Test-Yes $answers["systemOtaPathAvailable"]
$vendorApiOk = Test-Yes $answers["vendorApiAvailable"]
$apkOnlyBaselineUnknown = Test-AnyUnknown @($answers["apkOnlyFreshInstallOk"], $answers["apkOnlyDefaultHomePersists"], $answers["apkOnlyColdBootHomeOk"])
$restorePathUnknown = Test-AnyUnknown @($answers["restoreFactoryPreservesOpenClaw"], $answers["restoreFactoryReinstallsOpenClaw"], $answers["openclawPrivAppSupported"], $answers["factoryProvisioningToolAvailable"])
$castingPathUnknown = Test-AnyUnknown @($answers["leboWhitelisted"], $answers["vendorCastingReplacementAvailable"])
$supportLogPathUnknown = Test-AnyUnknown @($answers["noAdbLogExportAvailable"])
$systemPrivilegesUnknown = Test-AnyUnknown @($answers["openclawPrivAppSupported"], $answers["defaultHomeFirmwareSupported"], $answers["installPackagesWhitelisted"], $answers["bootCompletedWhitelisted"], $answers["restoreFactoryPreservesOpenClaw"])
$vendorApiUnknown = Test-AnyUnknown @($answers["vendorApiAvailable"], $answers["systemOtaPathAvailable"])

$defaultHomePathOk = $apkDefaultHomeOk -or $factoryProvisioningOk -or $firmwareHomeOk -or $vendorApiOk
$coldBootPathOk = $apkColdBootOk -or $factoryProvisioningOk -or $firmwareHomeOk -or $vendorApiOk
$restorePathOk = $restorePreserveOk -or $restoreReinstallOk -or $privAppOk -or $factoryProvisioningOk
$castingPathOk = $leboOk -or $vendorCastingOk
$apkOnlyOk = $freshInstallOk -and $apkDefaultHomeOk -and $apkColdBootOk -and $restorePathOk -and $castingPathOk -and $noAdbLogOk
$systemImageSignal = $privAppOk -or $firmwareHomeOk -or $installWhitelistOk -or $bootWhitelistOk -or $restorePreserveOk
$needsProvisioning = $factoryProvisioningOk -and (-not $apkOnlyOk)

$hasCompleteDecisionInput = ($missingFields.Count -eq 0 -and $unknownFields.Count -eq 0)
$hardBlockers = @()
if ($hasCompleteDecisionInput) {
    if (Test-No $answers["apkOnlyFreshInstallOk"]) {
        $hardBlockers += "fresh install is not accepted"
    }
    if (-not $defaultHomePathOk) {
        $hardBlockers += "no persistent default Home path"
    }
    if (-not $coldBootPathOk) {
        $hardBlockers += "no cold boot Home path"
    }
    if (-not $restorePathOk) {
        $hardBlockers += "no restore-factory preserve or reinstall path"
    }
    if (-not $castingPathOk) {
        $hardBlockers += "no Lebo whitelist or vendor casting replacement"
    }
    if (-not $noAdbLogOk) {
        $hardBlockers += "no no-ADB log export path"
    }
}

$gates = New-Object System.Collections.ArrayList
Add-Gate -List $gates -Name "metadata" -Status ($(if ($missingFields.Count -eq 0) { "PASS" } else { "INCOMPLETE" })) -Detail ($missingFields -join ",")
Add-Gate -List $gates -Name "decision fields" -Status ($(if ($unknownFields.Count -eq 0) { "PASS" } else { "INCOMPLETE" })) -Detail ($unknownFields -join ",")
Add-Gate -List $gates -Name "apk-only baseline" -Status ($(if ($apkOnlyBaselineUnknown) { "INCOMPLETE" } elseif ($freshInstallOk -and $apkDefaultHomeOk -and $apkColdBootOk) { "PASS" } else { "FAIL" })) -Detail "freshInstall=$($answers["apkOnlyFreshInstallOk"]) defaultHome=$($answers["apkOnlyDefaultHomePersists"]) coldBoot=$($answers["apkOnlyColdBootHomeOk"])"
Add-Gate -List $gates -Name "restore path" -Status ($(if ($restorePathUnknown) { "INCOMPLETE" } elseif ($restorePathOk) { "PASS" } else { "FAIL" })) -Detail "preserve=$($answers["restoreFactoryPreservesOpenClaw"]) reinstall=$($answers["restoreFactoryReinstallsOpenClaw"]) privApp=$($answers["openclawPrivAppSupported"]) provisioning=$($answers["factoryProvisioningToolAvailable"])"
Add-Gate -List $gates -Name "casting path" -Status ($(if ($castingPathUnknown) { "INCOMPLETE" } elseif ($castingPathOk) { "PASS" } else { "FAIL" })) -Detail "lebo=$($answers["leboWhitelisted"]) vendorReplacement=$($answers["vendorCastingReplacementAvailable"])"
Add-Gate -List $gates -Name "support log path" -Status ($(if ($supportLogPathUnknown) { "INCOMPLETE" } elseif ($noAdbLogOk) { "PASS" } else { "FAIL" })) -Detail "noAdbLogExport=$($answers["noAdbLogExportAvailable"])"
Add-Gate -List $gates -Name "system privileges" -Status ($(if ($systemPrivilegesUnknown) { "INCOMPLETE" } elseif ($systemImageSignal) { "AVAILABLE" } else { "NOT_AVAILABLE" })) -Detail "privApp=$($answers["openclawPrivAppSupported"]) firmwareHome=$($answers["defaultHomeFirmwareSupported"]) installWhitelist=$($answers["installPackagesWhitelisted"]) bootWhitelist=$($answers["bootCompletedWhitelisted"]) restorePreserve=$($answers["restoreFactoryPreservesOpenClaw"])"
Add-Gate -List $gates -Name "vendor api" -Status ($(if ($vendorApiUnknown) { "INCOMPLETE" } elseif ($vendorApiOk) { "AVAILABLE" } else { "NOT_AVAILABLE" })) -Detail "vendorApi=$($answers["vendorApiAvailable"]) systemOta=$($answers["systemOtaPathAvailable"])"

$recommendedDecision = "INCOMPLETE"
$requiredAction = "Complete all required fields and replace unknown values with yes, no, or not_applicable where valid."
$riskFlags = @()

if ($missingFields.Count -gt 0 -or $unknownFields.Count -gt 0) {
    $recommendedDecision = "INCOMPLETE"
} elseif ($hardBlockers.Count -gt 0) {
    $recommendedDecision = "blocked"
    $requiredAction = "Do not expand shipment. Resolve: $($hardBlockers -join '; ')."
} elseif ($apkOnlyOk) {
    $recommendedDecision = "APK-only acceptable"
    $requiredAction = "Enter limited factory pilot with APK-only SOP, one-device OTA canary, and evidence logging."
} elseif ($needsProvisioning) {
    $recommendedDecision = "factory provisioning required"
    $requiredAction = "Add factory install, permission, default Home, cold boot, restore, and log export steps to SOP."
} elseif ($systemImageSignal) {
    $recommendedDecision = "system image preinstall required"
    $requiredAction = "Move OpenClaw identity, default Home, permission whitelist, and restore behavior into the factory image or priv-app path."
} elseif ($vendorApiOk) {
    $recommendedDecision = "vendor API required"
    $requiredAction = "Start vendor service/API integration for the unsupported system capability before volume shipment."
} else {
    $recommendedDecision = "blocked"
    $requiredAction = "No acceptable APK-only, provisioning, system-image, or vendor API path is proven."
}

if (Test-No $answers["installPackagesWhitelisted"]) {
    $riskFlags += "silent APK install may fall back to system installer UI"
}
if (Test-No $answers["systemOtaPathAvailable"]) {
    $riskFlags += "system OTA remains outside home control"
}
if (Test-No $answers["bootCompletedWhitelisted"]) {
    $riskFlags += "boot receiver behavior must be verified per firmware"
}

$vendorDecision = Get-Field -Object $feedback -Name "vendorDecision"
$decisionMatchesVendor = [string]::IsNullOrWhiteSpace($vendorDecision) -or ((Normalize-Value -Value $vendorDecision) -eq (Normalize-Value -Value $recommendedDecision))

$result = [pscustomobject]@{
    status = "classified"
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    feedbackPath = $feedbackFile.Path
    recommendedDecision = $recommendedDecision
    vendorDecision = $vendorDecision
    decisionMatchesVendor = $decisionMatchesVendor
    requiredAction = $requiredAction
    missingFields = $missingFields
    unknownFields = $unknownFields
    hardBlockers = $hardBlockers
    riskFlags = $riskFlags
    answers = $answers
    gates = $gates
}

$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "vendor-permission-classification.json") -Encoding utf8

$summary = @"
status=CLASSIFIED
checkedAt=$($result.checkedAt)
feedbackPath=$($feedbackFile.Path)
recommendedDecision=$recommendedDecision
vendorDecision=$vendorDecision
decisionMatchesVendor=$decisionMatchesVendor
missingFields=$($missingFields -join ",")
unknownFields=$($unknownFields -join ",")
hardBlockers=$($hardBlockers -join "; ")
riskFlags=$($riskFlags -join "; ")
requiredAction=$requiredAction
outputDir=$outputDir
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($FailOnIncomplete -and ($recommendedDecision -eq "INCOMPLETE")) {
    exit 2
}
