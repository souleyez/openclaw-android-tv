param(
    [string]$OutputRoot = "",
    [switch]$SkipLivePositive
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

function Assert-NonEmpty {
    param(
        [System.Collections.ArrayList]$Failures,
        [string]$CaseName,
        [string]$Name,
        [object]$Actual
    )
    if ([string]::IsNullOrWhiteSpace([string]$Actual)) {
        Add-Failure -Failures $Failures -CaseName $CaseName -Message "$Name expected non-empty value"
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

function New-ReturnChecklist {
    return [ordered]@{
        schema = "openclaw.android-tv.factory-return-checklist.v1"
        requiredFiles = @(
            "feedback/android-tv-factory-feedback.json",
            "feedback/android-tv-vendor-system-permission.json"
        )
        requiredEvidenceDirectories = @(
            "evidence/factory-return/",
            "evidence/factory-return/screenshots/",
            "evidence/factory-return/logs/"
        )
        factoryFeedbackRequiredFields = @(
            "screenshotOrVideoPath",
            "logsPath"
        )
        vendorPermissionRequiredFields = @(
            "evidencePath"
        )
        rejectedPathRules = @(
            "no absolute paths",
            "no Windows drive paths",
            "no empty paths",
            "no .. path traversal",
            "no symbolic links, junctions, or reparse-point folder entries"
        )
        otaCanary = [ordered]@{
            releaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33"
            targetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e"
            versionCode = 2026070101
        }
    }
}

function New-CompleteFactoryFeedback {
    param([string]$LogsPath = "evidence/factory-return/logs/logs.txt")
    return [ordered]@{
        date = "2026-07-01"
        factoryContact = "factory-intake-smoke"
        deviceModel = "RK3128-smoke"
        deviceSn = "smoke-sn"
        firmwareVersion = "factory-fw-smoke"
        androidVersion = "7.1"
        apiLevel = "25"
        buildFingerprint = "openclaw/factory/smoke"
        wifiSsid = "Soulzy"
        apkFileName = "OpenClawTV-0.1.14.apk"
        apkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce"
        installMethod = "factory_tool"
        installResult = "PASS"
        installFailureLog = ""
        defaultHomeSettingMethod = "firmware_default"
        defaultHomeResult = "PASS"
        resolveActivityOutput = "com.openclaw.tv/.MainActivity"
        firstLaunchHomeResult = "PASS"
        remoteHomeReturnResult = "PASS"
        coldBootHomeResult = "PASS"
        restoreFactoryApkState = "retained"
        restoreFactoryDefaultHome = "retained"
        iphoneDiscovery = "PASS"
        iphoneConnection = "PASS"
        iphoneAudio = "PASS"
        xiaomiDiscovery = "PASS"
        xiaomiConnection = "PASS"
        xiaomiAudio = "PASS"
        castReturnTarget = "home"
        otaReceived = "PASS"
        otaInstallResult = "installed"
        homeReportStatus = "reported"
        openclawPss = "40MB"
        leboPss = "20MB"
        abnormalProcesses = "none"
        screenshotOrVideoPath = "evidence/factory-return/screenshots/home.jpg"
        logsPath = $LogsPath
        factoryConclusion = "PASS A"
        requiredFactoryAction = "none"
    }
}

function New-ApkOnlyVendorPermission {
    return [ordered]@{
        date = "2026-07-01"
        vendorContact = "vendor-intake-smoke"
        deviceModel = "RK3128-smoke"
        firmwareVersion = "factory-fw-smoke"
        buildFingerprint = "openclaw/factory/smoke"
        apkOnlyFreshInstallOk = "yes"
        apkOnlyDefaultHomePersists = "yes"
        apkOnlyColdBootHomeOk = "yes"
        openclawPrivAppSupported = "no"
        defaultHomeFirmwareSupported = "no"
        installPackagesWhitelisted = "yes"
        bootCompletedWhitelisted = "yes"
        restoreFactoryPreservesOpenClaw = "yes"
        restoreFactoryReinstallsOpenClaw = "no"
        leboWhitelisted = "yes"
        vendorCastingReplacementAvailable = "no"
        noAdbLogExportAvailable = "yes"
        systemOtaPathAvailable = "yes"
        factoryProvisioningToolAvailable = "no"
        vendorApiAvailable = "no"
        evidencePath = "evidence/factory-return/logs/vendor.txt"
        notes = "factory return package intake smoke fixture"
        vendorDecision = "APK-only acceptable"
    }
}

function New-ReturnPackage {
    param(
        [string]$Root,
        [string]$Mode
    )

    $feedbackDir = Join-Path $Root "feedback"
    $evidenceDir = Join-Path $Root "evidence\factory-return"
    $screenshotsDir = Join-Path $evidenceDir "screenshots"
    $logsDir = Join-Path $evidenceDir "logs"

    New-Item -ItemType Directory -Force -Path $feedbackDir | Out-Null

    if ($Mode -ne "missing-evidence-dirs") {
        New-Item -ItemType Directory -Force -Path $screenshotsDir | Out-Null
        New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
        Write-TextFile -Path (Join-Path $screenshotsDir "home.jpg") -Content "fake screenshot bytes for intake path validation"
        Write-TextFile -Path (Join-Path $logsDir "vendor.txt") -Content "fake vendor diagnostic log"
        if ($Mode -ne "missing-log-file") {
            Write-TextFile -Path (Join-Path $logsDir "logs.txt") -Content "fake install/home/casting/ota logs"
        }
    }

    if ($Mode -eq "template") {
        Copy-Item -LiteralPath (Join-Path $repoRoot "docs\ops\templates\android-tv-factory-feedback.template.json") -Destination (Join-Path $feedbackDir "android-tv-factory-feedback.json")
        Copy-Item -LiteralPath (Join-Path $repoRoot "docs\ops\templates\android-tv-vendor-system-permission.template.json") -Destination (Join-Path $feedbackDir "android-tv-vendor-system-permission.json")
    } else {
        $logsPath = if ($Mode -eq "missing-log-file") { "evidence/factory-return/logs/missing-log.txt" } else { "evidence/factory-return/logs/logs.txt" }
        Write-JsonFile -Path (Join-Path $feedbackDir "android-tv-factory-feedback.json") -Value (New-CompleteFactoryFeedback -LogsPath $logsPath)
        Write-JsonFile -Path (Join-Path $feedbackDir "android-tv-vendor-system-permission.json") -Value (New-ApkOnlyVendorPermission)
    }

    if ($Mode -ne "missing-checklist") {
        Write-JsonFile -Path (Join-Path $feedbackDir "return-package-checklist.json") -Value (New-ReturnChecklist)
    }
}

function Invoke-ReturnPackageIntake {
    param(
        [string]$Name,
        [string]$ReturnPath
    )

    $caseOutputRoot = Join-Path $runRoot "intake-$Name"
    $scriptOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-ingest-factory-pilot-return-package.ps1") -ReturnPath $ReturnPath -OutputRoot $caseOutputRoot -AllowPending 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $runRoot "$Name.log") -Content (($scriptOutput | Out-String).Trim())
    $summaryPath = Join-Path $caseOutputRoot "summary.txt"
    return [pscustomobject]@{
        name = $Name
        exitCode = $exitCode
        outputRoot = $caseOutputRoot
        summaryPath = $summaryPath
        summary = Get-SummaryMap -Path $summaryPath
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-return-intake-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$fixturesRoot = Join-Path $runRoot "fixtures"
New-Item -ItemType Directory -Force -Path $fixturesRoot | Out-Null

$failures = New-Object System.Collections.ArrayList
$caseResults = New-Object System.Collections.ArrayList

if (-not $SkipLivePositive) {
    $completeDir = Join-Path $fixturesRoot "complete-return"
    New-ReturnPackage -Root $completeDir -Mode "complete"
    $completeZip = Join-Path $fixturesRoot "complete-return.zip"
    Compress-Archive -Path (Join-Path $completeDir "*") -DestinationPath $completeZip -CompressionLevel Fastest
    $complete = Invoke-ReturnPackageIntake -Name "complete-zip" -ReturnPath $completeZip
    [void]$caseResults.Add($complete)
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "exitCode" -Expected 0 -Actual $complete.exitCode
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "status" -Expected "PENDING" -Actual $complete.summary["status"]
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "returnPackageKind" -Expected "zip" -Actual $complete.summary["returnPackageKind"]
    Assert-NonEmpty -Failures $failures -CaseName $complete.name -Name "returnPackageSha256" -Actual $complete.summary["returnPackageSha256"]
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "returnChecklistIssueCount" -Expected 0 -Actual $complete.summary["returnChecklistIssueCount"]
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "evidencePathIssueCount" -Expected 0 -Actual $complete.summary["evidencePathIssueCount"]
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "factoryConclusion" -Expected "PASS A" -Actual $complete.summary["factoryConclusion"]
    Assert-Equal -Failures $failures -CaseName $complete.name -Name "vendorDecision" -Expected "APK-only acceptable" -Actual $complete.summary["vendorDecision"]
}

$missingChecklistDir = Join-Path $fixturesRoot "missing-checklist-return"
New-ReturnPackage -Root $missingChecklistDir -Mode "missing-checklist"
$missingChecklist = Invoke-ReturnPackageIntake -Name "missing-checklist" -ReturnPath $missingChecklistDir
[void]$caseResults.Add($missingChecklist)
Assert-Equal -Failures $failures -CaseName $missingChecklist.name -Name "exitCode" -Expected 1 -Actual $missingChecklist.exitCode
Assert-Equal -Failures $failures -CaseName $missingChecklist.name -Name "status" -Expected "FAIL" -Actual $missingChecklist.summary["status"]
Assert-Equal -Failures $failures -CaseName $missingChecklist.name -Name "returnChecklistCandidateCount" -Expected 0 -Actual $missingChecklist.summary["returnChecklistCandidateCount"]
Assert-Equal -Failures $failures -CaseName $missingChecklist.name -Name "intakeStatus" -Expected "NOT_RUN" -Actual $missingChecklist.summary["intakeStatus"]

$missingDirsDir = Join-Path $fixturesRoot "missing-evidence-dirs-return"
New-ReturnPackage -Root $missingDirsDir -Mode "missing-evidence-dirs"
$missingDirs = Invoke-ReturnPackageIntake -Name "missing-evidence-dirs" -ReturnPath $missingDirsDir
[void]$caseResults.Add($missingDirs)
Assert-Equal -Failures $failures -CaseName $missingDirs.name -Name "exitCode" -Expected 1 -Actual $missingDirs.exitCode
Assert-Equal -Failures $failures -CaseName $missingDirs.name -Name "status" -Expected "FAIL" -Actual $missingDirs.summary["status"]
Assert-AtLeast -Failures $failures -CaseName $missingDirs.name -Name "returnChecklistIssueCount" -Minimum 3 -Actual $missingDirs.summary["returnChecklistIssueCount"]
Assert-Equal -Failures $failures -CaseName $missingDirs.name -Name "intakeStatus" -Expected "NOT_RUN" -Actual $missingDirs.summary["intakeStatus"]

$missingLogDir = Join-Path $fixturesRoot "missing-log-file-return"
New-ReturnPackage -Root $missingLogDir -Mode "missing-log-file"
$missingLog = Invoke-ReturnPackageIntake -Name "missing-log-file" -ReturnPath $missingLogDir
[void]$caseResults.Add($missingLog)
Assert-Equal -Failures $failures -CaseName $missingLog.name -Name "exitCode" -Expected 1 -Actual $missingLog.exitCode
Assert-Equal -Failures $failures -CaseName $missingLog.name -Name "status" -Expected "FAIL" -Actual $missingLog.summary["status"]
Assert-AtLeast -Failures $failures -CaseName $missingLog.name -Name "evidencePathIssueCount" -Minimum 1 -Actual $missingLog.summary["evidencePathIssueCount"]
Assert-Equal -Failures $failures -CaseName $missingLog.name -Name "intakeStatus" -Expected "NOT_RUN" -Actual $missingLog.summary["intakeStatus"]

$status = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
$result = [pscustomobject]@{
    status = $status
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $runRoot
    skippedLivePositive = [bool]$SkipLivePositive
    caseCount = $caseResults.Count
    failureCount = $failures.Count
    failures = @($failures)
    cases = @($caseResults | ForEach-Object {
        [pscustomobject]@{
            name = $_.name
            exitCode = $_.exitCode
            outputRoot = $_.outputRoot
            summaryPath = $_.summaryPath
            status = $_.summary["status"]
            intakeStatus = $_.summary["intakeStatus"]
            factoryConclusion = $_.summary["factoryConclusion"]
            vendorDecision = $_.summary["vendorDecision"]
        }
    })
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "factory-return-intake-test.json") -Encoding utf8

$summary = @"
status=$status
checkedAt=$($result.checkedAt)
outputDir=$runRoot
skippedLivePositive=$([bool]$SkipLivePositive)
caseCount=$($caseResults.Count)
failureCount=$($failures.Count)
failures=$($failures -join "; ")
"@
Write-TextFile -Path (Join-Path $runRoot "summary.txt") -Content $summary
Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
