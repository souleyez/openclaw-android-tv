param(
    [string]$OutputRoot = "",
    [string]$FactoryApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk",
    [string]$ExpectedFactoryApkSha256 = "6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce",
    [string]$OtaApkPath = "C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.15.apk",
    [string]$ExpectedOtaApkSha256 = "9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86",
    [int64]$ExpectedOtaApkSize = 12119959,
    [string]$ExpectedSigningCertSha256 = "2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522",
    [string]$ApksignerPath = "",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$TargetDeviceUuid = "6741af4b-02b9-4692-99f3-5b4380fbbc3e",
    [string]$ExpectedOtaReleaseId = "ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33",
    [int]$ExpectedTargetVersionCode = 2026070101,
    [string[]]$AcceptedReportStatuses = @("verified", "installed", "reported"),
    [string[]]$FailureReportStatuses = @("failed", "failure", "error", "download_failed", "verify_failed", "install_failed"),
    [string]$RecoverableFailurePattern = "(recoverable|retry|retryable|manual install|manual confirmation|system installer|permission|required|prompt|network|timeout|temporarily|\u53ef\u6062\u590d|\u53ef\u91cd\u8bd5|\u91cd\u8bd5|\u624b\u52a8\u5b89\u88c5|\u7cfb\u7edf\u5b89\u88c5\u5668|\u6743\u9650|\u7f51\u7edc|\u6682\u65f6)",
    [string]$FactoryFeedbackPath = "",
    [string]$VendorPermissionPath = "",
    [string]$HomeSshHost = "root@8.155.8.7",
    [string]$ExpectedHomeCommit = "f78944f",
    [switch]$SkipHomeDeploymentCheck,
    [switch]$SkipRemoteCanaryCheck,
    [switch]$SkipHandoffExportCheck,
    [switch]$SkipReadinessLedgerCheck,
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

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
}

function Test-HandoffFileHashManifest {
    param(
        [string]$Root,
        [string]$ManifestPath
    )
    if (-not (Test-Path -LiteralPath $ManifestPath)) {
        return [pscustomobject]@{
            ok = $false
            detail = "hashManifestExists=False"
        }
    }

    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $issues = @()
    $checkedCount = 0
    foreach ($line in Get-Content -LiteralPath $ManifestPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -notmatch "^([0-9a-fA-F]{64})\s+(.+)$") {
            $issues += "invalidLine=$line"
            continue
        }
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relativePath = $Matches[2].Trim()
        $localPath = Join-Path $Root ($relativePath.Replace("/", [System.IO.Path]::DirectorySeparatorChar))
        $localFull = [System.IO.Path]::GetFullPath($localPath)
        if (-not $localFull.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -and
            -not $localFull.StartsWith($rootFull + [System.IO.Path]::AltDirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            $issues += "outsideRoot=$relativePath"
            continue
        }
        if (-not (Test-Path -LiteralPath $localFull)) {
            $issues += "missing=$relativePath"
            continue
        }
        $actualHash = Get-Sha256 -Path $localFull
        if ($actualHash -ne $expectedHash) {
            $issues += "hashMismatch=$relativePath"
            continue
        }
        $checkedCount += 1
    }

    return [pscustomobject]@{
        ok = $issues.Count -eq 0 -and $checkedCount -gt 0
        detail = "hashManifestExists=True; hashManifestChecked=$checkedCount; hashManifestIssues=$($issues -join ',')"
    }
}

function Test-HandoffZipEntries {
    param(
        [string]$ZipPath,
        [string[]]$RequiredEntries
    )
    if (-not (Test-Path -LiteralPath $ZipPath)) {
        return [pscustomobject]@{
            ok = $false
            detail = "archiveEntriesChecked=False; archiveMissingEntries=zip_missing"
        }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $entryMap = @{}
    $unsafeEntries = @()
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        foreach ($entry in $archive.Entries) {
            $rawName = [string]$entry.FullName
            $normalizedName = $rawName.Replace("\", "/")
            $segments = @($normalizedName -split "/" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
            $hasTraversal = @($segments | Where-Object { $_ -eq ".." }).Count -gt 0
            if ([string]::IsNullOrWhiteSpace($normalizedName) -or $normalizedName.StartsWith("/") -or $normalizedName -match "^[a-zA-Z]:" -or $hasTraversal) {
                $unsafeEntries += $rawName
            }
            $normalized = $entry.FullName.Replace("\", "/").TrimStart("/")
            $entryMap[$normalized] = $true
        }
    } finally {
        $archive.Dispose()
    }

    $missingEntries = @($RequiredEntries | Where-Object { -not $entryMap.ContainsKey($_) })
    return [pscustomobject]@{
        ok = $missingEntries.Count -eq 0 -and $unsafeEntries.Count -eq 0
        detail = "archiveEntriesChecked=True; archiveEntryCount=$($entryMap.Count); archiveMissingEntries=$($missingEntries -join ','); archiveUnsafeEntries=$($unsafeEntries -join ',')"
    }
}

function Test-HandoffOperatorOtaSnapshot {
    param(
        [string]$HandoffRoot,
        [string]$ExpectedReleaseId,
        [string]$ExpectedTargetDeviceUuid,
        [int]$ExpectedVersionCode,
        [string]$ExpectedOtaSha256
    )

    $snapshotPath = Join-Path $HandoffRoot "evidence\production-services\operator-ota-snapshot\target-ota-report.json"
    if (-not (Test-Path -LiteralPath $snapshotPath)) {
        return [pscustomobject]@{
            ok = $false
            detail = "operatorOtaSnapshotExists=False"
        }
    }

    try {
        $snapshot = Get-Content -Raw -LiteralPath $snapshotPath | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{
            ok = $false
            detail = "operatorOtaSnapshotJsonValid=False"
        }
    }

    $status = [string]$snapshot.status
    $releaseId = [string]$snapshot.release.id
    $targetDeviceUuid = [string]$snapshot.targetDeviceUuid
    $expectedReleaseIdInSnapshot = [string]$snapshot.expectedOtaReleaseId
    $versionCode = [int]$snapshot.release.versionCode
    $expectedVersionCodeInSnapshot = [int]$snapshot.expectedTargetVersionCode
    $artifactSha = ([string]$snapshot.release.artifactSha256).ToLowerInvariant()
    $targetScope = [string]$snapshot.release.targetScope
    $acceptedStatuses = @{
        PASS = $true
        PENDING = $true
        RECOVERABLE_FAILURE = $true
    }
    $issues = @()
    if (-not $acceptedStatuses.ContainsKey($status.Trim().ToUpperInvariant())) {
        $issues += "status=$status"
    }
    if ($expectedReleaseIdInSnapshot -ne $ExpectedReleaseId -or $releaseId -ne $ExpectedReleaseId) {
        $issues += "releaseId=$releaseId expectedReleaseId=$expectedReleaseIdInSnapshot"
    }
    if ($targetDeviceUuid -ne $ExpectedTargetDeviceUuid) {
        $issues += "targetDeviceUuid=$targetDeviceUuid"
    }
    if ($expectedVersionCodeInSnapshot -ne $ExpectedVersionCode -or $versionCode -ne $ExpectedVersionCode) {
        $issues += "versionCode=$versionCode expectedVersionCode=$expectedVersionCodeInSnapshot"
    }
    if ($artifactSha -ne $ExpectedOtaSha256.ToLowerInvariant()) {
        $issues += "artifactSha256=$artifactSha"
    }
    if ($targetScope -ne "deviceUuid:$ExpectedTargetDeviceUuid") {
        $issues += "targetScope=$targetScope"
    }

    return [pscustomobject]@{
        ok = $issues.Count -eq 0
        detail = "operatorOtaSnapshotExists=True; operatorOtaSnapshotStatus=$status; operatorOtaReleaseId=$releaseId; operatorOtaTargetDeviceUuid=$targetDeviceUuid; operatorOtaVersionCode=$versionCode; operatorOtaArtifactSha256=$artifactSha; operatorOtaSnapshotIssues=$($issues -join ',')"
    }
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

function Resolve-ApksignerPath {
    param([string]$RequestedPath)

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -Path $RequestedPath).Path
        }
        return ""
    }

    $command = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidateRoots = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidateRoots += (Join-Path $env:ANDROID_HOME "build-tools")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidateRoots += (Join-Path $env:ANDROID_SDK_ROOT "build-tools")
    }
    $candidateRoots += @(
        "$env:USERPROFILE\develop\android-sdk\build-tools",
        "$env:LOCALAPPDATA\Android\Sdk\build-tools"
    )

    $candidates = @()
    foreach ($root in $candidateRoots | Select-Object -Unique) {
        if (Test-Path -LiteralPath $root) {
            $candidates += Get-ChildItem -LiteralPath $root -Recurse -File -Filter "apksigner.bat" -ErrorAction SilentlyContinue
        }
    }
    $latest = $candidates | Sort-Object FullName -Descending | Select-Object -First 1
    if ($latest) {
        return $latest.FullName
    }
    return ""
}

function Test-ApkSignature {
    param(
        [string]$Path,
        [string]$ExpectedCertSha256,
        [string]$VerifierPath,
        [string]$OutputPath
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-TextFile -Path $OutputPath -Content "missing file: $Path"
        return [pscustomobject]@{
            status = "FAIL"
            detail = "missing file: $Path"
        }
    }
    if ([string]::IsNullOrWhiteSpace($VerifierPath) -or -not (Test-Path -LiteralPath $VerifierPath)) {
        Write-TextFile -Path $OutputPath -Content "apksigner not found"
        return [pscustomobject]@{
            status = "FAIL"
            detail = "apksigner not found"
        }
    }

    $resolvedApk = (Resolve-Path -Path $Path).Path
    $expectedCert = $ExpectedCertSha256.ToLowerInvariant()
    try {
        $output = & $VerifierPath verify --verbose --print-certs $resolvedApk 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @($_.Exception.Message)
        $exitCode = 1
    }
    $text = ($output | Out-String).Trim()
    Write-TextFile -Path $OutputPath -Content $text
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "apksigner exit=$exitCode; path=$resolvedApk"
        }
    }

    $certMatch = [regex]::Match($text, "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})")
    $cert = if ($certMatch.Success) { $certMatch.Groups[1].Value.ToLowerInvariant() } else { "" }
    $v1 = $text -match "Verified using v1 scheme \(JAR signing\):\s*true"
    $v2 = $text -match "Verified using v2 scheme \(APK Signature Scheme v2\):\s*true"
    $v3 = $text -match "Verified using v3 scheme \(APK Signature Scheme v3\):\s*true"
    $signers = if ($text -match "Number of signers:\s*(\d+)") { [int]$Matches[1] } else { 0 }
    $passed = $cert -eq $expectedCert -and $v1 -and $v2 -and $v3 -and $signers -eq 1
    return [pscustomobject]@{
        status = if ($passed) { "PASS" } else { "FAIL" }
        detail = "certSha256=$cert; expected=$expectedCert; v1=$v1; v2=$v2; v3=$v3; signers=$signers; apksigner=$VerifierPath; path=$resolvedApk"
    }
}

function Invoke-ChildScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments,
        [string]$LogPath
    )
    try {
        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @($_.Exception.Message)
        $exitCode = 1
    }
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
    $remote = @'
cd /srv/home/repo || exit 1
echo "HEAD=$(git rev-parse --short HEAD)"
for service in home-platform-api home-public-admin lease-core fleet-core; do
  echo "SERVICE:$service=$(systemctl is-active "$service.service")"
done
for path in /projects/openclaw-android-tv /projects/openclaw-android-tv/devices; do
  tmp=$(mktemp)
  code=$(curl -sS -o "$tmp" -w "%{http_code}" "http://127.0.0.1:3002$path" || true)
  bytes=$(stat -c%s "$tmp" 2>/dev/null || echo 0)
  rm -f "$tmp"
  echo "PAGE:$path=$code:$bytes"
done
'@
    try {
        $output = $remote | & ssh $SshHost "tr -d '\r' | bash -s" 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @($_.Exception.Message)
        $exitCode = 1
    }
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "ssh home deployment check failed: $text"
            output = $text
        }
    }
    $lines = @($text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $headLine = @($lines | Where-Object { $_ -like "HEAD=*" } | Select-Object -First 1)
    $head = if ($headLine.Count -gt 0) { $headLine[0].Substring(5).Trim() } else { "" }
    $services = @($lines | Where-Object { $_ -like "SERVICE:*" })
    $allActive = ($services.Count -ge 4) -and -not ($services | Where-Object { $_ -notmatch "=active$" })
    $pageLines = @($lines | Where-Object { $_ -like "PAGE:*" })
    $healthyPages = @($pageLines | Where-Object {
        if ($_ -match "^PAGE:(.+)=([0-9]{3}):([0-9]+)$") {
            ([int]$Matches[2] -eq 200 -and [int]$Matches[3] -gt 0)
        } else {
            $false
        }
    })
    $commitOk = $head -eq $ExpectedCommit
    $pagesOk = $healthyPages.Count -eq 2
    if ($commitOk -and $allActive -and $pagesOk) {
        return [pscustomobject]@{
            status = "PASS"
            detail = "home=$head services=active operatorPages=$($healthyPages.Count)/2 via 127.0.0.1:3002"
            output = $text
        }
    }
    return [pscustomobject]@{
        status = "FAIL"
        detail = "home head, services, or operator pages mismatch; expected=$ExpectedCommit actual=$head services=$($services -join ',') pages=$($pageLines -join ',')"
        output = $text
    }
}

function Test-RemoteOtaCanaryReport {
    param(
        [string]$SshHost,
        [string]$ProjectKey,
        [string]$TargetDeviceUuid,
        [string]$ExpectedReleaseId,
        [int]$ExpectedVersionCode,
        [string[]]$AcceptedStatuses,
        [string[]]$FailureStatuses,
        [string]$RecoverablePattern,
        [string]$OutputPath
    )

    $acceptedCsv = ($AcceptedStatuses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $failureCsv = ($FailureStatuses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $remoteScript = @'
set -euo pipefail
PROJECT_KEY="__PROJECT_KEY__"
TARGET_DEVICE_UUID="__TARGET_DEVICE_UUID__"
EXPECTED_RELEASE_ID="__EXPECTED_RELEASE_ID__"
EXPECTED_TARGET_VERSION_CODE="__EXPECTED_TARGET_VERSION_CODE__"
ACCEPTED_STATUSES="__ACCEPTED_STATUSES__"
FAILURE_STATUSES="__FAILURE_STATUSES__"
RECOVERABLE_FAILURE_PATTERN="__RECOVERABLE_FAILURE_PATTERN__"
export PROJECT_KEY TARGET_DEVICE_UUID EXPECTED_RELEASE_ID EXPECTED_TARGET_VERSION_CODE ACCEPTED_STATUSES FAILURE_STATUSES RECOVERABLE_FAILURE_PATTERN

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
const failureStatuses = new Set((process.env.FAILURE_STATUSES || "").split(",").map((item) => item.trim().toLowerCase()).filter(Boolean));
const recoverablePatternText = process.env.RECOVERABLE_FAILURE_PATTERN || "";
let recoverablePattern = null;
try {
  recoverablePattern = recoverablePatternText ? new RegExp(recoverablePatternText, "i") : null;
} catch (_error) {
  recoverablePattern = null;
}
const release = (data.releases || []).find((item) => item.id === releaseId) || null;
const reports = (data.reports || []).filter((item) => item.releaseId === releaseId && item.deviceUuid === target);
reports.sort((left, right) => Date.parse(right.updatedAt || right.reportedAt || 0) - Date.parse(left.updatedAt || left.reportedAt || 0));
const latest = reports[0] || null;
const releaseMatches = Boolean(release) && Number(release.versionCode || 0) === expectedVersionCode;
const latestStatus = String((latest && latest.status) || "").toLowerCase();
const latestNote = String((latest && latest.note) || "");
const isFailureReport = Boolean(latest) && failureStatuses.has(latestStatus);
const recoverableFailure = Boolean(isFailureReport && recoverablePattern && recoverablePattern.test(latestNote));
let status = "PENDING";
let detail = "release found, but target device has not reported OTA lifecycle yet";
if (!releaseMatches) {
  status = "FAIL";
  detail = `expected release not found or version mismatch; found=${Boolean(release)}; versionCode=${release ? release.versionCode : 0}`;
} else if (latest && accepted.has(latestStatus)) {
  status = "PASS";
  detail = `target device report status=${latest.status}`;
} else if (recoverableFailure) {
  status = "RECOVERABLE_FAILURE";
  detail = `target device failure status=${latest.status}; recoverable note=${latestNote}`;
} else if (isFailureReport) {
  status = "FAIL";
  detail = `target device failure status=${latest.status}; missing recoverable reason`;
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
  matchingReports: reports.length,
  latestReport: latest ? {
    status: latest.status,
    currentVersionCode: latest.currentVersionCode,
    targetVersionCode: latest.targetVersionCode,
    progressPercent: latest.progressPercent,
    reportedAt: latest.reportedAt,
    updatedAt: latest.updatedAt,
    note: latest.note
  } : null,
  acceptedReportStatuses: (process.env.ACCEPTED_STATUSES || "").split(",").filter(Boolean),
  failureReportStatuses: (process.env.FAILURE_STATUSES || "").split(",").filter(Boolean),
  recoverableFailurePattern: recoverablePatternText,
  recoverableFailure,
  totals: data.totals || {},
  checkedAt: new Date().toISOString()
}, null, 2));
'
'@
    $remoteScript = $remoteScript.Replace("__PROJECT_KEY__", $ProjectKey)
    $remoteScript = $remoteScript.Replace("__TARGET_DEVICE_UUID__", $TargetDeviceUuid)
    $remoteScript = $remoteScript.Replace("__EXPECTED_RELEASE_ID__", $ExpectedReleaseId)
    $remoteScript = $remoteScript.Replace("__EXPECTED_TARGET_VERSION_CODE__", [string]$ExpectedVersionCode)
    $remoteScript = $remoteScript.Replace("__ACCEPTED_STATUSES__", $acceptedCsv)
    $remoteScript = $remoteScript.Replace("__FAILURE_STATUSES__", $failureCsv)
    $remoteScript = $remoteScript.Replace("__RECOVERABLE_FAILURE_PATTERN__", $RecoverablePattern)

    try {
        $output = $remoteScript | & ssh $SshHost "bash -s" 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @($_.Exception.Message)
        $exitCode = 1
    }
    $text = ($output | Out-String).Trim()
    Write-TextFile -Path $OutputPath -Content $text
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check failed: $text"
            evidencePath = $OutputPath
        }
    }
    try {
        $payload = $text | ConvertFrom-Json
        return [pscustomobject]@{
            status = [string]$payload.status
            detail = [string]$payload.detail
            evidencePath = $OutputPath
        }
    } catch {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote OTA canary check returned invalid JSON"
            evidencePath = $OutputPath
        }
    }
}

function Get-GitValue {
    param([string[]]$Arguments)
    try {
        $output = & git -C $repoRoot @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            return ""
        }
        return (($output | Out-String).Trim())
    } catch {
        return ""
    }
}

function Get-SummaryStatus {
    param([string]$Directory)

    $summaryPath = Join-Path $Directory "summary.txt"
    if (-not (Test-Path -LiteralPath $summaryPath)) {
        return ""
    }
    foreach ($line in Get-Content -LiteralPath $summaryPath) {
        if ($line -match "^status=(.+)$") {
            return $Matches[1].Trim()
        }
    }
    return ""
}

function Get-LatestHandoffExport {
    param([string]$Root)

    if (-not (Test-Path -LiteralPath $Root)) {
        return $null
    }
    return Get-ChildItem -LiteralPath $Root -Directory |
        Where-Object { $_.Name -like "handoff-*" -and $_.Name -notlike "*smoke*" } |
        Where-Object { (Get-SummaryStatus -Directory $_.FullName) -eq "EXPORTED" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Get-RemoteBranchHead {
    param(
        [string]$RemoteName,
        [string]$BranchName
    )
    if ([string]::IsNullOrWhiteSpace($RemoteName) -or [string]::IsNullOrWhiteSpace($BranchName)) {
        return ""
    }
    $output = Get-GitValue -Arguments @("ls-remote", "--heads", $RemoteName, $BranchName)
    if ([string]::IsNullOrWhiteSpace($output)) {
        return ""
    }
    $firstLine = @($output -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
    if ($firstLine.Count -eq 0 -or $firstLine[0] -notmatch "^([0-9a-fA-F]{40})\s+") {
        return ""
    }
    return $Matches[1].ToLowerInvariant()
}

function Test-HandoffExport {
    param(
        [string]$ExpectedFactorySha256,
        [string]$ExpectedOtaSha256,
        [string]$ExpectedReleaseId,
        [string]$ExpectedTargetDeviceUuid,
        [int]$ExpectedVersionCode
    )

    $handoffRoot = Join-Path $repoRoot "artifacts\factory-pilot-handoff"
    $latest = Get-LatestHandoffExport -Root $handoffRoot
    if (-not $latest) {
        return [pscustomobject]@{
            status = "PENDING"
            detail = "no exported factory handoff package found"
            evidencePath = $handoffRoot
        }
    }

    $manifestPath = Join-Path $latest.FullName "handoff-manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "latest handoff is missing handoff-manifest.json"
            evidencePath = $latest.FullName
        }
    }

    try {
        $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "latest handoff manifest is invalid JSON"
            evidencePath = $manifestPath
        }
    }

    $expectedHead = Get-GitValue -Arguments @("rev-parse", "--short", "HEAD")
    $expectedHeadFull = (Get-GitValue -Arguments @("rev-parse", "HEAD")).ToLowerInvariant()
    $manifestRemote = $manifest.source.PSObject.Properties["remote"]
    $manifestRemoteName = if ($manifestRemote) { [string]$manifest.source.remote.name } else { "" }
    $manifestRemoteBranch = if ($manifestRemote) { [string]$manifest.source.remote.branch } else { "" }
    $manifestRemoteHeadFull = if ($manifestRemote) { ([string]$manifest.source.remote.headFull).ToLowerInvariant() } else { "" }
    $manifestRemoteMatchesHead = $manifestRemote -and $manifest.source.remote.matchesHead -eq $true
    $actualRemoteHeadFull = Get-RemoteBranchHead -RemoteName $manifestRemoteName -BranchName $manifestRemoteBranch
    $archiveProperty = $manifest.PSObject.Properties["archive"]
    $archivePlanned = $false
    $archiveZipPath = "$($latest.FullName).zip"
    $archiveSidecarPath = "$archiveZipPath.sha256.txt"
    if ($archiveProperty) {
        $archivePlanned = $manifest.archive.planned -eq $true
        if (-not [string]::IsNullOrWhiteSpace([string]$manifest.archive.zipPath)) {
            $archiveZipPath = [string]$manifest.archive.zipPath
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$manifest.archive.sha256SidecarPath)) {
            $archiveSidecarPath = [string]$manifest.archive.sha256SidecarPath
        }
    }
    $archiveZipExists = $archivePlanned -and (Test-Path -LiteralPath $archiveZipPath)
    $archiveSidecarExists = $archivePlanned -and (Test-Path -LiteralPath $archiveSidecarPath)
    $archiveSidecarMatches = $false
    if ($archiveZipExists -and $archiveSidecarExists) {
        $archiveSha = Get-Sha256 -Path $archiveZipPath
        $archiveSidecarText = (Get-Content -Raw -LiteralPath $archiveSidecarPath).Trim()
        $archiveSidecarMatches = $archiveSidecarText.StartsWith($archiveSha, [System.StringComparison]::OrdinalIgnoreCase)
    }
    $requiredArchiveEntries = @(
        "apk/OpenClawTV-0.1.14.apk",
        "feedback/android-tv-factory-feedback.json",
        "feedback/android-tv-vendor-system-permission.json",
        "feedback/README-return-package.md",
        "docs/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md",
        "docs/2026-06-30-android-tv-production-readiness.md",
        "docs/2026-06-30-next-stage-production-development-plan.md",
        "evidence/production-services/summary.txt",
        "evidence/production-services/operator-ota-snapshot.log",
        "evidence/production-services/operator-ota-snapshot/summary.txt",
        "evidence/production-services/operator-ota-snapshot/target-ota-report.json",
        "evidence/factory-pilot-gate/summary.txt",
        "evidence/factory-pilot-gate/factory-apk-signature.txt",
        "evidence/factory-pilot-gate/ota-apk-signature.txt",
        "handoff-manifest.json",
        "handoff-files.sha256.txt",
        "README-factory-pilot.md",
        "summary.txt"
    )
    $archiveEntries = Test-HandoffZipEntries -ZipPath $archiveZipPath -RequiredEntries $requiredArchiveEntries
    $hashManifest = Test-HandoffFileHashManifest -Root $latest.FullName -ManifestPath (Join-Path $latest.FullName "handoff-files.sha256.txt")
    $operatorOtaSnapshot = Test-HandoffOperatorOtaSnapshot `
        -HandoffRoot $latest.FullName `
        -ExpectedReleaseId $ExpectedReleaseId `
        -ExpectedTargetDeviceUuid $ExpectedTargetDeviceUuid `
        -ExpectedVersionCode $ExpectedVersionCode `
        -ExpectedOtaSha256 $ExpectedOtaSha256

    $checks = @(
        [pscustomobject]@{ ok = [string]$manifest.source.head -eq $expectedHead; detail = "sourceHead=$($manifest.source.head); expectedHead=$expectedHead" },
        [pscustomobject]@{ ok = [string]$manifest.source.headFull -eq $expectedHeadFull; detail = "sourceHeadFull=$($manifest.source.headFull); expectedHeadFull=$expectedHeadFull" },
        [pscustomobject]@{ ok = $manifest.source.clean -eq $true; detail = "sourceClean=$($manifest.source.clean)" },
        [pscustomobject]@{ ok = $manifestRemoteName -eq "origin"; detail = "sourceRemote=$manifestRemoteName" },
        [pscustomobject]@{ ok = $manifestRemoteBranch -eq [string]$manifest.source.branch; detail = "sourceRemoteBranch=$manifestRemoteBranch; sourceBranch=$($manifest.source.branch)" },
        [pscustomobject]@{ ok = $manifestRemoteMatchesHead; detail = "sourceRemoteMatchesHead=$manifestRemoteMatchesHead" },
        [pscustomobject]@{ ok = $manifestRemoteHeadFull -eq $expectedHeadFull -and $actualRemoteHeadFull -eq $expectedHeadFull; detail = "sourceRemoteHeadFull=$manifestRemoteHeadFull; actualRemoteHeadFull=$actualRemoteHeadFull" },
        [pscustomobject]@{ ok = $manifest.installApk.copied -eq $true; detail = "factoryApkCopied=$($manifest.installApk.copied)" },
        [pscustomobject]@{ ok = [string]$manifest.installApk.sha256 -eq $ExpectedFactorySha256.ToLowerInvariant(); detail = "factorySha=$($manifest.installApk.sha256)" },
        [pscustomobject]@{ ok = [string]$manifest.otaCanary.sha256 -eq $ExpectedOtaSha256.ToLowerInvariant(); detail = "otaSha=$($manifest.otaCanary.sha256)" },
        [pscustomobject]@{ ok = [string]$manifest.otaCanary.releaseId -eq $ExpectedReleaseId; detail = "otaReleaseId=$($manifest.otaCanary.releaseId)" },
        [pscustomobject]@{ ok = [string]$manifest.otaCanary.targetDeviceUuid -eq $ExpectedTargetDeviceUuid; detail = "targetDeviceUuid=$($manifest.otaCanary.targetDeviceUuid)" },
        [pscustomobject]@{ ok = $manifest.evidence.productionServices.copied -eq $true; detail = "productionServiceEvidenceCopied=$($manifest.evidence.productionServices.copied)" },
        [pscustomobject]@{ ok = $manifest.evidence.factoryPilotGate.copied -eq $true; detail = "factoryGateEvidenceCopied=$($manifest.evidence.factoryPilotGate.copied)" },
        [pscustomobject]@{ ok = $archivePlanned; detail = "archivePlanned=$archivePlanned" },
        [pscustomobject]@{ ok = $archiveZipExists; detail = "archiveZipExists=$archiveZipExists; archiveZipPath=$archiveZipPath" },
        [pscustomobject]@{ ok = $archiveSidecarExists; detail = "archiveSidecarExists=$archiveSidecarExists; archiveSidecarPath=$archiveSidecarPath" },
        [pscustomobject]@{ ok = $archiveSidecarMatches; detail = "archiveSidecarMatches=$archiveSidecarMatches" },
        [pscustomobject]@{ ok = $hashManifest.ok; detail = $hashManifest.detail },
        [pscustomobject]@{ ok = $operatorOtaSnapshot.ok; detail = $operatorOtaSnapshot.detail },
        [pscustomobject]@{ ok = $archiveEntries.ok; detail = $archiveEntries.detail }
    )
    $failed = @($checks | Where-Object { -not $_.ok })
    $detail = ($checks | ForEach-Object { $_.detail }) -join "; "
    if ($failed.Count -gt 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = $detail
            evidencePath = $latest.FullName
        }
    }

    return [pscustomobject]@{
        status = "PASS"
        detail = "latest=$($latest.Name); $detail"
        evidencePath = $latest.FullName
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
$resolvedApksignerPath = Resolve-ApksignerPath -RequestedPath $ApksignerPath

$factoryApk = Get-FileHashStatus -Path $FactoryApkPath -ExpectedSha256 $ExpectedFactoryApkSha256 -ExpectedSize 0
Add-Gate -List $gates -Name "factory apk hash" -Status $factoryApk.status -Detail $factoryApk.detail

$otaApk = Get-FileHashStatus -Path $OtaApkPath -ExpectedSha256 $ExpectedOtaApkSha256 -ExpectedSize $ExpectedOtaApkSize
Add-Gate -List $gates -Name "ota apk hash" -Status $otaApk.status -Detail $otaApk.detail

$factoryApkSignature = Test-ApkSignature `
    -Path $FactoryApkPath `
    -ExpectedCertSha256 $ExpectedSigningCertSha256 `
    -VerifierPath $resolvedApksignerPath `
    -OutputPath (Join-Path $outputDir "factory-apk-signature.txt")
Add-Gate -List $gates -Name "factory apk signature" -Status $factoryApkSignature.status -Detail $factoryApkSignature.detail -EvidencePath (Join-Path $outputDir "factory-apk-signature.txt")

$otaApkSignature = Test-ApkSignature `
    -Path $OtaApkPath `
    -ExpectedCertSha256 $ExpectedSigningCertSha256 `
    -VerifierPath $resolvedApksignerPath `
    -OutputPath (Join-Path $outputDir "ota-apk-signature.txt")
Add-Gate -List $gates -Name "ota apk signature" -Status $otaApkSignature.status -Detail $otaApkSignature.detail -EvidencePath (Join-Path $outputDir "ota-apk-signature.txt")

if ($SkipHandoffExportCheck) {
    Add-Gate -List $gates -Name "factory handoff export" -Status "SKIPPED" -Detail "skipped by flag"
} else {
    $handoff = Test-HandoffExport `
        -ExpectedFactorySha256 $ExpectedFactoryApkSha256 `
        -ExpectedOtaSha256 $ExpectedOtaApkSha256 `
        -ExpectedReleaseId $ExpectedOtaReleaseId `
        -ExpectedTargetDeviceUuid $TargetDeviceUuid `
        -ExpectedVersionCode $ExpectedTargetVersionCode
    Add-Gate -List $gates -Name "factory handoff export" -Status $handoff.status -Detail $handoff.detail -EvidencePath $handoff.evidencePath
}

$serviceOutputRoot = Join-Path $outputDir "production-services"
$serviceCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-production-services.ps1") `
    -Arguments @("-OutputRoot", $serviceOutputRoot) `
    -LogPath (Join-Path $outputDir "production-services.log")
$serviceSummary = Get-SummaryMap -Path (Join-Path $serviceOutputRoot "summary.txt")
$serviceStatus = if ($serviceSummary.ContainsKey("status")) { $serviceSummary["status"] } elseif ($serviceCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$serviceFailedCount = if ($serviceSummary.ContainsKey("failedCount")) { $serviceSummary["failedCount"] } else { "" }
Add-Gate -List $gates -Name "production services" -Status $serviceStatus -Detail "exit=$($serviceCheck.exitCode); failedCount=$serviceFailedCount" -EvidencePath $serviceOutputRoot

if ($SkipReadinessLedgerCheck) {
    Add-Gate -List $gates -Name "production readiness ledger" -Status "SKIPPED" -Detail "skipped by flag"
} else {
    $readinessOutputRoot = Join-Path $outputDir "production-readiness-ledger"
    $readinessCheck = Invoke-ChildScript `
        -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-production-readiness-ledger.ps1") `
        -Arguments @("-OutputRoot", $readinessOutputRoot, "-AllowPending") `
        -LogPath (Join-Path $outputDir "production-readiness-ledger.log")
    $readinessSummary = Get-SummaryMap -Path (Join-Path $readinessOutputRoot "summary.txt")
    $readinessStatus = if ($readinessSummary.ContainsKey("status")) { $readinessSummary["status"] } elseif ($readinessCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
    $readinessPendingCount = if ($readinessSummary.ContainsKey("pendingRowCount")) { $readinessSummary["pendingRowCount"] } else { "" }
    $readinessIssueCount = if ($readinessSummary.ContainsKey("rowIssueCount")) { $readinessSummary["rowIssueCount"] } else { "" }
    $readinessEvidenceReferenceCheckCount = if ($readinessSummary.ContainsKey("evidenceReferenceCheckCount")) { $readinessSummary["evidenceReferenceCheckCount"] } else { "" }
    $readinessHomeCommitCheckCount = if ($readinessSummary.ContainsKey("homeCommitCheckCount")) { $readinessSummary["homeCommitCheckCount"] } else { "" }
    $readinessAutomationCheckCount = if ($readinessSummary.ContainsKey("automationCheckCount")) { $readinessSummary["automationCheckCount"] } else { "" }
    Add-Gate -List $gates -Name "production readiness ledger" -Status $readinessStatus -Detail "exit=$($readinessCheck.exitCode); pendingRows=$readinessPendingCount; rowIssues=$readinessIssueCount; evidenceReferenceChecks=$readinessEvidenceReferenceCheckCount; homeCommitChecks=$readinessHomeCommitCheckCount; automationChecks=$readinessAutomationCheckCount" -EvidencePath $readinessOutputRoot
}

$canaryOutputRoot = Join-Path $outputDir "ota-canary-report"
$canaryCheck = Invoke-ChildScript `
    -ScriptPath (Join-Path $PSScriptRoot "android-tv-check-ota-canary-report.ps1") `
    -Arguments @(
        "-OutputRoot", $canaryOutputRoot,
        "-FailureReportStatuses", $FailureReportStatuses,
        "-RecoverableFailurePattern", $RecoverableFailurePattern,
        "-AllowMissingAdminAuth",
        "-AllowPending"
    ) `
    -LogPath (Join-Path $outputDir "ota-canary-report.log")
$canarySummary = Get-SummaryMap -Path (Join-Path $canaryOutputRoot "summary.txt")
$canaryStatus = if ($canarySummary.ContainsKey("status")) { $canarySummary["status"] } elseif ($canaryCheck.exitCode -eq 0) { "PASS" } else { "FAIL" }
$canaryDetail = if ($canarySummary.ContainsKey("detail")) { $canarySummary["detail"] } else { "" }
$canaryEvidencePath = $canaryOutputRoot
if ($canaryStatus -eq "AUTH_REQUIRED" -and -not $SkipRemoteCanaryCheck) {
    $remoteCanaryPath = Join-Path $outputDir "remote-ota-canary-report.json"
    $remoteCanary = Test-RemoteOtaCanaryReport `
        -SshHost $HomeSshHost `
        -ProjectKey $ProjectKey `
        -TargetDeviceUuid $TargetDeviceUuid `
        -ExpectedReleaseId $ExpectedOtaReleaseId `
        -ExpectedVersionCode $ExpectedTargetVersionCode `
        -AcceptedStatuses $AcceptedReportStatuses `
        -FailureStatuses $FailureReportStatuses `
        -RecoverablePattern $RecoverableFailurePattern `
        -OutputPath $remoteCanaryPath
    $canaryStatus = $remoteCanary.status
    $canaryDetail = "remote=$($remoteCanary.detail)"
    $canaryEvidencePath = $remoteCanaryPath
}
Add-Gate -List $gates -Name "ota installed report" -Status $canaryStatus -Detail "exit=$($canaryCheck.exitCode); $canaryDetail" -EvidencePath $canaryEvidencePath

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
$pending = @($gates | Where-Object { $_.status -eq "PENDING" -or $_.status -eq "AUTH_REQUIRED" -or $_.status -eq "RECOVERABLE_FAILURE" })
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
