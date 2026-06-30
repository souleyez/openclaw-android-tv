param(
    [string]$OutputRoot = "",
    [string]$ApiBaseUrl = "https://oc.goods-editor.com",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [int]$ExpectedTargetVersionCode = 2026070101,
    [string[]]$AcceptedReportStatuses = @("verified", "installed", "reported"),
    [string]$AdminToken = "",
    [string]$AdminSession = "",
    [string]$HomeSshHost = "root@8.155.8.7",
    [switch]$SkipRemoteAdminFallback,
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

function Get-FirstNonEmptyEnv {
    param([string[]]$Names)
    foreach ($name in $Names) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
    }
    return ""
}

function Get-JsonContent {
    param([object]$Content)
    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function Get-ReportTimestamp {
    param([object]$Report)
    $candidate = ""
    if ($Report -and $Report.PSObject.Properties.Name -contains "updatedAt") {
        $candidate = [string]$Report.updatedAt
    }
    if ([string]::IsNullOrWhiteSpace($candidate) -and $Report -and $Report.PSObject.Properties.Name -contains "reportedAt") {
        $candidate = [string]$Report.reportedAt
    }
    $parsed = [datetime]::MinValue
    if ([datetime]::TryParse($candidate, [ref]$parsed)) {
        return $parsed
    }
    return [datetime]::MinValue
}

function Get-ObjectPropertyValue {
    param(
        [object]$Object,
        [string]$Name,
        [object]$Fallback
    )
    if ($Object -and $Object.PSObject.Properties.Name -contains $Name) {
        $value = $Object.$Name
        if ($null -ne $value) {
            return $value
        }
    }
    return $Fallback
}

function Write-SummaryAndExit {
    param(
        [string]$Status,
        [string]$Detail,
        [int]$ExitCode
    )
    $summary = @"
status=$Status
checkedAt=$((Get-Date).ToUniversalTime().ToString("o"))
projectKey=$ProjectKey
targetDeviceUuid=$TargetDeviceUuid
expectedOtaReleaseId=$ExpectedOtaReleaseId
expectedTargetVersionCode=$ExpectedTargetVersionCode
detail=$Detail
outputDir=$outputDir
"@
    Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
    Write-Host "[$Status] $Detail"
    exit $ExitCode
}

function ConvertTo-BashLiteral {
    param([string]$Value)

    if ($null -eq $Value) {
        return "''"
    }
    return "'" + $Value.Replace("'", "'\''") + "'"
}

function Invoke-RemoteOtaCanarySnapshot {
    param(
        [string]$SshHost,
        [string]$ProjectKey,
        [string]$TargetDeviceUuid,
        [string]$ExpectedReleaseId,
        [int]$ExpectedVersionCode,
        [string[]]$AcceptedStatuses
    )

    $acceptedCsv = ($AcceptedStatuses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $remoteScript = @'
set -euo pipefail
PROJECT_KEY="__PROJECT_KEY__"
TARGET_DEVICE_UUID="__TARGET_DEVICE_UUID__"
EXPECTED_RELEASE_ID="__EXPECTED_RELEASE_ID__"
EXPECTED_TARGET_VERSION_CODE="__EXPECTED_TARGET_VERSION_CODE__"
ACCEPTED_STATUSES="__ACCEPTED_STATUSES__"
export PROJECT_KEY TARGET_DEVICE_UUID EXPECTED_RELEASE_ID EXPECTED_TARGET_VERSION_CODE ACCEPTED_STATUSES

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
  node -e 'console.log(JSON.stringify({status:"AUTH_REQUIRED", detail:"admin auth env var not present on remote host", checkedAt:new Date().toISOString()}, null, 2))'
  exit 0
fi

curl -fsS -H "$admin_header_name: $admin_secret" "http://127.0.0.1:3210/api/admin/ota?projectKey=$PROJECT_KEY" | node -e '
const fs = require("fs");
const data = JSON.parse(fs.readFileSync(0, "utf8"));
const releaseId = process.env.EXPECTED_RELEASE_ID;
const target = process.env.TARGET_DEVICE_UUID;
const expectedVersionCode = Number(process.env.EXPECTED_TARGET_VERSION_CODE || 0);
const accepted = new Set((process.env.ACCEPTED_STATUSES || "").split(",").map((item) => item.trim().toLowerCase()).filter(Boolean));
const release = (data.releases || []).find((item) => item.id === releaseId) || null;
const reports = (data.reports || []).filter((item) => item.releaseId === releaseId && item.deviceUuid === target);
reports.sort((left, right) => Date.parse(right.updatedAt || right.reportedAt || 0) - Date.parse(left.updatedAt || left.reportedAt || 0));
const latest = reports[0] || null;
const releaseMatches = Boolean(release) && Number(release.versionCode || 0) === expectedVersionCode;
let status = "PENDING";
let detail = "release found, but target device has not reported OTA lifecycle yet";
if (!releaseMatches) {
  status = "FAIL";
  detail = `expected release not found or version mismatch; found=${Boolean(release)}; versionCode=${release ? release.versionCode : 0}`;
} else if (latest && accepted.has(String(latest.status || "").toLowerCase())) {
  status = "PASS";
  detail = `target device report status=${latest.status}`;
} else if (latest) {
  detail = `latest target report status=${latest.status || "unknown"}`;
}
console.log(JSON.stringify({
  status,
  detail,
  projectKey: process.env.PROJECT_KEY,
  targetDeviceUuid: target,
  expectedOtaReleaseId: releaseId,
  expectedTargetVersionCode: expectedVersionCode,
  release: release ? {
    id: release.id,
    versionName: release.versionName,
    versionCode: release.versionCode,
    rolloutStatus: release.rolloutStatus,
    targetScope: release.targetScope,
    installPolicy: release.installPolicy,
    artifactSha256: release.artifactSha256
  } : null,
  latestReport: latest ? {
    releaseId: latest.releaseId,
    deviceUuid: latest.deviceUuid,
    currentVersionCode: latest.currentVersionCode,
    targetVersionCode: latest.targetVersionCode,
    status: latest.status,
    progressPercent: latest.progressPercent,
    note: latest.note,
    reportedAt: latest.reportedAt,
    updatedAt: latest.updatedAt
  } : null,
  totals: {
    releases: (data.totals && data.totals.releases) || (data.releases || []).length,
    rolling: (data.totals && data.totals.rolling) || 0,
    reports: (data.totals && data.totals.reports) || (data.reports || []).length,
    matchingReports: reports.length
  },
  acceptedReportStatuses: (process.env.ACCEPTED_STATUSES || "").split(",").filter(Boolean),
  checkedAt: new Date().toISOString(),
  source: "remote-admin"
}, null, 2));
'
'@
    $remoteScript = $remoteScript.Replace('"__PROJECT_KEY__"', (ConvertTo-BashLiteral -Value $ProjectKey))
    $remoteScript = $remoteScript.Replace('"__TARGET_DEVICE_UUID__"', (ConvertTo-BashLiteral -Value $TargetDeviceUuid))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_RELEASE_ID__"', (ConvertTo-BashLiteral -Value $ExpectedReleaseId))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_TARGET_VERSION_CODE__"', (ConvertTo-BashLiteral -Value ([string]$ExpectedVersionCode)))
    $remoteScript = $remoteScript.Replace('"__ACCEPTED_STATUSES__"', (ConvertTo-BashLiteral -Value $acceptedCsv))

    $output = $remoteScript | & ssh $SshHost "bash -s" 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        $payload = [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check failed"
            error = $text
            checkedAt = (Get-Date).ToUniversalTime().ToString("o")
            source = "remote-admin"
        }
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check failed"
            json = ($payload | ConvertTo-Json -Depth 4)
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
        $payload = [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check returned invalid JSON"
            checkedAt = (Get-Date).ToUniversalTime().ToString("o")
            source = "remote-admin"
        }
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check returned invalid JSON"
            json = ($payload | ConvertTo-Json -Depth 4)
        }
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\service-checks\ota-canary-report-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

if ([string]::IsNullOrWhiteSpace($AdminSession)) {
    $AdminSession = Get-FirstNonEmptyEnv -Names @("CONTROL_PLANE_ADMIN_SESSION", "CP_ADMIN_SESSION")
}
if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    $AdminToken = Get-FirstNonEmptyEnv -Names @("CONTROL_PLANE_ADMIN_TOKEN", "HOME_ADMIN_TOKEN", "CP_ADMIN_TOKEN")
}

if ([string]::IsNullOrWhiteSpace($AdminSession) -and [string]::IsNullOrWhiteSpace($AdminToken) -and -not $SkipRemoteAdminFallback) {
    $remoteSnapshot = Invoke-RemoteOtaCanarySnapshot `
        -SshHost $HomeSshHost `
        -ProjectKey $ProjectKey `
        -TargetDeviceUuid $TargetDeviceUuid `
        -ExpectedReleaseId $ExpectedOtaReleaseId `
        -ExpectedVersionCode $ExpectedTargetVersionCode `
        -AcceptedStatuses $AcceptedReportStatuses
    Write-TextFile -Path (Join-Path $outputDir "target-ota-report.json") -Content $remoteSnapshot.json
    $exitCode = switch ($remoteSnapshot.status) {
        "PASS" { 0 }
        "PENDING" { if ($AllowPending) { 0 } else { 1 } }
        "AUTH_REQUIRED" { if ($AllowMissingAdminAuth) { 0 } else { 2 } }
        default { 1 }
    }
    Write-SummaryAndExit -Status $remoteSnapshot.status -Detail "remote=$($remoteSnapshot.detail)" -ExitCode $exitCode
}

if ([string]::IsNullOrWhiteSpace($AdminSession) -and [string]::IsNullOrWhiteSpace($AdminToken)) {
    $snapshot = [pscustomobject]@{
        status = "AUTH_REQUIRED"
        projectKey = $ProjectKey
        targetDeviceUuid = $TargetDeviceUuid
        expectedOtaReleaseId = $ExpectedOtaReleaseId
        expectedTargetVersionCode = $ExpectedTargetVersionCode
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        requiredAuth = "Set CONTROL_PLANE_ADMIN_SESSION or CONTROL_PLANE_ADMIN_TOKEN in the local environment."
    }
    $snapshot | ConvertTo-Json -Depth 4 | Out-File -FilePath (Join-Path $outputDir "target-ota-report.json") -Encoding utf8
    $exitCode = if ($AllowMissingAdminAuth) { 0 } else { 2 }
    Write-SummaryAndExit -Status "AUTH_REQUIRED" -Detail "admin auth env var not present; canary report not checked" -ExitCode $exitCode
}

$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($AdminSession)) {
    $headers["X-Control-Plane-Admin-Session"] = $AdminSession
} else {
    $headers["X-Control-Plane-Admin-Token"] = $AdminToken
}

$apiBase = $ApiBaseUrl.TrimEnd("/")
$snapshotUrl = "$apiBase/api/admin/ota?projectKey=$([uri]::EscapeDataString($ProjectKey))"

try {
    $response = Invoke-WebRequest -Uri $snapshotUrl -Headers $headers -UseBasicParsing -TimeoutSec 20
    $body = Get-JsonContent -Content $response.Content
    $payload = $body | ConvertFrom-Json
} catch {
    $message = $_.Exception.Message
    $snapshot = [pscustomobject]@{
        status = "FAIL"
        projectKey = $ProjectKey
        targetDeviceUuid = $TargetDeviceUuid
        expectedOtaReleaseId = $ExpectedOtaReleaseId
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        error = $message
    }
    $snapshot | ConvertTo-Json -Depth 4 | Out-File -FilePath (Join-Path $outputDir "target-ota-report.json") -Encoding utf8
    Write-SummaryAndExit -Status "FAIL" -Detail "admin OTA snapshot request failed: $message" -ExitCode 1
}

$releases = @($payload.releases)
$reports = @($payload.reports)
$release = $releases | Where-Object { $_.id -eq $ExpectedOtaReleaseId } | Select-Object -First 1
$matchingReports = @($reports | Where-Object {
    $_.releaseId -eq $ExpectedOtaReleaseId -and $_.deviceUuid -eq $TargetDeviceUuid
} | Sort-Object -Property @{ Expression = { Get-ReportTimestamp -Report $_ }; Descending = $true })
$latestReport = $matchingReports | Select-Object -First 1
$latestStatus = if ($latestReport) { [string]$latestReport.status } else { "" }
$releaseVersionCode = if ($release) { [int](Get-ObjectPropertyValue -Object $release -Name "versionCode" -Fallback 0) } else { 0 }
$releaseStatus = if ($release) { [string](Get-ObjectPropertyValue -Object $release -Name "rolloutStatus" -Fallback "") } else { "" }
$acceptedSet = @{}
foreach ($status in $AcceptedReportStatuses) {
    if (-not [string]::IsNullOrWhiteSpace($status)) {
        $acceptedSet[$status.Trim().ToLowerInvariant()] = $true
    }
}
$accepted = $acceptedSet.ContainsKey($latestStatus.Trim().ToLowerInvariant())
$releaseMatches = $release -and $releaseVersionCode -eq $ExpectedTargetVersionCode

$snapshotResult = [pscustomobject]@{
    status = if ($accepted) { "PASS" } elseif ($latestReport) { "PENDING" } else { "PENDING" }
    projectKey = $ProjectKey
    targetDeviceUuid = $TargetDeviceUuid
    expectedOtaReleaseId = $ExpectedOtaReleaseId
    expectedTargetVersionCode = $ExpectedTargetVersionCode
    release = if ($release) {
        [pscustomobject]@{
            id = $release.id
            versionName = $release.versionName
            versionCode = $releaseVersionCode
            rolloutStatus = $releaseStatus
            targetScope = $release.targetScope
            installPolicy = $release.installPolicy
            artifactSha256 = $release.artifactSha256
        }
    } else {
        $null
    }
    latestReport = if ($latestReport) {
        [pscustomobject]@{
            releaseId = $latestReport.releaseId
            deviceUuid = $latestReport.deviceUuid
            currentVersionCode = $latestReport.currentVersionCode
            targetVersionCode = $latestReport.targetVersionCode
            status = $latestReport.status
            progressPercent = $latestReport.progressPercent
            note = $latestReport.note
            reportedAt = $latestReport.reportedAt
            updatedAt = $latestReport.updatedAt
        }
    } else {
        $null
    }
    totals = [pscustomobject]@{
        releases = [int](Get-ObjectPropertyValue -Object $payload.totals -Name "releases" -Fallback $releases.Count)
        rolling = [int](Get-ObjectPropertyValue -Object $payload.totals -Name "rolling" -Fallback 0)
        reports = [int](Get-ObjectPropertyValue -Object $payload.totals -Name "reports" -Fallback $reports.Count)
        matchingReports = $matchingReports.Count
    }
    acceptedReportStatuses = $AcceptedReportStatuses
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$snapshotResult | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "target-ota-report.json") -Encoding utf8

if (-not $releaseMatches) {
    $detail = "expected release not found or version mismatch; found=$([bool]$release); versionCode=$releaseVersionCode"
    Write-SummaryAndExit -Status "FAIL" -Detail $detail -ExitCode 1
}
if (-not $latestReport) {
    $exitCode = if ($AllowPending) { 0 } else { 1 }
    Write-SummaryAndExit -Status "PENDING" -Detail "release found, but target device has not reported OTA lifecycle yet" -ExitCode $exitCode
}
if (-not $accepted) {
    $exitCode = if ($AllowPending) { 0 } else { 1 }
    Write-SummaryAndExit -Status "PENDING" -Detail "latest target report status=$latestStatus; accepted=$($AcceptedReportStatuses -join ',')" -ExitCode $exitCode
}

Write-SummaryAndExit -Status "PASS" -Detail "target device report status=$latestStatus; release=$ExpectedOtaReleaseId" -ExitCode 0
