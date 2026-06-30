param(
    [string]$OutputRoot = "",
    [string]$FactoryApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$OtaApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.15.apk",
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [int64]$ExpectedOtaApkSize = 12119959,
    [string]$FactoryFeedbackPath = "",
    [string]$VendorPermissionPath = "",
    [string]$HomeSshHost = "root@8.155.8.7",
    [string]$ExpectedHomeCommit = "bd61b95",
    [switch]$SkipHomeDeploymentCheck,
    [switch]$SkipAdbCheck,
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

function Add-Gate {
    param(
        [System.Collections.ArrayList]$List,
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$EvidencePath = ""
    )
    [void]$List.Add([pscustomobject]@{
        name = $Name
        status = $Status
        detail = $Detail
        evidencePath = $EvidencePath
    })
}

function Get-SummaryMap {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path $Path)) {
        return $map
    }
    foreach ($line in Get-Content -Path $Path) {
        $index = $line.IndexOf("=")
        if ($index -gt 0) {
            $key = $line.Substring(0, $index).Trim()
            $value = $line.Substring($index + 1).Trim()
            $map[$key] = $value
        }
    }
    return $map
}

function Get-FileHashStatus {
    param(
        [string]$Path,
        [string]$ExpectedSha256,
        [int64]$ExpectedSize = 0
    )
    if (-not (Test-Path $Path)) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "missing file: $Path"
        }
    }
    $resolved = (Resolve-Path $Path).Path
    $hash = (Get-FileHash -Algorithm SHA256 -Path $resolved).Hash.ToLowerInvariant()
    $size = (Get-Item -LiteralPath $resolved).Length
    $expected = $ExpectedSha256.ToLowerInvariant()
    if ($hash -ne $expected) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "sha256 mismatch; expected=$expected actual=$hash path=$resolved"
        }
    }
    if ($ExpectedSize -gt 0 -and $size -ne $ExpectedSize) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "size mismatch; expected=$ExpectedSize actual=$size path=$resolved"
        }
    }
    return [pscustomobject]@{
        status = "PASS"
        detail = "sha256=$hash size=$size path=$resolved"
    }
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

function Test-AdbOnlineDevice {
    $output = & adb devices -l 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "adb failed: $text"
            output = $text
        }
    }
    $deviceLines = @($text -split "`r?`n" | Where-Object {
        $line = $_.Trim()
        $line -and $line -notmatch "^List of devices"
    })
    $online = @($deviceLines | Where-Object { $_ -match "\sdevice\s" })
    if ($online.Count -gt 0) {
        return [pscustomobject]@{
            status = "PASS"
            detail = "online devices=$($online.Count)"
            output = $text
        }
    }
    return [pscustomobject]@{
        status = "PENDING"
        detail = "no online adb device"
        output = $text
    }
}

function Test-HomeDeployment {
    param(
        [string]$SshHost,
        [string]$ExpectedCommit
    )
    $remote = 'cd /srv/home/repo && git rev-parse --short HEAD && systemctl is-active home-platform-api.service home-public-admin.service lease-core.service fleet-core.service'
    $output = & ssh $SshHost $remote 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "ssh home deployment check failed: $text"
            output = $text
        }
    }
    $lines = @($text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $head = if ($lines.Count -gt 0) { $lines[0].Trim() } else { "" }
    $services = @($lines | Select-Object -Skip 1)
    $allActive = ($services.Count -ge 4) -and -not ($services | Where-Object { $_.Trim() -ne "active" })
    $commitOk = $head -eq $ExpectedCommit
    if ($commitOk -and $allActive) {
        return [pscustomobject]@{
            status = "PASS"
            detail = "home=$head services=active"
            output = $text
        }
    }
    return [pscustomobject]@{
        status = "FAIL"
        detail = "home head or services mismatch; expected=$ExpectedCommit actual=$head services=$($services -join ',')"
        output = $text
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\factory-pilot-gates\gate-check-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$gates = New-Object System.Collections.ArrayList

$factoryApk = Get-FileHashStatus -Path $FactoryApkPath -ExpectedSha256 $ExpectedFactoryApkSha256 -ExpectedSize 0
Add-Gate -List $gates -Name "factory apk hash" -Status $factoryApk.status -Detail $factoryApk.detail

$otaApk = Get-FileHashStatus -Path $OtaApkPath -ExpectedSha256 $ExpectedOtaApkSha256 -ExpectedSize $ExpectedOtaApkSize
Add-Gate -List $gates -Name "ota apk hash" -Status $otaApk.status -Detail $otaApk.detail

$serviceOutputRoot = Join-Path $outputDir "production-services"
$serviceCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-production-services.ps1") `
    -Arguments @("-OutputRoot", $serviceOutputRoot) `
    -LogPath (Join-Path $outputDir "production-services.log")
$serviceSummary = Get-SummaryMap -Path (Join-Path $serviceOutputRoot "summary.txt")
$serviceStatus = if ($serviceSummary.ContainsKey("status")) { $serviceSummary["status"] } elseif ($serviceCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$serviceFailedCount = if ($serviceSummary.ContainsKey("failedCount")) { $serviceSummary["failedCount"] } else { "" }
Add-Gate -List $gates -Name "production services" -Status $serviceStatus -Detail "exit=$($serviceCheck.exitCode); failedCount=$serviceFailedCount" -EvidencePath $serviceOutputRoot

$canaryOutputRoot = Join-Path $outputDir "ota-canary-report"
$canaryCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-ota-canary-report.ps1") `
    -Arguments @("-OutputRoot", $canaryOutputRoot, "-AllowMissingAdminAuth") `
    -LogPath (Join-Path $outputDir "ota-canary-report.log")
$canarySummary = Get-SummaryMap -Path (Join-Path $canaryOutputRoot "summary.txt")
$canaryStatus = if ($canarySummary.ContainsKey("status")) { $canarySummary["status"] } elseif ($canaryCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$canaryDetail = if ($canarySummary.ContainsKey("detail")) { $canarySummary["detail"] } else { "" }
Add-Gate -List $gates -Name "ota installed report" -Status $canaryStatus -Detail "exit=$($canaryCheck.exitCode); $canaryDetail" -EvidencePath $canaryOutputRoot

if ($SkipAdbCheck) {
    Add-Gate -List $gates -Name "adb online device" -Status "SKIPPED" -Detail "skipped by flag"
} else {
    $adb = Test-AdbOnlineDevice
    Write-TextFile -Path (Join-Path $outputDir "adb-devices.txt") -Content $adb.output
    Add-Gate -List $gates -Name "adb online device" -Status $adb.status -Detail $adb.detail -EvidencePath (Join-Path $outputDir "adb-devices.txt")
}

if ($SkipHomeDeploymentCheck) {
    Add-Gate -List $gates -Name "home deployment" -Status "SKIPPED" -Detail "skipped by flag"
} else {
    $homeDeployment = Test-HomeDeployment -SshHost $HomeSshHost -ExpectedCommit $ExpectedHomeCommit
    Write-TextFile -Path (Join-Path $outputDir "home-deployment.txt") -Content $homeDeployment.output
    Add-Gate -List $gates -Name "home deployment" -Status $homeDeployment.status -Detail $homeDeployment.detail -EvidencePath (Join-Path $outputDir "home-deployment.txt")
}

if ([string]::IsNullOrWhiteSpace($FactoryFeedbackPath)) {
    Add-Gate -List $gates -Name "factory fresh feedback" -Status "PENDING" -Detail "no factory feedback path provided"
} elseif (-not (Test-Path $FactoryFeedbackPath)) {
    Add-Gate -List $gates -Name "factory fresh feedback" -Status "FAIL" -Detail "feedback file not found: $FactoryFeedbackPath"
} else {
    $factoryOutputRoot = Join-Path $outputDir "factory-feedback"
    $factoryCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-factory-feedback.ps1") `
        -Arguments @("-FeedbackPath", (Resolve-Path $FactoryFeedbackPath).Path, "-OutputRoot", $factoryOutputRoot) `
        -LogPath (Join-Path $outputDir "factory-feedback.log")
    $factorySummary = Get-SummaryMap -Path (Join-Path $factoryOutputRoot "summary.txt")
    $conclusion = if ($factorySummary.ContainsKey("recommendedConclusion")) { $factorySummary["recommendedConclusion"] } else { "" }
    $factoryAction = if ($factorySummary.ContainsKey("requiredFactoryAction")) { $factorySummary["requiredFactoryAction"] } else { "" }
    $status = switch -Regex ($conclusion) {
        "^PASS A$" { "PASS"; break }
        "^PASS B$" { "PENDING"; break }
        "^BLOCKED" { "FAIL"; break }
        default { "PENDING" }
    }
    Add-Gate -List $gates -Name "factory fresh feedback" -Status $status -Detail "conclusion=$conclusion; action=$factoryAction" -EvidencePath $factoryOutputRoot
}

if ([string]::IsNullOrWhiteSpace($VendorPermissionPath)) {
    Add-Gate -List $gates -Name "vendor permission decision" -Status "PENDING" -Detail "no vendor permission feedback path provided"
} elseif (-not (Test-Path $VendorPermissionPath)) {
    Add-Gate -List $gates -Name "vendor permission decision" -Status "FAIL" -Detail "feedback file not found: $VendorPermissionPath"
} else {
    $vendorOutputRoot = Join-Path $outputDir "vendor-permission"
    $vendorCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-classify-vendor-permission.ps1") `
        -Arguments @("-FeedbackPath", (Resolve-Path $VendorPermissionPath).Path, "-OutputRoot", $vendorOutputRoot) `
        -LogPath (Join-Path $outputDir "vendor-permission.log")
    $vendorSummary = Get-SummaryMap -Path (Join-Path $vendorOutputRoot "summary.txt")
    $decision = if ($vendorSummary.ContainsKey("recommendedDecision")) { $vendorSummary["recommendedDecision"] } else { "" }
    $vendorAction = if ($vendorSummary.ContainsKey("requiredAction")) { $vendorSummary["requiredAction"] } else { "" }
    $status = switch -Regex ($decision) {
        "^APK-only acceptable$" { "PASS"; break }
        "^blocked$" { "FAIL"; break }
        "^INCOMPLETE$" { "PENDING"; break }
        default { "PENDING" }
    }
    Add-Gate -List $gates -Name "vendor permission decision" -Status $status -Detail "decision=$decision; action=$vendorAction" -EvidencePath $vendorOutputRoot
}

$failures = @($gates | Where-Object { $_.status -eq "FAIL" })
$pending = @($gates | Where-Object { $_.status -eq "PENDING" -or $_.status -eq "AUTH_REQUIRED" })
$overallStatus = if ($failures.Count -gt 0) {
    "FAIL"
} elseif ($pending.Count -gt 0) {
    "PENDING"
} else {
    "PASS"
}

$result = [pscustomobject]@{
    status = $overallStatus
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    outputDir = $outputDir
    gates = $gates
    failedCount = $failures.Count
    pendingCount = $pending.Count
}
$result | ConvertTo-Json -Depth 6 | Out-File -FilePath (Join-Path $outputDir "factory-pilot-gates.json") -Encoding utf8

$summary = @"
status=$overallStatus
checkedAt=$($result.checkedAt)
outputDir=$outputDir
failedCount=$($failures.Count)
pendingCount=$($pending.Count)
"@
Write-TextFile -Path (Join-Path $outputDir "summary.txt") -Content $summary

foreach ($gate in $gates) {
    Write-Host "[$($gate.status)] $($gate.name): $($gate.detail)"
}
Write-Host "Summary: $($summary.Trim())"

if ($overallStatus -eq "FAIL") {
    exit 1
}
if ($overallStatus -eq "PENDING" -and -not $AllowPending) {
    exit 2
}
