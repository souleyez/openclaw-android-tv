param(
    [string]$OutputDir = "",
    [switch]$Release,
    [switch]$Platform3128,
    [string]$PlatformSigningDir = "",
    [string]$ApplicationId = "",
    [string]$DistributionKey = "",
    [string]$CountryCode = "CN",
    [string]$RegionCode = "",
    [string]$OutputLabel = "",
    [string]$ExpectedSigningCertSha256 = "2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$androidRoot = Join-Path $repoRoot "apps\android-tv-client-kotlin"
$buildFile = Join-Path $androidRoot "app\build.gradle.kts"
$localSigningProps = Join-Path $repoRoot "artifacts\signing\openclaw-tv-factory-pilot.signing.local.properties"
$defaultPlatformSigningDir = Join-Path $repoRoot "artifacts\signing\3128-platform"
$platformSigningRoot = if ($PlatformSigningDir) { $PlatformSigningDir } else { $defaultPlatformSigningDir }
$targetOutputDir = if ($OutputDir) {
    $OutputDir
} else {
    Join-Path $repoRoot "artifacts\android-tv\factory-pilot"
}

if ($Platform3128 -and -not $Release) {
    throw "-Platform3128 requires -Release"
}

function Resolve-ApkSigner {
    $command = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $sdkRoots = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "C:\Users\soulzyn\develop\android-sdk",
        "$env:LOCALAPPDATA\Android\Sdk"
    ) | Where-Object { $_ }

    foreach ($root in $sdkRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $root -Recurse -File -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    throw "apksigner not found"
}

function Resolve-Aapt {
    $command = Get-Command aapt -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $sdkRoots = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "C:\Users\soulzyn\develop\android-sdk",
        "$env:LOCALAPPDATA\Android\Sdk"
    ) | Where-Object { $_ }

    foreach ($root in $sdkRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $root -Recurse -File -Filter "aapt.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    throw "aapt not found"
}

function Test-ApkSignature {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath,
        [Parameter(Mandatory = $true)][string]$ExpectedCertSha256
    )

    $apksigner = Resolve-ApkSigner
    $text = & $apksigner verify --verbose --print-certs $ApkPath 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "apksigner verify failed for $ApkPath"
    }

    $joined = $text -join "`n"
    $cert = [regex]::Match($joined, "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)").Groups[1].Value.ToLowerInvariant()
    $v1 = $joined -match "Verified using v1 scheme \(JAR signing\):\s*true"
    $v2 = $joined -match "Verified using v2 scheme \(APK Signature Scheme v2\):\s*true"
    $v3 = $joined -match "Verified using v3 scheme \(APK Signature Scheme v3\):\s*true"
    $signers = [regex]::Match($joined, "Number of signers:\s*(\d+)").Groups[1].Value

    if ($ExpectedCertSha256 -and $cert -ne $ExpectedCertSha256.ToLowerInvariant()) {
        throw "Signing certificate mismatch for $ApkPath; actual=$cert expected=$($ExpectedCertSha256.ToLowerInvariant())"
    }

    [pscustomobject]@{
        apksigner = $apksigner
        certSha256 = $cert
        v1 = [bool]$v1
        v2 = [bool]$v2
        v3 = [bool]$v3
        signers = if ($signers) { [int]$signers } else { 0 }
    }
}

function Get-ApkManifestPackageName {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath
    )

    $aapt = Resolve-Aapt
    $text = & $aapt dump badging $ApkPath 2>&1
    $packageLine = $text | Where-Object { $_ -match "^package:\s+name='([^']+)'" } | Select-Object -First 1
    if (-not $packageLine -or $packageLine -notmatch "^package:\s+name='([^']+)'") {
        throw "Unable to resolve APK manifest package for $ApkPath"
    }
    return $matches[1]
}

function Test-ApkContainsTextInDex {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath,
        [Parameter(Mandatory = $true)][string]$Needle
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notlike "classes*.dex") {
                continue
            }
            $stream = $entry.Open()
            try {
                $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::GetEncoding("ISO-8859-1"))
                if ($reader.ReadToEnd().Contains($Needle)) {
                    return $true
                }
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }

    return $false
}

if ($Release -and -not $Platform3128 -and (Test-Path -LiteralPath $localSigningProps)) {
    foreach ($line in Get-Content -LiteralPath $localSigningProps) {
        if ($line -notmatch "^([^=#]+)=(.*)$") {
            continue
        }
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        if ($name -and $value -and -not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$buildText = Get-Content -Raw $buildFile
$versionName = [regex]::Match($buildText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$versionCode = [regex]::Match($buildText, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $versionName -or -not $versionCode) {
    throw "Unable to resolve versionName/versionCode from $buildFile"
}

$expectedApplicationId = if ($ApplicationId.Trim()) { $ApplicationId.Trim() } else { "com.openclaw.tv" }
$resolvedDistributionKey = $DistributionKey.Trim()
$resolvedCountryCode = $CountryCode.Trim().ToUpperInvariant()
$resolvedRegionCode = $RegionCode.Trim().ToUpperInvariant()
$variant = if ($Release) { "Release" } else { "Debug" }
$task = ":app:assemble$variant"
$gradleArgs = @("--console=plain")
$gradleArgs += "-POPENCLAW_APPLICATION_ID=$expectedApplicationId"
$gradleArgs += "-POPENCLAW_DISTRIBUTION_KEY=$resolvedDistributionKey"
$gradleArgs += "-POPENCLAW_COUNTRY_CODE=$resolvedCountryCode"
$gradleArgs += "-POPENCLAW_REGION_CODE=$resolvedRegionCode"
$gradleArgs += "--rerun-tasks"
$gradleArgs += $task
$releaseSigningEnvNames = @(
    "OPENCLAW_RELEASE_STORE_FILE",
    "OPENCLAW_RELEASE_STORE_PASSWORD",
    "OPENCLAW_RELEASE_KEY_ALIAS",
    "OPENCLAW_RELEASE_KEY_PASSWORD"
)
$previousReleaseSigningEnv = @{}

Push-Location $androidRoot
try {
    if ($Platform3128) {
        foreach ($name in $releaseSigningEnvNames) {
            $previousReleaseSigningEnv[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            [Environment]::SetEnvironmentVariable($name, $null, "Process")
        }
    }

    & ".\gradlew.bat" @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $($gradleArgs -join ' ')"
    }
} finally {
    Pop-Location
    if ($Platform3128) {
        foreach ($name in $releaseSigningEnvNames) {
            [Environment]::SetEnvironmentVariable($name, $previousReleaseSigningEnv[$name], "Process")
        }
    }
}

$variantDir = if ($Release) { "release" } else { "debug" }
$apkDir = Join-Path $androidRoot "app\build\outputs\apk\$variantDir"
$outputMetadata = Join-Path $apkDir "output-metadata.json"
if (Test-Path -LiteralPath $outputMetadata) {
    $metadata = Get-Content -Raw -LiteralPath $outputMetadata | ConvertFrom-Json
    $outputApplicationId = [string]$metadata.applicationId
    if ($outputApplicationId -and $outputApplicationId -ne $expectedApplicationId) {
        throw "Output metadata applicationId mismatch; actual=$outputApplicationId expected=$expectedApplicationId"
    }
}
$apkFilter = if ($Platform3128) { "*unsigned*.apk" } else { "*.apk" }
$apk = Get-ChildItem -Path $apkDir -Filter $apkFilter |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    throw "No APK found in $apkDir with filter $apkFilter"
}

New-Item -ItemType Directory -Force -Path $targetOutputDir | Out-Null
$signedMarker = if ($Platform3128) { "3128-platform-signed" } elseif ($apk.Name -like "*unsigned*") { "unsigned" } else { "signed" }
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$labelParts = @()
if ($OutputLabel.Trim()) {
    $labelParts += $OutputLabel.Trim()
}
if ($DistributionKey.Trim()) {
    $labelParts += $DistributionKey.Trim()
}
$label = ($labelParts -join "-").ToLowerInvariant() -replace "[^a-z0-9._-]+", "-"
$labelSegment = if ($label) { "-$label" } else { "" }
$targetName = "openclaw-tv-$versionName-$versionCode$labelSegment-factory-pilot-$($variant.ToLowerInvariant())-$signedMarker-$timestamp.apk"
$targetPath = Join-Path $targetOutputDir $targetName
if ($Platform3128) {
    $platformKey = Join-Path $platformSigningRoot "platform.pk8"
    $platformCert = Join-Path $platformSigningRoot "platform.x509.pem"
    if (-not (Test-Path -LiteralPath $platformKey)) {
        throw "Missing 3128 platform key: $platformKey"
    }
    if (-not (Test-Path -LiteralPath $platformCert)) {
        throw "Missing 3128 platform certificate: $platformCert"
    }

    $apksigner = Resolve-ApkSigner
    & $apksigner sign `
        --key $platformKey `
        --cert $platformCert `
        --v1-signing-enabled true `
        --v2-signing-enabled true `
        --v3-signing-enabled true `
        --out $targetPath `
        $apk.FullName
    if ($LASTEXITCODE -ne 0) {
        throw "apksigner sign failed"
    }
} else {
    Copy-Item -LiteralPath $apk.FullName -Destination $targetPath
}

$signature = $null
if ($Release -and $signedMarker -ne "unsigned") {
    $signature = Test-ApkSignature -ApkPath $targetPath -ExpectedCertSha256 $ExpectedSigningCertSha256
}

$manifestPackage = Get-ApkManifestPackageName -ApkPath $targetPath
if ($manifestPackage -ne $expectedApplicationId) {
    throw "Signed APK manifest package mismatch; actual=$manifestPackage expected=$expectedApplicationId"
}

$distributionKeyPresent = $null
if ($resolvedDistributionKey) {
    $distributionKeyPresent = Test-ApkContainsTextInDex -ApkPath $targetPath -Needle $resolvedDistributionKey
    if (-not $distributionKeyPresent) {
        throw "Signed APK does not contain distribution key: $resolvedDistributionKey"
    }
}

$sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetPath).Hash.ToLowerInvariant()
$shaPath = "$targetPath.sha256.txt"
Set-Content -LiteralPath $shaPath -Encoding UTF8 -Value "$sha256  $targetName"

[pscustomobject]@{
    versionName = $versionName
    versionCode = [long]$versionCode
    variant = $variant
    applicationId = $expectedApplicationId
    distributionKey = $resolvedDistributionKey
    countryCode = $resolvedCountryCode
    regionCode = $resolvedRegionCode
    manifestPackage = $manifestPackage
    distributionKeyPresent = $distributionKeyPresent
    apk = $targetPath
    sha256 = $sha256
    sha256File = $shaPath
    signing = $signature
} | ConvertTo-Json -Depth 3
