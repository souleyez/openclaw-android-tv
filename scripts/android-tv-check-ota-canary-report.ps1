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
    Write-SummaryAndExit -Status "PENDING" -Detail "release found, but target device has not reported OTA lifecycle yet" -ExitCode 1
}
if (-not $accepted) {
    Write-SummaryAndExit -Status "PENDING" -Detail "latest target report status=$latestStatus; accepted=$($AcceptedReportStatuses -join ',')" -ExitCode 1
}

Write-SummaryAndExit -Status "PASS" -Detail "target device report status=$latestStatus; release=$ExpectedOtaReleaseId" -ExitCode 0
