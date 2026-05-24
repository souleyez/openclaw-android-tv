param(
    [string]$OutputDir = "",
    [switch]$Release
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$androidRoot = Join-Path $repoRoot "apps\android-tv-client-kotlin"
$buildFile = Join-Path $androidRoot "app\build.gradle.kts"
$localSigningProps = Join-Path $repoRoot "artifacts\signing\openclaw-tv-factory-pilot.signing.local.properties"
$targetOutputDir = if ($OutputDir) {
    $OutputDir
} else {
    Join-Path $repoRoot "artifacts\android-tv\factory-pilot"
}

if ($Release -and (Test-Path -LiteralPath $localSigningProps)) {
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

Push-Location $androidRoot
try {
    & ".\gradlew.bat" $task "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $task"
    }
} finally {
    Pop-Location
}

$variantDir = if ($Release) { "release" } else { "debug" }
$apkDir = Join-Path $androidRoot "app\build\outputs\apk\$variantDir"
$apk = Get-ChildItem -Path $apkDir -Filter "*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    throw "No APK found in $apkDir"
}

New-Item -ItemType Directory -Force -Path $targetOutputDir | Out-Null
$signedMarker = if ($apk.Name -like "*unsigned*") { "unsigned" } else { "signed" }
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$targetName = "openclaw-tv-$versionName-$versionCode-factory-pilot-$($variant.ToLowerInvariant())-$signedMarker-$timestamp.apk"
$targetPath = Join-Path $targetOutputDir $targetName
Copy-Item -LiteralPath $apk.FullName -Destination $targetPath

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
} | ConvertTo-Json -Depth 3
