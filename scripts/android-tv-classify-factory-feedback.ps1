param(
    [Parameter(Mandatory = $true)]
    [string]$FeedbackPath,
    [string]$OutputRoot = "",
    [string]$ExpectedApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [int]$ExpectedVersionCode = 2026062401,
    [bool]$RequiresFactoryResetPersistence = $true,
    [string]$EvidenceRoot = "",
    [switch]$RequireEvidenceRoot,
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

function Test-PassValue {
    param([string]$Value)
    $normalized = Normalize-Value -Value $Value
    return @("pass", "passed", "ok", "true", "yes", "installed", "verified", "reported", "home", "openclaw_home", "retained", "auto_reinstalled") -contains $normalized
}

function Test-UntestedValue {
    param([string]$Value)
    $normalized = Normalize-Value -Value $Value
    return [string]::IsNullOrWhiteSpace($normalized) -or @("not_tested", "untested", "n/a", "na") -contains $normalized
}

function Test-RemovedAfterFactoryReset {
    param([string]$Value)
    $normalized = Normalize-Value -Value $Value
    return @("deleted", "removed", "delete", "remove") -contains $normalized
}

function Test-FirmwareDefaultHome {
    param([string]$Value)
    $normalized = Normalize-Value -Value $Value
    return @("firmware default", "factory image default", "preinstall default", "firmware_default", "system_default", "preinstall_default") -contains $normalized
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

function Test-SkipEvidencePathValue {
    param([string]$Value)
    $normalized = ([string]$Value).Trim().ToLowerInvariant()
    return [string]::IsNullOrWhiteSpace($normalized) -or
        @("not_tested", "untested", "n/a", "na", "none", "unknown") -contains $normalized
}

function Split-EvidencePathValue {
    param([string]$Value)
    return @(([string]$Value -split "[;`r`n]+") |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not (Test-SkipEvidencePathValue -Value $_) })
}

function Test-RelativeEvidencePath {
    param(
        [string]$Root,
        [string]$PathValue
    )
    $raw = ([string]$PathValue).Trim()
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    if ([System.IO.Path]::IsPathRooted($raw) -or $raw -match "^[a-zA-Z][a-zA-Z0-9+.-]*:") {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = ""
            issue = "not a relative evidence path: $raw"
        }
    }

    $candidate = [System.IO.Path]::GetFullPath((Join-Path $Root $raw))
    $insideRoot = $candidate.Equals($rootFull, [System.StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($rootFull + [System.IO.Path]::AltDirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
    if (-not $insideRoot) {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = $candidate
            issue = "path escapes evidence root: $raw"
        }
    }
    if (-not (Test-Path -LiteralPath $candidate)) {
        return [pscustomobject]@{
            ok = $false
            resolvedPath = $candidate
            issue = "referenced evidence path not found: $raw"
        }
    }
    return [pscustomobject]@{
        ok = $true
        resolvedPath = $candidate
        issue = ""
    }
}

$feedbackFile = Resolve-Path -Path $FeedbackPath
$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-feedback\classification-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path
$evidenceRootPath = ""
if (-not [string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $evidenceRootPath = (Resolve-Path -Path $EvidenceRoot).Path
}

$raw = Get-Content -Raw -Path $feedbackFile
$feedback = $raw | ConvertFrom-Json

$requiredFields = @(
    "date",
    "factoryContact",
    "deviceModel",
    "deviceSn",
    "firmwareVersion",
    "androidVersion",
    "buildFingerprint",
    "apkFileName",
    "apkSha256",
    "installMethod",
    "installResult",
    "defaultHomeSettingMethod",
    "defaultHomeResult",
    "resolveActivityOutput",
    "firstLaunchHomeResult",
    "remoteHomeReturnResult",
    "coldBootHomeResult",
    "restoreFactoryApkState",
    "restoreFactoryDefaultHome",
    "iphoneDiscovery",
    "xiaomiDiscovery",
    "castReturnTarget",
    "otaReceived",
    "otaInstallResult",
    "homeReportStatus",
    "logsPath",
    "screenshotOrVideoPath"
)
$strictRequiredFields = @(
    "installMethod",
    "installResult",
    "defaultHomeSettingMethod",
    "defaultHomeResult",
    "resolveActivityOutput",
    "firstLaunchHomeResult",
    "remoteHomeReturnResult",
    "coldBootHomeResult",
    "homeReportStatus",
    "logsPath",
    "screenshotOrVideoPath"
)

$missingFields = @()
foreach ($field in $requiredFields) {
    $fieldValue = Get-Field -Object $feedback -Name $field
    if ([string]::IsNullOrWhiteSpace($fieldValue) -or (($strictRequiredFields -contains $field) -and (Test-UntestedValue -Value $fieldValue))) {
        $missingFields += $field
    }
}

$apkSha = (Get-Field -Object $feedback -Name "apkSha256").ToLowerInvariant()
$expectedSha = $ExpectedApkSha256.ToLowerInvariant()
$installResult = Get-Field -Object $feedback -Name "installResult"
$defaultHomeResult = Get-Field -Object $feedback -Name "defaultHomeResult"
$coldBootResult = Get-Field -Object $feedback -Name "coldBootHomeResult"
$homeSettingMethod = Get-Field -Object $feedback -Name "defaultHomeSettingMethod"
$restoreApkState = Get-Field -Object $feedback -Name "restoreFactoryApkState"
$iphoneDiscovery = Get-Field -Object $feedback -Name "iphoneDiscovery"
$xiaomiDiscovery = Get-Field -Object $feedback -Name "xiaomiDiscovery"
$otaReceived = Get-Field -Object $feedback -Name "otaReceived"
$otaInstallResult = Get-Field -Object $feedback -Name "otaInstallResult"
$homeReportStatus = Get-Field -Object $feedback -Name "homeReportStatus"
$logsPath = Get-Field -Object $feedback -Name "logsPath"
$screenshotOrVideoPath = Get-Field -Object $feedback -Name "screenshotOrVideoPath"

$apkShaMatches = $apkSha -eq $expectedSha
$installPass = Test-PassValue -Value $installResult
$defaultHomePass = Test-PassValue -Value $defaultHomeResult
$coldBootPass = Test-PassValue -Value $coldBootResult
$restoreRemoved = Test-RemovedAfterFactoryReset -Value $restoreApkState
$homeNeedsProvisioning = -not (Test-FirmwareDefaultHome -Value $homeSettingMethod)
$castingDiscoveryPass = (Test-PassValue -Value $iphoneDiscovery) -and (Test-PassValue -Value $xiaomiDiscovery)
$otaPass = (Test-PassValue -Value $otaReceived) -and (Test-PassValue -Value $otaInstallResult) -and (Test-PassValue -Value $homeReportStatus)
$hasLogsPackage = -not [string]::IsNullOrWhiteSpace($logsPath)
$hasScreenshot = -not [string]::IsNullOrWhiteSpace($screenshotOrVideoPath)
$evidencePathChecks = New-Object System.Collections.ArrayList
$evidencePathIssues = @()
if ($RequireEvidenceRoot -and [string]::IsNullOrWhiteSpace($evidenceRootPath)) {
    $evidencePathIssues += "EvidenceRoot is required to validate screenshotOrVideoPath and logsPath"
} elseif (-not [string]::IsNullOrWhiteSpace($evidenceRootPath)) {
    foreach ($field in @("screenshotOrVideoPath", "logsPath")) {
        $fieldValue = if ($field -eq "screenshotOrVideoPath") { $screenshotOrVideoPath } else { $logsPath }
        foreach ($pathValue in (Split-EvidencePathValue -Value $fieldValue)) {
            $pathCheck = Test-RelativeEvidencePath -Root $evidenceRootPath -PathValue $pathValue
            [void]$evidencePathChecks.Add([pscustomobject]@{
                field = $field
                value = $pathValue
                ok = $pathCheck.ok
                resolvedPath = $pathCheck.resolvedPath
                issue = $pathCheck.issue
            })
            if (-not $pathCheck.ok) {
                $evidencePathIssues += "$field`: $($pathCheck.issue)"
            }
        }
    }
}

$gates = New-Object System.Collections.ArrayList
Add-Gate -List $gates -Name "required fields" -Status ($(if ($missingFields.Count -eq 0) { "PASS" } else { "INCOMPLETE" })) -Detail ($missingFields -join ",")
Add-Gate -List $gates -Name "apk sha256" -Status ($(if ($apkShaMatches) { "PASS" } else { "FAIL" })) -Detail "expected=$expectedSha actual=$apkSha"
Add-Gate -List $gates -Name "install" -Status ($(if (Test-UntestedValue -Value $installResult) { "INCOMPLETE" } elseif ($installPass) { "PASS" } else { "FAIL" })) -Detail $installResult
Add-Gate -List $gates -Name "default home" -Status ($(if (Test-UntestedValue -Value $defaultHomeResult) { "INCOMPLETE" } elseif ($defaultHomePass) { "PASS" } else { "FAIL" })) -Detail $defaultHomeResult
Add-Gate -List $gates -Name "cold boot" -Status ($(if (Test-UntestedValue -Value $coldBootResult) { "INCOMPLETE" } elseif ($coldBootPass) { "PASS" } else { "FAIL" })) -Detail $coldBootResult
Add-Gate -List $gates -Name "factory reset" -Status ($(if ($RequiresFactoryResetPersistence -and $restoreRemoved) { "FAIL" } elseif (Test-UntestedValue -Value $restoreApkState) { "PENDING" } else { "PASS" })) -Detail $restoreApkState
Add-Gate -List $gates -Name "casting discovery" -Status ($(if ($castingDiscoveryPass) { "PASS" } elseif ((Test-UntestedValue -Value $iphoneDiscovery) -or (Test-UntestedValue -Value $xiaomiDiscovery)) { "PENDING" } else { "FAIL" })) -Detail "iphone=$iphoneDiscovery xiaomi=$xiaomiDiscovery"
Add-Gate -List $gates -Name "ota canary" -Status ($(if ($otaPass) { "PASS" } elseif ((Test-UntestedValue -Value $otaReceived) -or (Test-UntestedValue -Value $otaInstallResult)) { "PENDING" } else { "FAIL" })) -Detail "received=$otaReceived install=$otaInstallResult homeReport=$homeReportStatus"
Add-Gate -List $gates -Name "logs package" -Status ($(if ($hasLogsPackage) { "PASS" } else { "INCOMPLETE" })) -Detail $logsPath
Add-Gate -List $gates -Name "screenshot/video" -Status ($(if ($hasScreenshot) { "PASS" } else { "INCOMPLETE" })) -Detail $screenshotOrVideoPath
Add-Gate -List $gates -Name "evidence paths" -Status ($(if ($evidencePathIssues.Count -eq 0) { "PASS" } else { "INCOMPLETE" })) -Detail ($evidencePathIssues -join "; ")

$recommendedConclusion = "INCOMPLETE"
$requiredAction = "Complete missing feedback fields and attach screenshot/video plus logs package evidence."

if ($missingFields.Count -gt 0 -or $evidencePathIssues.Count -gt 0) {
    $recommendedConclusion = "INCOMPLETE"
} elseif (-not $apkShaMatches -or -not $installPass) {
    $recommendedConclusion = "BLOCKED A"
    $requiredAction = "Stop expansion; verify APK file, signature lineage, installer permissions, and factory install flow."
} elseif (-not $defaultHomePass -or -not $coldBootPass) {
    $recommendedConclusion = "BLOCKED B"
    $requiredAction = "Require firmware default Home or factory provisioning that persists across cold boot."
} elseif ($RequiresFactoryResetPersistence -and $restoreRemoved) {
    $recommendedConclusion = "BLOCKED C"
    $requiredAction = "Require system image preinstall or restore-time provisioning before volume shipment."
} elseif ($homeNeedsProvisioning -or -not $castingDiscoveryPass -or -not $otaPass -or -not $hasLogsPackage -or -not $hasScreenshot) {
    $recommendedConclusion = "PASS B"
    $requiredAction = "Keep pilot limited; add factory provisioning or complete casting/OTA/screenshot/log evidence before volume shipment."
} else {
    $recommendedConclusion = "PASS A"
    $requiredAction = "Eligible to enter limited factory pilot; keep OTA rollout at one-device scope until installed report is recorded."
}

$factoryConclusion = Get-Field -Object $feedback -Name "factoryConclusion"
$conclusionMatchesFactory = [string]::IsNullOrWhiteSpace($factoryConclusion) -or ((Normalize-Value -Value $factoryConclusion) -eq (Normalize-Value -Value $recommendedConclusion))

$result = [pscustomobject]@{
    status = "ok"
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    feedbackPath = $feedbackFile.Path
    evidenceRootPath = $evidenceRootPath
    expectedVersionCode = $ExpectedVersionCode
    expectedApkSha256 = $expectedSha
    requiresFactoryResetPersistence = $RequiresFactoryResetPersistence
    recommendedConclusion = $recommendedConclusion
    factoryConclusion = $factoryConclusion
    conclusionMatchesFactory = $conclusionMatchesFactory
    requiredFactoryAction = $requiredAction
    missingFields = $missingFields
    evidencePathIssueCount = $evidencePathIssues.Count
    evidencePathIssues = $evidencePathIssues
    evidencePathChecks = @($evidencePathChecks)
    gates = $gates
}

$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-feedback-classification.json") -Encoding utf8

$summary = @"
status=CLASSIFIED
checkedAt=$($result.checkedAt)
feedbackPath=$($feedbackFile.Path)
evidenceRootPath=$evidenceRootPath
recommendedConclusion=$recommendedConclusion
factoryConclusion=$factoryConclusion
conclusionMatchesFactory=$conclusionMatchesFactory
missingFields=$($missingFields -join ",")
evidencePathIssueCount=$($evidencePathIssues.Count)
evidencePathIssues=$($evidencePathIssues -join "; ")
requiredFactoryAction=$requiredAction
outputDir=$outputDir
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

Write-Host $summary.Trim()

if ($FailOnIncomplete -and ($recommendedConclusion -eq "INCOMPLETE")) {
    exit 2
}
