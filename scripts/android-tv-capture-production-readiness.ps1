param(
    [string]$DeviceId = "",
    [string]$OutputRoot = "",
    [switch]$LaunchHome,
    [switch]$AllowNoDevice
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    $candidates = @(
        "C:\Users\soulzyn\develop\android-sdk\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "adb"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -eq "adb") {
            $command = Get-Command adb -ErrorAction SilentlyContinue
            if ($command) {
                return $command.Source
            }
            continue
        }
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "adb not found. Install Android platform-tools or pass adb through PATH."
}

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
    $Value | ConvertTo-Json -Depth 6 | Out-File -FilePath $Path -Encoding utf8
}

function Get-EvidenceFileInventory {
    param([string]$Root)
    return @(
        Get-ChildItem -LiteralPath $Root -File -Force |
            Sort-Object Name |
            ForEach-Object {
                [pscustomobject]@{
                    name = $_.Name
                    path = $_.FullName
                    sizeBytes = $_.Length
                }
            }
    )
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $fullArgs = @()
    if ($script:DeviceArg) {
        $fullArgs += @("-s", $script:DeviceArg)
    }
    $fullArgs += $Arguments

    try {
        $output = & $script:AdbExe @fullArgs 2>&1
        return ($output | Out-String).Trim()
    } catch {
        if ($AllowFailure) {
            return $_.Exception.Message
        }
        throw
    }
}

function Save-AdbText {
    param(
        [string]$Name,
        [string[]]$Arguments
    )
    $output = Invoke-Adb -Arguments $Arguments -AllowFailure
    Write-TextFile -Path (Join-Path $script:OutputDir $Name) -Content $output
}

function Save-AdbExecOut {
    param(
        [string]$Name,
        [string[]]$Arguments
    )
    $target = Join-Path $script:OutputDir $Name
    $fullArgs = @()
    if ($script:DeviceArg) {
        $fullArgs += @("-s", $script:DeviceArg)
    }
    $fullArgs += $Arguments
    & $script:AdbExe @fullArgs > $target 2>$null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $repoRoot "artifacts\device-tests\production-readiness-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$script:OutputDir = (Resolve-Path $OutputRoot).Path
$script:AdbExe = Resolve-AdbPath

& $script:AdbExe start-server | Out-Null
$devicesRaw = (& $script:AdbExe devices -l 2>&1) | Out-String
Write-TextFile -Path (Join-Path $script:OutputDir "adb-devices.txt") -Content $devicesRaw.Trim()

if ($DeviceId) {
    $script:DeviceArg = $DeviceId
} else {
    $online = $devicesRaw -split "`r?`n" |
        Where-Object { $_ -match "\sdevice\s" } |
        Select-Object -First 1
    if (-not $online) {
        $capture = [pscustomobject]@{
            status = "NO_ADB_DEVICE"
            checkedAt = (Get-Date).ToUniversalTime().ToString("o")
            outputDir = $script:OutputDir
            adbExe = $script:AdbExe
            requestedDeviceId = $DeviceId
            launchHome = [bool]$LaunchHome
            adbDevicesFile = Join-Path $script:OutputDir "adb-devices.txt"
            next = "Connect an Android TV device through USB or TCP adb, then re-run this script."
            evidenceFiles = @(Get-EvidenceFileInventory -Root $script:OutputDir)
        }
        Write-JsonFile -Path (Join-Path $script:OutputDir "production-readiness-capture.json") -Value $capture
        Write-TextFile -Path (Join-Path $script:OutputDir "summary.txt") -Content @"
status=NO_ADB_DEVICE
checkedAt=$($capture.checkedAt)
outputDir=$script:OutputDir
adbExe=$script:AdbExe
adbDevicesFile=$($capture.adbDevicesFile)
next=Connect an Android TV device through USB or TCP adb, then re-run this script.
"@
        Write-Host "No online adb device found. Evidence directory: $script:OutputDir"
        if ($AllowNoDevice) {
            exit 0
        }
        exit 2
    }
    $script:DeviceArg = ($online -split "\s+")[0]
}

if ($LaunchHome) {
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "HOME") -AllowFailure | Out-Null
    Start-Sleep -Seconds 1
    Invoke-Adb -Arguments @("shell", "monkey", "-p", "com.openclaw.tv", "1") -AllowFailure | Out-Null
    Start-Sleep -Seconds 2
}

Save-AdbText -Name "device-info.txt" -Arguments @("shell", "getprop")
Save-AdbText -Name "build-fingerprint.txt" -Arguments @("shell", "getprop", "ro.build.fingerprint")
Save-AdbText -Name "android-version.txt" -Arguments @("shell", "getprop", "ro.build.version.release")
Save-AdbText -Name "home-resolve.txt" -Arguments @(
    "shell",
    "cmd",
    "package",
    "resolve-activity",
    "--brief",
    "-a",
    "android.intent.action.MAIN",
    "-c",
    "android.intent.category.HOME"
)
Save-AdbText -Name "package-openclaw.txt" -Arguments @("shell", "dumpsys", "package", "com.openclaw.tv")
Save-AdbText -Name "window-focus.txt" -Arguments @("shell", "dumpsys", "window", "windows")
Save-AdbText -Name "activity-focus.txt" -Arguments @("shell", "dumpsys", "activity", "activities")
Save-AdbText -Name "meminfo-openclaw.txt" -Arguments @("shell", "dumpsys", "meminfo", "com.openclaw.tv")
Save-AdbText -Name "meminfo-lebo.txt" -Arguments @("shell", "dumpsys", "meminfo", "com.hpplay.happyplay.aw")
Save-AdbText -Name "process-list.txt" -Arguments @("shell", "ps", "-A")
Save-AdbText -Name "crash-logcat.txt" -Arguments @("logcat", "-b", "crash", "-d")
Save-AdbText -Name "main-logcat-openclaw.txt" -Arguments @("logcat", "-d", "-s", "OpenClawTv", "OpenClawRuntime", "OpenClawOTA", "OpenClawTrim", "OpenClawCast")
Save-AdbText -Name "uiautomator-dump.txt" -Arguments @("shell", "uiautomator", "dump", "/dev/tty")
Save-AdbExecOut -Name "screenshot.png" -Arguments @("exec-out", "screencap", "-p")

$packageText = Get-Content -Raw -Path (Join-Path $script:OutputDir "package-openclaw.txt")
$homeText = Get-Content -Raw -Path (Join-Path $script:OutputDir "home-resolve.txt")
$openclawPss = Select-String -Path (Join-Path $script:OutputDir "meminfo-openclaw.txt") -Pattern "TOTAL\s+(\d+)" | Select-Object -First 1
$leboPss = Select-String -Path (Join-Path $script:OutputDir "meminfo-lebo.txt") -Pattern "TOTAL\s+(\d+)" | Select-Object -First 1
$versionCodeLine = (Select-String -InputObject $packageText -Pattern "versionCode=" | Select-Object -First 1).Line
$versionNameLine = (Select-String -InputObject $packageText -Pattern "versionName=" | Select-Object -First 1).Line
$capture = [pscustomobject]@{
    status = "CAPTURED"
    checkedAt = (Get-Date).ToUniversalTime().ToString("o")
    device = $script:DeviceArg
    outputDir = $script:OutputDir
    adbExe = $script:AdbExe
    launchHome = [bool]$LaunchHome
    homeContainsOpenClaw = [bool]($homeText -match "com.openclaw.tv")
    packageInstalled = [bool]($packageText -match "com.openclaw.tv")
    versionCodeLine = $versionCodeLine
    versionNameLine = $versionNameLine
    openclawPssLine = $openclawPss.Line
    leboPssLine = $leboPss.Line
    evidenceFiles = @(Get-EvidenceFileInventory -Root $script:OutputDir)
    next = "Attach this directory to docs/testing/2026-06-30-android-tv-production-readiness.md evidence notes."
}
Write-JsonFile -Path (Join-Path $script:OutputDir "production-readiness-capture.json") -Value $capture

Write-TextFile -Path (Join-Path $script:OutputDir "summary.txt") -Content @"
status=CAPTURED
checkedAt=$($capture.checkedAt)
device=$script:DeviceArg
outputDir=$script:OutputDir
homeContainsOpenClaw=$($homeText -match "com.openclaw.tv")
packageInstalled=$($packageText -match "com.openclaw.tv")
versionCodeLine=$versionCodeLine
versionNameLine=$versionNameLine
openclawPssLine=$($openclawPss.Line)
leboPssLine=$($leboPss.Line)
captureJson=$(Join-Path $script:OutputDir "production-readiness-capture.json")
next=Attach this directory to docs/testing/2026-06-30-android-tv-production-readiness.md evidence notes.
"@

Write-Host "Production readiness evidence captured: $script:OutputDir"
