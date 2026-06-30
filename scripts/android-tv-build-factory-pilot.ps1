param(
    [string]$OutputDir = "",
    [switch]$Release,
    [switch]$Platform3128,
    [string]$PlatformSigningDir = "",
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

$variant = if ($Release) { "Release" } else { "Debug" }
$task = ":app:assemble$variant"
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

    & ".\gradlew.bat" $task "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $task"
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
$targetName = "openclaw-tv-$versionName-$versionCode-factory-pilot-$($variant.ToLowerInvariant())-$signedMarker-$timestamp.apk"
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

$sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $targetPath).Hash.ToLowerInvariant()
$shaPath = "$targetPath.sha256.txt"
Set-Content -LiteralPath $shaPath -Encoding UTF8 -Value "$sha256  $targetName"

[pscustomobject]@{
    versionName = $versionName
    versionCode = [long]$versionCode
    variant = $variant
    apk = $targetPath
    sha256 = $sha256
    sha256File = $shaPath
    signing = $signature
} | ConvertTo-Json -Depth 3
