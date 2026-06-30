param(
    [string]$OutputRoot = "",
    [string]$ApiBaseUrl = "https://oc.goods-editor.com",
    [string]$AdHealthUrl = "https://gm.goods-editor.com/ads/_health.txt",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [string]$NonTargetDeviceUuid = "not-factory-pilot-device",
    [int]$CurrentVersionCode = 2026062401,
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [string]$ExpectedArtifactSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [int64]$ExpectedArtifactSize = 12119959,
    [int]$CertificateWarnDays = 30
)

$ErrorActionPreference = "Stop"

function New-CheckResult {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    [pscustomobject]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }
}

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content
    )
    $Content | Out-File -FilePath $Path -Encoding utf8
}

function Get-CertificateExpiry {
    param([string]$HostName)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect($HostName, 443)
        $stream = [System.Net.Security.SslStream]::new($client.GetStream(), $false, ({ $true } -as [System.Net.Security.RemoteCertificateValidationCallback]))
        $stream.AuthenticateAsClient($HostName)
        $cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($stream.RemoteCertificate)
        return $cert.NotAfter
    } finally {
        if ($stream) {
            $stream.Dispose()
        }
        $client.Dispose()
    }
}

function Get-Json {
    param([string]$Url)
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20
    return @{
        StatusCode = [int]$response.StatusCode
        Body = $response.Content
        Json = $response.Content | ConvertFrom-Json
    }
}

function Get-Head {
    param([string]$Url)
    return Invoke-WebRequest -Uri $Url -Method Head -UseBasicParsing -TimeoutSec 20
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\service-checks\production-services-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$apiBase = $ApiBaseUrl.TrimEnd("/")
$artifactUrl = "$apiBase/storage/ota/openclaw-android-tv/OpenClawTV-0.1.15.apk"
$artifactShaUrl = "$artifactUrl.sha256.txt"
$targetBootstrapUrl = "$apiBase/api/ota/bootstrap?projectKey=$ProjectKey&deviceUuid=$TargetDeviceUuid&currentVersionCode=$CurrentVersionCode&currentConfigVersion=0"
$nonTargetBootstrapUrl = "$apiBase/api/ota/bootstrap?projectKey=$ProjectKey&deviceUuid=$NonTargetDeviceUuid&currentVersionCode=$CurrentVersionCode&currentConfigVersion=0"
$results = @()

try {
    $health = Get-Json -Url "$apiBase/api/health"
    Write-TextFile -Path (Join-Path $outputDir "health.json") -Content $health.Body
    $results += New-CheckResult -Name "home health" -Passed ($health.StatusCode -eq 200 -and $health.Json.status -eq "ok") -Detail "$($health.StatusCode) $($health.Json.status)"
} catch {
    $results += New-CheckResult -Name "home health" -Passed $false -Detail $_.Exception.Message
}

try {
    $uri = [Uri]$apiBase
    $expiresAt = Get-CertificateExpiry -HostName $uri.Host
    $daysLeft = [Math]::Floor(($expiresAt.ToUniversalTime() - (Get-Date).ToUniversalTime()).TotalDays)
    $results += New-CheckResult -Name "home certificate" -Passed ($daysLeft -ge $CertificateWarnDays) -Detail "expires=$($expiresAt.ToString("o")); daysLeft=$daysLeft"
} catch {
    $results += New-CheckResult -Name "home certificate" -Passed $false -Detail $_.Exception.Message
}

try {
    $artifactHead = Get-Head -Url $artifactUrl
    $contentLength = [int64]($artifactHead.Headers["Content-Length"] | Select-Object -First 1)
    $contentType = [string]($artifactHead.Headers["Content-Type"] | Select-Object -First 1)
    $artifactHeadPassed = [int]$artifactHead.StatusCode -eq 200 -and $contentLength -eq $ExpectedArtifactSize
    $results += New-CheckResult -Name "ota artifact head" -Passed $artifactHeadPassed -Detail "status=$($artifactHead.StatusCode); contentLength=$contentLength; contentType=$contentType"
} catch {
    $results += New-CheckResult -Name "ota artifact head" -Passed $false -Detail $_.Exception.Message
}

try {
    $shaResponse = Invoke-WebRequest -Uri $artifactShaUrl -UseBasicParsing -TimeoutSec 20
    $shaContent = if ($shaResponse.Content -is [byte[]]) {
        [System.Text.Encoding]::UTF8.GetString($shaResponse.Content)
    } else {
        [string]$shaResponse.Content
    }
    $shaText = $shaContent.Trim()
    Write-TextFile -Path (Join-Path $outputDir "artifact.sha256.txt") -Content $shaText
    $results += New-CheckResult -Name "ota artifact sha" -Passed ($shaText -like "$ExpectedArtifactSha256*") -Detail $shaText
} catch {
    $results += New-CheckResult -Name "ota artifact sha" -Passed $false -Detail $_.Exception.Message
}

try {
    $adHealth = Invoke-WebRequest -Uri $AdHealthUrl -UseBasicParsing -TimeoutSec 20
    Write-TextFile -Path (Join-Path $outputDir "ad-health.txt") -Content $adHealth.Content.Trim()
    $results += New-CheckResult -Name "ad asset health" -Passed ($adHealth.Content.Trim() -eq "gm-ad-assets-ok") -Detail $adHealth.Content.Trim()
} catch {
    $results += New-CheckResult -Name "ad asset health" -Passed $false -Detail $_.Exception.Message
}

try {
    $targetBootstrap = Get-Json -Url $targetBootstrapUrl
    Write-TextFile -Path (Join-Path $outputDir "ota-bootstrap-target.json") -Content $targetBootstrap.Body
    $targetAvailable = $targetBootstrap.Json.ota.available -eq $true
    $targetReleaseMatches = $targetBootstrap.Json.ota.release.id -eq $ExpectedOtaReleaseId
    $targetShaMatches = $targetBootstrap.Json.ota.release.artifactSha256 -eq $ExpectedArtifactSha256
    $targetPassed = $targetAvailable -and $targetReleaseMatches -and $targetShaMatches
    $results += New-CheckResult -Name "ota target scope" -Passed $targetPassed -Detail "available=$($targetBootstrap.Json.ota.available); release=$($targetBootstrap.Json.ota.release.id)"
} catch {
    $results += New-CheckResult -Name "ota target scope" -Passed $false -Detail $_.Exception.Message
}

try {
    $nonTargetBootstrap = Get-Json -Url $nonTargetBootstrapUrl
    Write-TextFile -Path (Join-Path $outputDir "ota-bootstrap-non-target.json") -Content $nonTargetBootstrap.Body
    $results += New-CheckResult -Name "ota non-target scope" -Passed ($nonTargetBootstrap.Json.ota.available -eq $false) -Detail "available=$($nonTargetBootstrap.Json.ota.available)"
} catch {
    $results += New-CheckResult -Name "ota non-target scope" -Passed $false -Detail $_.Exception.Message
}

$failed = @($results | Where-Object { -not $_.passed })
$summary = @"
status=$(if ($failed.Count -eq 0) { "PASS" } else { "FAIL" })
checkedAt=$((Get-Date).ToUniversalTime().ToString("o"))
outputDir=$outputDir
failedCount=$($failed.Count)
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary
$results | ConvertTo-Json -Depth 4 | Out-File -FilePath (Join-Path $outputDir "results.json") -Encoding utf8

foreach ($result in $results) {
    $prefix = if ($result.passed) { "PASS" } else { "FAIL" }
    Write-Host "[$prefix] $($result.name): $($result.detail)"
}
Write-Host "Summary: $($summary.Trim())"

if ($failed.Count -gt 0) {
    exit 1
}
