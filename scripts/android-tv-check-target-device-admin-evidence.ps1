param(
    [string]$OutputRoot = "",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [int]$ExpectedTargetVersionCode = 2026070101,
    [string]$HomeSshHost = "root@8.155.8.7",
    [string]$AdminSnapshotPath = "",
    [int]$RecentOnlineMinutes = 10,
    [int]$WarmOnlineMinutes = 60,
    [string[]]$AcceptedReportStatuses = @("verified", "installed", "reported"),
    [string[]]$FailureReportStatuses = @("failed", "failure", "error", "download_failed", "verify_failed", "install_failed"),
    [string]$RecoverableFailurePattern = "(recoverable|retry|retryable|manual install|manual confirmation|system installer|permission|required|prompt|network|timeout|temporarily|\u53ef\u6062\u590d|\u53ef\u91cd\u8bd5|\u91cd\u8bd5|\u624b\u52a8\u5b89\u88c5|\u7cfb\u7edf\u5b89\u88c5\u5668|\u6743\u9650|\u7f51\u7edc|\u6682\u65f6)",
    [switch]$AllowPending,
    [switch]$AllowMissingAdminAuth
)

$ErrorActionPreference = "Stop"

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function ConvertTo-BashLiteral {
    param([string]$Value)

    if ($null -eq $Value) {
        return "''"
    }
    return "'" + $Value.Replace("'", "'\''") + "'"
}

function Format-IsoValue {
    param([object]$Value)

    if ($null -eq $Value) {
        return ""
    }
    if ($Value -is [datetime]) {
        return $Value.ToUniversalTime().ToString("o")
    }
    return [string]$Value
}

function Get-ObjectPropertyValue {
    param(
        [object]$Object,
        [string]$Name,
        [object]$Fallback = $null
    )

    if ($Object -and $Object.PSObject.Properties.Name -contains $Name) {
        $value = $Object.$Name
        if ($null -ne $value) {
            return $value
        }
    }
    return $Fallback
}

function Get-IsoTimestampMillis {
    param([object]$Value)

    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return 0
    }
    $parsed = [datetimeoffset]::MinValue
    if ([datetimeoffset]::TryParse($text, [ref]$parsed)) {
        return $parsed.ToUnixTimeMilliseconds()
    }
    return 0
}

function Get-StringSuffix {
    param(
        [object]$Value,
        [int]$Length = 8
    )

    $text = [string]$Value
    if ($text.Length -le $Length) {
        return $text
    }
    return $text.Substring($text.Length - $Length)
}

function Get-MaskedIp {
    param([object]$Value)

    $text = ([string]$Value).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return ""
    }
    if ($text -match '^\d+\.\d+\.\d+\.\d+$') {
        return ($text -replace '\.\d+$', '.x')
    }
    return Get-StringSuffix -Value $text -Length 12
}

function Get-DeviceKeys {
    param([object]$Device)

    $keys = New-Object System.Collections.ArrayList
    foreach ($value in @(
        (Get-ObjectPropertyValue -Object $Device -Name "deviceFingerprint" -Fallback ""),
        (Get-ObjectPropertyValue -Object $Device -Name "id" -Fallback ""),
        (Get-ObjectPropertyValue -Object (Get-ObjectPropertyValue -Object $Device -Name "telemetry" -Fallback $null) -Name "deviceId" -Fallback "")
    )) {
        if (-not [string]::IsNullOrWhiteSpace([string]$value)) {
            [void]$keys.Add([string]$value)
        }
    }
    return @($keys)
}

function Test-TargetDevice {
    param(
        [object]$Device,
        [string]$TargetDeviceUuid
    )
    return @((Get-DeviceKeys -Device $Device) | Where-Object { $_ -eq $TargetDeviceUuid }).Count -gt 0
}

function Get-DeviceHeartbeat {
    param([object]$Device)

    $telemetry = Get-ObjectPropertyValue -Object $Device -Name "telemetry" -Fallback $null
    $receivedAt = Get-ObjectPropertyValue -Object $telemetry -Name "receivedAt" -Fallback ""
    if (-not [string]::IsNullOrWhiteSpace([string]$receivedAt)) {
        return [string]$receivedAt
    }
    $capturedAt = Get-ObjectPropertyValue -Object $telemetry -Name "capturedAt" -Fallback ""
    if (-not [string]::IsNullOrWhiteSpace([string]$capturedAt)) {
        return [string]$capturedAt
    }
    return [string](Get-ObjectPropertyValue -Object $Device -Name "lastSeenAt" -Fallback "")
}

function Get-DevicePresence {
    param(
        [object]$Device,
        [int]$RecentOnlineMinutes,
        [int]$WarmOnlineMinutes,
        [datetimeoffset]$Now
    )

    if (-not $Device) {
        return [pscustomobject]@{
            label = "missing"
            heartbeatAt = ""
            heartbeatAgeMinutes = $null
        }
    }
    $heartbeat = Get-DeviceHeartbeat -Device $Device
    $timestampMillis = Get-IsoTimestampMillis -Value $heartbeat
    if ($timestampMillis -le 0) {
        return [pscustomobject]@{
            label = "offline"
            heartbeatAt = $heartbeat
            heartbeatAgeMinutes = $null
        }
    }
    $ageMinutes = [math]::Max(0, [math]::Floor(($Now.ToUnixTimeMilliseconds() - $timestampMillis) / 60000))
    $label = if ($ageMinutes -le $RecentOnlineMinutes) {
        "online"
    } elseif ($ageMinutes -le $WarmOnlineMinutes) {
        "warm"
    } else {
        "stale"
    }
    return [pscustomobject]@{
        label = $label
        heartbeatAt = $heartbeat
        heartbeatAgeMinutes = [int]$ageMinutes
    }
}

function ConvertTo-SanitizedTelemetry {
    param([object]$Telemetry)

    if (-not $Telemetry) {
        return $null
    }
    $network = Get-ObjectPropertyValue -Object $Telemetry -Name "network" -Fallback $null
    $memory = Get-ObjectPropertyValue -Object $Telemetry -Name "memory" -Fallback $null
    $resourceSession = Get-ObjectPropertyValue -Object $Telemetry -Name "resourceSession" -Fallback $null
    return [pscustomobject]@{
        present = $true
        deviceIdSuffix = Get-StringSuffix -Value (Get-ObjectPropertyValue -Object $Telemetry -Name "deviceId" -Fallback "")
        deviceIdMatchesTarget = $false
        appVersion = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "appVersion" -Fallback "")
        openclawVersion = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "openclawVersion" -Fallback "")
        runtimeVersion = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "runtimeVersion" -Fallback "")
        foregroundState = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "foregroundState" -Fallback "")
        castState = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "castState" -Fallback "")
        capturedAt = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "capturedAt" -Fallback "")
        receivedAt = [string](Get-ObjectPropertyValue -Object $Telemetry -Name "receivedAt" -Fallback "")
        network = [pscustomobject]@{
            connected = Get-ObjectPropertyValue -Object $network -Name "connected" -Fallback ""
            transport = Get-ObjectPropertyValue -Object $network -Name "transport" -Fallback ""
            wifiSsidPresent = -not [string]::IsNullOrWhiteSpace([string](Get-ObjectPropertyValue -Object $network -Name "wifiSsid" -Fallback ""))
        }
        memory = [pscustomobject]@{
            appPssKb = [int](Get-ObjectPropertyValue -Object $memory -Name "appPssKb" -Fallback 0)
            appPrivateDirtyKb = [int](Get-ObjectPropertyValue -Object $memory -Name "appPrivateDirtyKb" -Fallback 0)
            systemLowMemory = Get-ObjectPropertyValue -Object $memory -Name "systemLowMemory" -Fallback ""
        }
        resourceSession = [pscustomobject]@{
            queueStatus = [string](Get-ObjectPropertyValue -Object $resourceSession -Name "queueStatus" -Fallback "")
            phase = [string](Get-ObjectPropertyValue -Object $resourceSession -Name "phase" -Fallback "")
        }
    }
}

function ConvertTo-SanitizedDevice {
    param(
        [object]$Device,
        [string]$TargetDeviceUuid,
        [int]$RecentOnlineMinutes,
        [int]$WarmOnlineMinutes,
        [datetimeoffset]$Now
    )

    if (-not $Device) {
        return $null
    }
    $telemetry = Get-ObjectPropertyValue -Object $Device -Name "telemetry" -Fallback $null
    $sanitizedTelemetry = ConvertTo-SanitizedTelemetry -Telemetry $telemetry
    if ($sanitizedTelemetry) {
        $sanitizedTelemetry.deviceIdMatchesTarget = [string](Get-ObjectPropertyValue -Object $telemetry -Name "deviceId" -Fallback "") -eq $TargetDeviceUuid
    }
    return [pscustomobject]@{
        idSuffix = Get-StringSuffix -Value (Get-ObjectPropertyValue -Object $Device -Name "id" -Fallback "")
        deviceFingerprint = if ([string](Get-ObjectPropertyValue -Object $Device -Name "deviceFingerprint" -Fallback "") -eq $TargetDeviceUuid) { [string](Get-ObjectPropertyValue -Object $Device -Name "deviceFingerprint" -Fallback "") } else { "" }
        deviceFingerprintSuffix = Get-StringSuffix -Value (Get-ObjectPropertyValue -Object $Device -Name "deviceFingerprint" -Fallback "")
        deviceFingerprintMatchesTarget = [string](Get-ObjectPropertyValue -Object $Device -Name "deviceFingerprint" -Fallback "") -eq $TargetDeviceUuid
        deviceName = [string](Get-ObjectPropertyValue -Object $Device -Name "deviceName" -Fallback "")
        osFamily = [string](Get-ObjectPropertyValue -Object $Device -Name "osFamily" -Fallback "")
        osVersion = [string](Get-ObjectPropertyValue -Object $Device -Name "osVersion" -Fallback "")
        clientVersion = [string](Get-ObjectPropertyValue -Object $Device -Name "clientVersion" -Fallback "")
        runtimeVersion = [string](Get-ObjectPropertyValue -Object $Device -Name "runtimeVersion" -Fallback "")
        openclawVersion = [string](Get-ObjectPropertyValue -Object $Device -Name "openclawVersion" -Fallback "")
        lastIpMasked = Get-MaskedIp -Value (Get-ObjectPropertyValue -Object $Device -Name "lastIp" -Fallback "")
        lastSeenAt = [string](Get-ObjectPropertyValue -Object $Device -Name "lastSeenAt" -Fallback "")
        createdAt = [string](Get-ObjectPropertyValue -Object $Device -Name "createdAt" -Fallback "")
        updatedAt = [string](Get-ObjectPropertyValue -Object $Device -Name "updatedAt" -Fallback "")
        presence = Get-DevicePresence -Device $Device -RecentOnlineMinutes $RecentOnlineMinutes -WarmOnlineMinutes $WarmOnlineMinutes -Now $Now
        telemetry = $sanitizedTelemetry
    }
}

function ConvertTo-SanitizedReport {
    param([object]$Report)

    if (-not $Report) {
        return $null
    }
    return [pscustomobject]@{
        releaseId = [string](Get-ObjectPropertyValue -Object $Report -Name "releaseId" -Fallback "")
        deviceUuid = [string](Get-ObjectPropertyValue -Object $Report -Name "deviceUuid" -Fallback "")
        currentVersionCode = [int](Get-ObjectPropertyValue -Object $Report -Name "currentVersionCode" -Fallback 0)
        targetVersionCode = [int](Get-ObjectPropertyValue -Object $Report -Name "targetVersionCode" -Fallback 0)
        status = [string](Get-ObjectPropertyValue -Object $Report -Name "status" -Fallback "")
        progressPercent = [int](Get-ObjectPropertyValue -Object $Report -Name "progressPercent" -Fallback 0)
        note = [string](Get-ObjectPropertyValue -Object $Report -Name "note" -Fallback "")
        reportedAt = [string](Get-ObjectPropertyValue -Object $Report -Name "reportedAt" -Fallback "")
        updatedAt = [string](Get-ObjectPropertyValue -Object $Report -Name "updatedAt" -Fallback "")
    }
}

function ConvertTo-TargetDeviceAdminEvidence {
    param(
        [object]$Snapshot,
        [string]$ProjectKey,
        [string]$TargetDeviceUuid,
        [string]$ExpectedReleaseId,
        [int]$ExpectedVersionCode,
        [int]$RecentOnlineMinutes,
        [int]$WarmOnlineMinutes,
        [string[]]$AcceptedStatuses,
        [string[]]$FailureStatuses,
        [string]$RecoverablePattern,
        [string]$Source = "local-snapshot"
    )

    $now = [datetimeoffset]::UtcNow
    $devicesPayload = Get-ObjectPropertyValue -Object $Snapshot -Name "devices" -Fallback $null
    $sessionsPayload = Get-ObjectPropertyValue -Object $Snapshot -Name "sessions" -Fallback $null
    $otaPayload = Get-ObjectPropertyValue -Object $Snapshot -Name "ota" -Fallback $null
    $devicesStatusCode = [int](Get-ObjectPropertyValue -Object $devicesPayload -Name "statusCode" -Fallback 200)
    $sessionsStatusCode = [int](Get-ObjectPropertyValue -Object $sessionsPayload -Name "statusCode" -Fallback 200)
    $otaStatusCode = [int](Get-ObjectPropertyValue -Object $otaPayload -Name "statusCode" -Fallback 200)
    $devicesJson = Get-ObjectPropertyValue -Object $devicesPayload -Name "json" -Fallback $devicesPayload
    $sessionsJson = Get-ObjectPropertyValue -Object $sessionsPayload -Name "json" -Fallback $sessionsPayload
    $otaJson = Get-ObjectPropertyValue -Object $otaPayload -Name "json" -Fallback $otaPayload
    $endpointStatusOk = $devicesStatusCode -eq 200 -and $sessionsStatusCode -eq 200 -and $otaStatusCode -eq 200 -and
        [string](Get-ObjectPropertyValue -Object $devicesJson -Name "status" -Fallback "ok") -eq "ok" -and
        [string](Get-ObjectPropertyValue -Object $sessionsJson -Name "status" -Fallback "ok") -eq "ok" -and
        [string](Get-ObjectPropertyValue -Object $otaJson -Name "status" -Fallback "ok") -eq "ok"
    $devices = @((Get-ObjectPropertyValue -Object $devicesJson -Name "items" -Fallback @()))
    $sessions = @((Get-ObjectPropertyValue -Object $sessionsJson -Name "items" -Fallback @()))
    $releases = @((Get-ObjectPropertyValue -Object $otaJson -Name "releases" -Fallback @()))
    $reports = @((Get-ObjectPropertyValue -Object $otaJson -Name "reports" -Fallback @()))

    $targetDevices = @($devices | Where-Object { Test-TargetDevice -Device $_ -TargetDeviceUuid $TargetDeviceUuid } | Sort-Object -Property @{ Expression = { Get-IsoTimestampMillis -Value (Get-DeviceHeartbeat -Device $_) }; Descending = $true })
    $targetDevice = $targetDevices | Select-Object -First 1
    $targetPresence = Get-DevicePresence -Device $targetDevice -RecentOnlineMinutes $RecentOnlineMinutes -WarmOnlineMinutes $WarmOnlineMinutes -Now $now
    $release = $releases | Where-Object { [string](Get-ObjectPropertyValue -Object $_ -Name "id" -Fallback "") -eq $ExpectedReleaseId } | Select-Object -First 1
    $matchingReports = @($reports | Where-Object {
        [string](Get-ObjectPropertyValue -Object $_ -Name "releaseId" -Fallback "") -eq $ExpectedReleaseId -and
            [string](Get-ObjectPropertyValue -Object $_ -Name "deviceUuid" -Fallback "") -eq $TargetDeviceUuid
    } | Sort-Object -Property @{ Expression = {
        $updatedAt = [string](Get-ObjectPropertyValue -Object $_ -Name "updatedAt" -Fallback "")
        if ([string]::IsNullOrWhiteSpace($updatedAt)) {
            $updatedAt = [string](Get-ObjectPropertyValue -Object $_ -Name "reportedAt" -Fallback "")
        }
        Get-IsoTimestampMillis -Value $updatedAt
    }; Descending = $true })
    $latestReport = $matchingReports | Select-Object -First 1
    $latestStatus = ([string](Get-ObjectPropertyValue -Object $latestReport -Name "status" -Fallback "")).ToLowerInvariant()
    $latestNote = [string](Get-ObjectPropertyValue -Object $latestReport -Name "note" -Fallback "")
    $acceptedSet = @{}
    foreach ($acceptedStatus in $AcceptedStatuses) {
        if (-not [string]::IsNullOrWhiteSpace($acceptedStatus)) {
            $acceptedSet[$acceptedStatus.Trim().ToLowerInvariant()] = $true
        }
    }
    $failureSet = @{}
    foreach ($failureStatus in $FailureStatuses) {
        if (-not [string]::IsNullOrWhiteSpace($failureStatus)) {
            $failureSet[$failureStatus.Trim().ToLowerInvariant()] = $true
        }
    }
    $releaseVersionCode = [int](Get-ObjectPropertyValue -Object $release -Name "versionCode" -Fallback 0)
    $releaseMatches = $release -and $releaseVersionCode -eq $ExpectedVersionCode
    $latestAccepted = $latestReport -and $acceptedSet.ContainsKey($latestStatus)
    $latestFailure = $latestReport -and $failureSet.ContainsKey($latestStatus)
    $recoverableFailure = $latestFailure -and -not [string]::IsNullOrWhiteSpace($latestNote) -and $latestNote -match $RecoverablePattern
    $latestReportStatusText = [string](Get-ObjectPropertyValue -Object $latestReport -Name "status" -Fallback "")
    $activeTargetSessions = @($sessions | Where-Object {
        (Get-ObjectPropertyValue -Object $_ -Name "active" -Fallback $false) -eq $true -and (
            [string](Get-ObjectPropertyValue -Object $_ -Name "deviceFingerprint" -Fallback "") -eq $TargetDeviceUuid -or
            [string](Get-ObjectPropertyValue -Object $_ -Name "deviceId" -Fallback "") -eq [string](Get-ObjectPropertyValue -Object $targetDevice -Name "id" -Fallback "") -or
            [string](Get-ObjectPropertyValue -Object $_ -Name "deviceId" -Fallback "") -eq $TargetDeviceUuid
        )
    })

    $status = "PENDING"
    $detail = "target device evidence pending"
    if (-not $endpointStatusOk) {
        $status = "FAIL"
        $detail = "admin endpoint status mismatch devices=$devicesStatusCode sessions=$sessionsStatusCode ota=$otaStatusCode"
    } elseif (-not $releaseMatches) {
        $status = "FAIL"
        $detail = "expected release not found or version mismatch; found=$([bool]$release); versionCode=$releaseVersionCode"
    } elseif ($latestAccepted -and $targetDevice) {
        $status = "PASS"
        $detail = "target device report status=$latestReportStatusText; presence=$($targetPresence.label)"
    } elseif ($recoverableFailure) {
        $status = "RECOVERABLE_FAILURE"
        $detail = "target device failure status=$latestReportStatusText; recoverable note=$latestNote"
    } elseif ($latestFailure) {
        $status = "FAIL"
        $detail = "target device failure status=$latestReportStatusText; missing recoverable reason"
    } elseif (-not $targetDevice) {
        $detail = "target device not found in admin device list; matchingReports=$($matchingReports.Count)"
    } elseif (-not $latestReport) {
        $detail = "target device present; presence=$($targetPresence.label); no matching OTA report"
    } else {
        if ([string]::IsNullOrWhiteSpace($latestReportStatusText)) {
            $latestReportStatusText = "unknown"
        }
        $detail = "latest target report status=$latestReportStatusText; presence=$($targetPresence.label)"
    }

    return [pscustomobject]@{
        status = $status
        detail = $detail
        projectKey = $ProjectKey
        targetDeviceUuid = $TargetDeviceUuid
        expectedOtaReleaseId = $ExpectedReleaseId
        expectedTargetVersionCode = $ExpectedVersionCode
        checkedAt = $now.ToString("o")
        source = $Source
        thresholds = [pscustomobject]@{
            recentOnlineMinutes = $RecentOnlineMinutes
            warmOnlineMinutes = $WarmOnlineMinutes
        }
        endpointStatus = [pscustomobject]@{
            devices = $devicesStatusCode
            sessions = $sessionsStatusCode
            ota = $otaStatusCode
        }
        targetDevice = ConvertTo-SanitizedDevice -Device $targetDevice -TargetDeviceUuid $TargetDeviceUuid -RecentOnlineMinutes $RecentOnlineMinutes -WarmOnlineMinutes $WarmOnlineMinutes -Now $now
        targetDevicePresent = [bool]$targetDevice
        targetDeviceDuplicateCount = $targetDevices.Count
        targetPresence = $targetPresence
        activeTargetSessionCount = $activeTargetSessions.Count
        release = if ($release) {
            [pscustomobject]@{
                id = [string](Get-ObjectPropertyValue -Object $release -Name "id" -Fallback "")
                versionName = [string](Get-ObjectPropertyValue -Object $release -Name "versionName" -Fallback "")
                versionCode = $releaseVersionCode
                rolloutStatus = [string](Get-ObjectPropertyValue -Object $release -Name "rolloutStatus" -Fallback "")
                targetScope = [string](Get-ObjectPropertyValue -Object $release -Name "targetScope" -Fallback "")
                installPolicy = [string](Get-ObjectPropertyValue -Object $release -Name "installPolicy" -Fallback "")
                artifactSha256 = [string](Get-ObjectPropertyValue -Object $release -Name "artifactSha256" -Fallback "")
            }
        } else {
            $null
        }
        latestReport = ConvertTo-SanitizedReport -Report $latestReport
        matchingReportCount = $matchingReports.Count
        latestAccepted = [bool]$latestAccepted
        recoverableFailure = [bool]$recoverableFailure
        totals = [pscustomobject]@{
            devices = $devices.Count
            sessions = $sessions.Count
            activeSessions = @($sessions | Where-Object { (Get-ObjectPropertyValue -Object $_ -Name "active" -Fallback $false) -eq $true }).Count
            releases = $releases.Count
            reports = $reports.Count
        }
        acceptedReportStatuses = $AcceptedStatuses
        failureReportStatuses = $FailureStatuses
        recoverableFailurePattern = $RecoverablePattern
    }
}

function Invoke-RemoteTargetDeviceAdminSnapshot {
    param(
        [string]$SshHost,
        [string]$ProjectKey,
        [string]$TargetDeviceUuid,
        [string]$ExpectedReleaseId,
        [int]$ExpectedVersionCode,
        [int]$RecentOnlineMinutes,
        [int]$WarmOnlineMinutes,
        [string[]]$AcceptedStatuses,
        [string[]]$FailureStatuses,
        [string]$RecoverablePattern
    )

    $acceptedCsv = ($AcceptedStatuses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $failureCsv = ($FailureStatuses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $remoteScript = @'
set -euo pipefail
PROJECT_KEY="__PROJECT_KEY__"
TARGET_DEVICE_UUID="__TARGET_DEVICE_UUID__"
EXPECTED_RELEASE_ID="__EXPECTED_RELEASE_ID__"
EXPECTED_TARGET_VERSION_CODE="__EXPECTED_TARGET_VERSION_CODE__"
RECENT_ONLINE_MINUTES="__RECENT_ONLINE_MINUTES__"
WARM_ONLINE_MINUTES="__WARM_ONLINE_MINUTES__"
ACCEPTED_STATUSES="__ACCEPTED_STATUSES__"
FAILURE_STATUSES="__FAILURE_STATUSES__"
RECOVERABLE_FAILURE_PATTERN="__RECOVERABLE_FAILURE_PATTERN__"
export PROJECT_KEY TARGET_DEVICE_UUID EXPECTED_RELEASE_ID EXPECTED_TARGET_VERSION_CODE RECENT_ONLINE_MINUTES WARM_ONLINE_MINUTES ACCEPTED_STATUSES FAILURE_STATUSES RECOVERABLE_FAILURE_PATTERN

set -a
[ -f /etc/default/home-platform-api ] && . /etc/default/home-platform-api || true
[ -f /srv/home/.env.production ] && . /srv/home/.env.production || true
set +a

admin_header_name=""
admin_secret=""
if [ -n "${CONTROL_PLANE_ADMIN_SESSION:-}" ]; then
  admin_header_name="X-Control-Plane-Admin-Session"
  admin_secret="$CONTROL_PLANE_ADMIN_SESSION"
elif [ -n "${CP_ADMIN_SESSION:-}" ]; then
  admin_header_name="X-Control-Plane-Admin-Session"
  admin_secret="$CP_ADMIN_SESSION"
elif [ -n "${CONTROL_PLANE_ADMIN_TOKEN:-}" ]; then
  admin_header_name="X-Control-Plane-Admin-Token"
  admin_secret="$CONTROL_PLANE_ADMIN_TOKEN"
elif [ -n "${HOME_ADMIN_TOKEN:-}" ]; then
  admin_header_name="X-Control-Plane-Admin-Token"
  admin_secret="$HOME_ADMIN_TOKEN"
elif [ -n "${CP_ADMIN_TOKEN:-}" ]; then
  admin_header_name="X-Control-Plane-Admin-Token"
  admin_secret="$CP_ADMIN_TOKEN"
fi

if [ -z "$admin_secret" ]; then
  node -e 'console.log(JSON.stringify({status:"AUTH_REQUIRED", detail:"admin auth env var not present on remote host", checkedAt:new Date().toISOString(), source:"remote-admin"}, null, 2))'
  exit 0
fi

export ADMIN_HEADER_NAME="$admin_header_name"
export ADMIN_SECRET="$admin_secret"

node <<'NODE'
const http = require('http');

const projectKey = process.env.PROJECT_KEY;
const targetDeviceUuid = process.env.TARGET_DEVICE_UUID;
const expectedReleaseId = process.env.EXPECTED_RELEASE_ID;
const expectedVersionCode = Number(process.env.EXPECTED_TARGET_VERSION_CODE || 0);
const recentOnlineMinutes = Number(process.env.RECENT_ONLINE_MINUTES || 10);
const warmOnlineMinutes = Number(process.env.WARM_ONLINE_MINUTES || 60);
const headerName = process.env.ADMIN_HEADER_NAME;
const adminSecret = process.env.ADMIN_SECRET;
const accepted = new Set(String(process.env.ACCEPTED_STATUSES || '').split(',').map((item) => item.trim().toLowerCase()).filter(Boolean));
const failureStatuses = new Set(String(process.env.FAILURE_STATUSES || '').split(',').map((item) => item.trim().toLowerCase()).filter(Boolean));
const recoverablePatternText = process.env.RECOVERABLE_FAILURE_PATTERN || '';
let recoverablePattern = null;
try {
  recoverablePattern = recoverablePatternText ? new RegExp(recoverablePatternText, 'i') : null;
} catch (_error) {
  recoverablePattern = null;
}

function getJson(path) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: '127.0.0.1',
      port: 3210,
      method: 'GET',
      path,
      headers: { [headerName]: adminSecret },
    }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        try {
          resolve({ statusCode: res.statusCode, json: JSON.parse(body) });
        } catch (error) {
          reject(error);
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

function parseTime(value) {
  const parsed = Date.parse(value || '');
  return Number.isNaN(parsed) ? 0 : parsed;
}

function suffix(value, length = 8) {
  return String(value || '').slice(-length);
}

function maskIp(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return '';
  }
  if (/^\d+\.\d+\.\d+\.\d+$/.test(raw)) {
    return raw.replace(/\.\d+$/, '.x');
  }
  return suffix(raw, 12);
}

function readDeviceKeys(device) {
  return [
    device?.deviceFingerprint,
    device?.id,
    device?.telemetry?.deviceId,
  ].filter(Boolean).map((item) => String(item));
}

function isTargetDevice(device) {
  return readDeviceKeys(device).includes(targetDeviceUuid);
}

function heartbeatAt(device) {
  return device?.telemetry?.receivedAt || device?.telemetry?.capturedAt || device?.lastSeenAt || '';
}

function presenceFor(device) {
  if (!device) {
    return { label: 'missing', heartbeatAt: '', heartbeatAgeMinutes: null };
  }
  const heartbeat = heartbeatAt(device);
  const timestamp = parseTime(heartbeat);
  if (!timestamp) {
    return { label: 'offline', heartbeatAt: heartbeat, heartbeatAgeMinutes: null };
  }
  const ageMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (ageMinutes <= recentOnlineMinutes) {
    return { label: 'online', heartbeatAt: heartbeat, heartbeatAgeMinutes: ageMinutes };
  }
  if (ageMinutes <= warmOnlineMinutes) {
    return { label: 'warm', heartbeatAt: heartbeat, heartbeatAgeMinutes: ageMinutes };
  }
  return { label: 'stale', heartbeatAt: heartbeat, heartbeatAgeMinutes: ageMinutes };
}

function sanitizeTelemetry(telemetry) {
  if (!telemetry) {
    return null;
  }
  return {
    present: true,
    deviceIdSuffix: suffix(telemetry.deviceId),
    deviceIdMatchesTarget: telemetry.deviceId === targetDeviceUuid,
    appVersion: telemetry.appVersion || '',
    openclawVersion: telemetry.openclawVersion || '',
    runtimeVersion: telemetry.runtimeVersion || '',
    foregroundState: telemetry.foregroundState || '',
    castState: telemetry.castState || '',
    capturedAt: telemetry.capturedAt || '',
    receivedAt: telemetry.receivedAt || '',
    network: {
      connected: telemetry.network?.connected ?? '',
      transport: telemetry.network?.transport ?? '',
      wifiSsidPresent: Boolean(telemetry.network?.wifiSsid),
    },
    memory: {
      appPssKb: Number(telemetry.memory?.appPssKb || 0),
      appPrivateDirtyKb: Number(telemetry.memory?.appPrivateDirtyKb || 0),
      systemLowMemory: telemetry.memory?.systemLowMemory ?? '',
    },
    resourceSession: {
      queueStatus: telemetry.resourceSession?.queueStatus || '',
      phase: telemetry.resourceSession?.phase || '',
    },
  };
}

function sanitizeDevice(device) {
  if (!device) {
    return null;
  }
  const presence = presenceFor(device);
  return {
    idSuffix: suffix(device.id),
    deviceFingerprint: isTargetDevice(device) ? device.deviceFingerprint || '' : '',
    deviceFingerprintSuffix: suffix(device.deviceFingerprint),
    deviceFingerprintMatchesTarget: device.deviceFingerprint === targetDeviceUuid,
    deviceName: device.deviceName || '',
    osFamily: device.osFamily || '',
    osVersion: device.osVersion || '',
    clientVersion: device.clientVersion || '',
    runtimeVersion: device.runtimeVersion || '',
    openclawVersion: device.openclawVersion || '',
    lastIpMasked: maskIp(device.lastIp),
    lastSeenAt: device.lastSeenAt || '',
    createdAt: device.createdAt || '',
    updatedAt: device.updatedAt || '',
    presence,
    telemetry: sanitizeTelemetry(device.telemetry),
  };
}

function reportTime(report) {
  return parseTime(report?.updatedAt || report?.reportedAt);
}

function sanitizeReport(report) {
  if (!report) {
    return null;
  }
  return {
    releaseId: report.releaseId || '',
    deviceUuid: report.deviceUuid || '',
    currentVersionCode: Number(report.currentVersionCode || 0),
    targetVersionCode: Number(report.targetVersionCode || 0),
    status: report.status || '',
    progressPercent: Number(report.progressPercent || 0),
    note: report.note || '',
    reportedAt: report.reportedAt || '',
    updatedAt: report.updatedAt || '',
  };
}

(async () => {
  const [devicesResponse, sessionsResponse, otaResponse] = await Promise.all([
    getJson(`/api/admin/devices?projectKey=${encodeURIComponent(projectKey)}`),
    getJson(`/api/admin/sessions?projectKey=${encodeURIComponent(projectKey)}`),
    getJson(`/api/admin/ota?projectKey=${encodeURIComponent(projectKey)}`),
  ]);

  const endpointStatusOk = [devicesResponse, sessionsResponse, otaResponse]
    .every((item) => item.statusCode === 200 && item.json.status === 'ok');
  const devices = devicesResponse.json.items || [];
  const sessions = sessionsResponse.json.items || [];
  const releases = otaResponse.json.releases || [];
  const reports = otaResponse.json.reports || [];
  const targetDevices = devices
    .filter(isTargetDevice)
    .sort((left, right) => parseTime(heartbeatAt(right)) - parseTime(heartbeatAt(left)));
  const targetDevice = targetDevices[0] || null;
  const targetPresence = presenceFor(targetDevice);
  const release = releases.find((item) => item.id === expectedReleaseId) || null;
  const matchingReports = reports
    .filter((item) => item.releaseId === expectedReleaseId && item.deviceUuid === targetDeviceUuid)
    .sort((left, right) => reportTime(right) - reportTime(left));
  const latestReport = matchingReports[0] || null;
  const latestStatus = String(latestReport?.status || '').toLowerCase();
  const latestNote = String(latestReport?.note || '');
  const releaseMatches = Boolean(release) && Number(release.versionCode || 0) === expectedVersionCode;
  const latestAccepted = Boolean(latestReport) && accepted.has(latestStatus);
  const latestFailure = Boolean(latestReport) && failureStatuses.has(latestStatus);
  const recoverableFailure = Boolean(latestFailure && recoverablePattern && recoverablePattern.test(latestNote));
  const activeTargetSessions = sessions.filter((session) => (
    session.active === true
    && (
      session.deviceFingerprint === targetDeviceUuid
      || session.deviceId === targetDevice?.id
      || session.deviceId === targetDeviceUuid
    )
  ));

  let status = 'PENDING';
  let detail = 'target device evidence pending';
  if (!endpointStatusOk) {
    status = 'FAIL';
    detail = `admin endpoint status mismatch devices=${devicesResponse.statusCode} sessions=${sessionsResponse.statusCode} ota=${otaResponse.statusCode}`;
  } else if (!releaseMatches) {
    status = 'FAIL';
    detail = `expected release not found or version mismatch; found=${Boolean(release)}; versionCode=${release ? release.versionCode : 0}`;
  } else if (latestAccepted && targetDevice) {
    status = 'PASS';
    detail = `target device report status=${latestReport.status}; presence=${targetPresence.label}`;
  } else if (recoverableFailure) {
    status = 'RECOVERABLE_FAILURE';
    detail = `target device failure status=${latestReport.status}; recoverable note=${latestNote}`;
  } else if (latestFailure) {
    status = 'FAIL';
    detail = `target device failure status=${latestReport.status}; missing recoverable reason`;
  } else if (!targetDevice) {
    detail = `target device not found in admin device list; matchingReports=${matchingReports.length}`;
  } else if (!latestReport) {
    detail = `target device present; presence=${targetPresence.label}; no matching OTA report`;
  } else {
    detail = `latest target report status=${latestReport.status || 'unknown'}; presence=${targetPresence.label}`;
  }

  console.log(JSON.stringify({
    status,
    detail,
    projectKey,
    targetDeviceUuid,
    expectedOtaReleaseId: expectedReleaseId,
    expectedTargetVersionCode: expectedVersionCode,
    checkedAt: new Date().toISOString(),
    source: 'remote-admin',
    thresholds: {
      recentOnlineMinutes,
      warmOnlineMinutes,
    },
    endpointStatus: {
      devices: devicesResponse.statusCode,
      sessions: sessionsResponse.statusCode,
      ota: otaResponse.statusCode,
    },
    targetDevice: sanitizeDevice(targetDevice),
    targetDevicePresent: Boolean(targetDevice),
    targetDeviceDuplicateCount: targetDevices.length,
    targetPresence,
    activeTargetSessionCount: activeTargetSessions.length,
    release: release ? {
      id: release.id,
      versionName: release.versionName,
      versionCode: release.versionCode,
      rolloutStatus: release.rolloutStatus,
      targetScope: release.targetScope,
      installPolicy: release.installPolicy,
      artifactSha256: release.artifactSha256,
    } : null,
    latestReport: sanitizeReport(latestReport),
    matchingReportCount: matchingReports.length,
    latestAccepted,
    recoverableFailure,
    totals: {
      devices: devices.length,
      sessions: sessions.length,
      activeSessions: sessions.filter((item) => item.active === true).length,
      releases: releases.length,
      reports: reports.length,
    },
    acceptedReportStatuses: [...accepted],
    failureReportStatuses: [...failureStatuses],
    recoverableFailurePattern: recoverablePatternText,
  }, null, 2));
})().catch((error) => {
  console.log(JSON.stringify({
    status: 'FAIL',
    detail: error.message,
    checkedAt: new Date().toISOString(),
    source: 'remote-admin',
  }, null, 2));
  process.exit(1);
});
NODE
'@
    $remoteScript = $remoteScript.Replace('"__PROJECT_KEY__"', (ConvertTo-BashLiteral -Value $ProjectKey))
    $remoteScript = $remoteScript.Replace('"__TARGET_DEVICE_UUID__"', (ConvertTo-BashLiteral -Value $TargetDeviceUuid))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_RELEASE_ID__"', (ConvertTo-BashLiteral -Value $ExpectedReleaseId))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_TARGET_VERSION_CODE__"', (ConvertTo-BashLiteral -Value ([string]$ExpectedVersionCode)))
    $remoteScript = $remoteScript.Replace('"__RECENT_ONLINE_MINUTES__"', (ConvertTo-BashLiteral -Value ([string]$RecentOnlineMinutes)))
    $remoteScript = $remoteScript.Replace('"__WARM_ONLINE_MINUTES__"', (ConvertTo-BashLiteral -Value ([string]$WarmOnlineMinutes)))
    $remoteScript = $remoteScript.Replace('"__ACCEPTED_STATUSES__"', (ConvertTo-BashLiteral -Value $acceptedCsv))
    $remoteScript = $remoteScript.Replace('"__FAILURE_STATUSES__"', (ConvertTo-BashLiteral -Value $failureCsv))
    $remoteScript = $remoteScript.Replace('"__RECOVERABLE_FAILURE_PATTERN__"', (ConvertTo-BashLiteral -Value $RecoverablePattern))

    try {
        $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $processInfo.FileName = "ssh"
        $remoteCommand = "tr -d '\r' | bash -s"
        if ($processInfo.ArgumentList) {
            [void]$processInfo.ArgumentList.Add($SshHost)
            [void]$processInfo.ArgumentList.Add($remoteCommand)
        } else {
            $processInfo.Arguments = "$SshHost `"$remoteCommand`""
        }
        $processInfo.RedirectStandardInput = $true
        $processInfo.RedirectStandardOutput = $true
        $processInfo.RedirectStandardError = $true
        $processInfo.UseShellExecute = $false
        if ($processInfo.PSObject.Properties.Name -contains "StandardInputEncoding") {
            $processInfo.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
        }
        if ($processInfo.PSObject.Properties.Name -contains "StandardOutputEncoding") {
            $processInfo.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
        }
        if ($processInfo.PSObject.Properties.Name -contains "StandardErrorEncoding") {
            $processInfo.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
        }
        $process = [System.Diagnostics.Process]::Start($processInfo)
        $process.StandardInput.Write($remoteScript.TrimStart([char]0xFEFF))
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode
        if ($exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($stdout)) {
            $text = $stdout.Trim()
        } else {
            $text = (($stdout, $stderr) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Out-String).Trim()
        }
    } catch {
        $text = $_.Exception.Message
        $exitCode = 1
    }
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote target device admin check failed"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote target device admin check failed"
                error = $text
                checkedAt = (Get-Date).ToUniversalTime().ToString("o")
                source = "remote-admin"
            } | ConvertTo-Json -Depth 4)
        }
    }
    try {
        $payload = $text | ConvertFrom-Json
        return [pscustomobject]@{
            status = [string]$payload.status
            detail = [string]$payload.detail
            json = $text
        }
    } catch {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote target device admin check returned invalid JSON"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote target device admin check returned invalid JSON"
                rawOutput = $text
                checkedAt = (Get-Date).ToUniversalTime().ToString("o")
                source = "remote-admin"
            } | ConvertTo-Json -Depth 4)
        }
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\target-device-admin-checks\target-device-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

if (-not [string]::IsNullOrWhiteSpace($AdminSnapshotPath)) {
    try {
        $snapshotFile = Resolve-Path -Path $AdminSnapshotPath
        $fixture = Get-Content -Raw -LiteralPath $snapshotFile.Path | ConvertFrom-Json
        $snapshotPayload = ConvertTo-TargetDeviceAdminEvidence `
            -Snapshot $fixture `
            -ProjectKey $ProjectKey `
            -TargetDeviceUuid $TargetDeviceUuid `
            -ExpectedReleaseId $ExpectedOtaReleaseId `
            -ExpectedVersionCode $ExpectedTargetVersionCode `
            -RecentOnlineMinutes $RecentOnlineMinutes `
            -WarmOnlineMinutes $WarmOnlineMinutes `
            -AcceptedStatuses $AcceptedReportStatuses `
            -FailureStatuses $FailureReportStatuses `
            -RecoverablePattern $RecoverableFailurePattern `
            -Source "local-admin-snapshot"
        $snapshot = [pscustomobject]@{
            status = [string]$snapshotPayload.status
            detail = [string]$snapshotPayload.detail
            json = ($snapshotPayload | ConvertTo-Json -Depth 8)
        }
    } catch {
        $snapshot = [pscustomobject]@{
            status = "FAIL"
            detail = "admin snapshot file read failed: $($_.Exception.Message)"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "admin snapshot file read failed"
                error = $_.Exception.Message
                checkedAt = (Get-Date).ToUniversalTime().ToString("o")
                source = "local-admin-snapshot"
            } | ConvertTo-Json -Depth 4)
        }
    }
} else {
    $snapshot = Invoke-RemoteTargetDeviceAdminSnapshot `
        -SshHost $HomeSshHost `
        -ProjectKey $ProjectKey `
        -TargetDeviceUuid $TargetDeviceUuid `
        -ExpectedReleaseId $ExpectedOtaReleaseId `
        -ExpectedVersionCode $ExpectedTargetVersionCode `
        -RecentOnlineMinutes $RecentOnlineMinutes `
        -WarmOnlineMinutes $WarmOnlineMinutes `
        -AcceptedStatuses $AcceptedReportStatuses `
        -FailureStatuses $FailureReportStatuses `
        -RecoverablePattern $RecoverableFailurePattern
}

Write-TextFile -Path (Join-Path $outputDir "target-device-admin-evidence.json") -Content $snapshot.json
$payload = $snapshot.json | ConvertFrom-Json

$status = [string]$payload.status
$detail = [string]$payload.detail
$targetPresence = if ($payload.PSObject.Properties.Name -contains "targetPresence") { $payload.targetPresence } else { $null }
$release = if ($payload.PSObject.Properties.Name -contains "release") { $payload.release } else { $null }
$latestReport = if ($payload.PSObject.Properties.Name -contains "latestReport") { $payload.latestReport } else { $null }
$totals = if ($payload.PSObject.Properties.Name -contains "totals") { $payload.totals } else { $null }

$summary = @"
status=$status
checkedAt=$(Format-IsoValue -Value $payload.checkedAt)
projectKey=$ProjectKey
targetDeviceUuid=$TargetDeviceUuid
expectedOtaReleaseId=$ExpectedOtaReleaseId
expectedTargetVersionCode=$ExpectedTargetVersionCode
outputDir=$outputDir
detail=$detail
targetDevicePresent=$(if ($payload.PSObject.Properties.Name -contains "targetDevicePresent") { $payload.targetDevicePresent } else { "" })
targetPresence=$(if ($targetPresence) { $targetPresence.label } else { "" })
targetHeartbeatAt=$(if ($targetPresence) { Format-IsoValue -Value $targetPresence.heartbeatAt } else { "" })
targetHeartbeatAgeMinutes=$(if ($targetPresence) { $targetPresence.heartbeatAgeMinutes } else { "" })
activeTargetSessionCount=$(if ($payload.PSObject.Properties.Name -contains "activeTargetSessionCount") { $payload.activeTargetSessionCount } else { "" })
releaseFound=$([bool]$release)
releaseVersionCode=$(if ($release) { $release.versionCode } else { "" })
releaseRolloutStatus=$(if ($release) { $release.rolloutStatus } else { "" })
matchingReportCount=$(if ($payload.PSObject.Properties.Name -contains "matchingReportCount") { $payload.matchingReportCount } else { "" })
latestReportStatus=$(if ($latestReport) { $latestReport.status } else { "" })
latestReportUpdatedAt=$(if ($latestReport) { Format-IsoValue -Value $latestReport.updatedAt } else { "" })
deviceTotal=$(if ($totals) { $totals.devices } else { "" })
reportTotal=$(if ($totals) { $totals.reports } else { "" })
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
Write-Host $summary.Trim()

if ($status -eq "FAIL") {
    exit 1
}
if ($status -eq "AUTH_REQUIRED" -and -not $AllowMissingAdminAuth) {
    exit 2
}
if ($status -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
