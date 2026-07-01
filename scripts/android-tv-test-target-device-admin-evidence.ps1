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
    $Value | ConvertTo-Json -Depth 10 | Out-File -FilePath $Path -Encoding utf8
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

function New-Device {
    param(
        [string]$DeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
        [string]$LastSeenAt = "2026-06-24T14:48:19.733Z",
        [string]$TelemetryReceivedAt = "",
        [string]$ClientVersion = "0.1.14",
        [string]$OpenClawVersion = "0.1.14"
    )

    $telemetry = $null
    if (-not [string]::IsNullOrWhiteSpace($TelemetryReceivedAt)) {
        $telemetry = [ordered]@{
            deviceId = $DeviceUuid
            appVersion = $ClientVersion
            openclawVersion = $OpenClawVersion
            runtimeVersion = "factory-pilot"
            foregroundState = "home"
            castState = "idle"
            capturedAt = $TelemetryReceivedAt
            receivedAt = $TelemetryReceivedAt
            network = [ordered]@{
                connected = "true"
                transport = "wifi"
                wifiSsid = "Soulzy"
            }
            memory = [ordered]@{
                appPssKb = 42000
                appPrivateDirtyKb = 12000
                systemLowMemory = "false"
            }
            resourceSession = [ordered]@{
                queueStatus = "not_requested"
                phase = "idle"
            }
        }
    }

    return [ordered]@{
        id = "device-record-$DeviceUuid"
        projectKey = "openclaw-android-tv"
        userId = "user-target"
        deviceFingerprint = $DeviceUuid
        deviceName = "OpenClaw TV"
        osFamily = "Android"
        osVersion = "7.1"
        clientVersion = $ClientVersion
        runtimeVersion = "factory-pilot"
        deviceMetadata = @{}
        openclawVersion = $OpenClawVersion
        lastIp = "192.168.1.88"
        lastSeenAt = $LastSeenAt
        createdAt = "2026-06-24T14:00:00.000Z"
        updatedAt = $LastSeenAt
        telemetry = $telemetry
    }
}

function New-OtaRelease {
    param(
        [int]$VersionCode = 2026070101,
        [string]$ReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
        [string]$DeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e"
    )

    return [ordered]@{
        id = $ReleaseId
        versionName = "0.1.15"
        versionCode = $VersionCode
        rolloutStatus = "rolling"
        targetScope = "deviceUuid:$DeviceUuid"
        installPolicy = "vendor_silent"
        artifactSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86"
    }
}

function New-OtaReport {
    param(
        [string]$Status,
        [string]$Note = "",
        [string]$ReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
        [string]$DeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e"
    )

    return [ordered]@{
        releaseId = $ReleaseId
        deviceUuid = $DeviceUuid
        currentVersionCode = 2026062401
        targetVersionCode = 2026070101
        status = $Status
        progressPercent = if ($Status -eq "installed") { 100 } else { 75 }
        note = $Note
        reportedAt = "2026-06-30T10:00:00.000Z"
        updatedAt = "2026-06-30T10:01:00.000Z"
    }
}

function New-AdminSnapshot {
    param(
        [object[]]$Devices = @(),
        [object[]]$Sessions = @(),
        [object[]]$Reports = @(),
        [int]$ReleaseVersionCode = 2026070101
    )

    return [ordered]@{
        devices = [ordered]@{
            statusCode = 200
            json = [ordered]@{
                status = "ok"
                items = @($Devices)
            }
        }
        sessions = [ordered]@{
            statusCode = 200
            json = [ordered]@{
                status = "ok"
                items = @($Sessions)
            }
        }
        ota = [ordered]@{
            statusCode = 200
            json = [ordered]@{
                status = "ok"
                releases = @((New-OtaRelease -VersionCode $ReleaseVersionCode))
                reports = @($Reports)
                totals = [ordered]@{
                    releases = 1
                    rolling = 1
                    reports = @($Reports).Count
                }
            }
        }
    }
}

function Invoke-TargetDeviceCase {
    param(
        [string]$Name,
        [object]$Snapshot,
        [int]$ExpectedExitCode,
        [string]$ExpectedStatus,
        [string]$ExpectedPresence = "",
        [string]$ExpectedTargetPresent = "",
        [switch]$AllowPending
    )

    $caseRoot = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
    $snapshotPath = Join-Path $caseRoot "admin-snapshot.json"
    Write-JsonFile -Path $snapshotPath -Value $Snapshot
    $outputRoot = Join-Path $caseRoot "out"

    $args = @(
        "-AdminSnapshotPath", $snapshotPath,
        "-OutputRoot", $outputRoot
    )
    if ($AllowPending) {
        $args += "-AllowPending"
    }

    $scriptOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\android-tv-check-target-device-admin-evidence.ps1") @args 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextFile -Path (Join-Path $caseRoot "stdout.txt") -Content (($scriptOutput | Out-String).Trim())

    $summaryPath = Join-Path $outputRoot "summary.txt"
    $summary = Get-SummaryMap -Path $summaryPath
    Assert-Equal -Failures $failures -CaseName $Name -Name "exitCode" -Expected $ExpectedExitCode -Actual $exitCode
    Assert-Equal -Failures $failures -CaseName $Name -Name "status" -Expected $ExpectedStatus -Actual $summary["status"]
    if (-not [string]::IsNullOrWhiteSpace($ExpectedPresence)) {
        Assert-Equal -Failures $failures -CaseName $Name -Name "targetPresence" -Expected $ExpectedPresence -Actual $summary["targetPresence"]
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedTargetPresent)) {
        Assert-Equal -Failures $failures -CaseName $Name -Name "targetDevicePresent" -Expected $ExpectedTargetPresent -Actual $summary["targetDevicePresent"]
    }

    [void]$caseResults.Add([pscustomobject]@{
        name = $Name
        exitCode = $exitCode
        expectedExitCode = $ExpectedExitCode
        status = $summary["status"]
        expectedStatus = $ExpectedStatus
        targetPresence = $summary["targetPresence"]
        targetDevicePresent = $summary["targetDevicePresent"]
        outputRoot = $outputRoot
        summaryPath = $summaryPath
    })
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\target-device-admin-evidence-tests\run-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runRoot = (Resolve-Path $OutputRoot).Path
$failures = New-Object System.Collections.ArrayList
$caseResults = New-Object System.Collections.ArrayList

$staleDevice = New-Device
$onlineDevice = New-Device -TelemetryReceivedAt "2099-01-01T00:00:00.000Z" -LastSeenAt "2099-01-01T00:00:00.000Z"
$activeSession = [ordered]@{
    deviceId = $onlineDevice.id
    deviceFingerprint = $onlineDevice.deviceFingerprint
    active = $true
}

Invoke-TargetDeviceCase `
    -Name "missing-device-no-report" `
    -Snapshot (New-AdminSnapshot) `
    -ExpectedExitCode 0 `
    -ExpectedStatus "PENDING" `
    -ExpectedPresence "missing" `
    -ExpectedTargetPresent "False" `
    -AllowPending

Invoke-TargetDeviceCase `
    -Name "stale-device-no-report" `
    -Snapshot (New-AdminSnapshot -Devices @($staleDevice)) `
    -ExpectedExitCode 0 `
    -ExpectedStatus "PENDING" `
    -ExpectedPresence "stale" `
    -ExpectedTargetPresent "True" `
    -AllowPending

Invoke-TargetDeviceCase `
    -Name "online-installed" `
    -Snapshot (New-AdminSnapshot -Devices @($onlineDevice) -Sessions @($activeSession) -Reports @((New-OtaReport -Status "installed"))) `
    -ExpectedExitCode 0 `
    -ExpectedStatus "PASS" `
    -ExpectedPresence "online" `
    -ExpectedTargetPresent "True"

Invoke-TargetDeviceCase `
    -Name "recoverable-install-failure" `
    -Snapshot (New-AdminSnapshot -Devices @($onlineDevice) -Reports @((New-OtaReport -Status "install_failed" -Note "manual confirmation required by system installer"))) `
    -ExpectedExitCode 0 `
    -ExpectedStatus "RECOVERABLE_FAILURE" `
    -ExpectedPresence "online" `
    -ExpectedTargetPresent "True"

Invoke-TargetDeviceCase `
    -Name "hard-install-failure" `
    -Snapshot (New-AdminSnapshot -Devices @($onlineDevice) -Reports @((New-OtaReport -Status "install_failed" -Note "signature mismatch"))) `
    -ExpectedExitCode 1 `
    -ExpectedStatus "FAIL" `
    -ExpectedPresence "online" `
    -ExpectedTargetPresent "True"

Invoke-TargetDeviceCase `
    -Name "wrong-release-version" `
    -Snapshot (New-AdminSnapshot -Devices @($onlineDevice) -ReleaseVersionCode 2026079999) `
    -ExpectedExitCode 1 `
    -ExpectedStatus "FAIL" `
    -ExpectedPresence "online" `
    -ExpectedTargetPresent "True"

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
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $runRoot "target-device-admin-evidence-test.json") -Encoding utf8

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
