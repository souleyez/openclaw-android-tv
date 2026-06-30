param(
    [string]$OutputRoot = "",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$HomeSshHost = "root@8.155.8.7",
    [string[]]$ExpectedSlotIds = @("home.hero"),
    [string]$ExpectedAssetUrlPrefix = "https://gm.goods-editor.com/ads/",
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

function Invoke-RemoteAdPublishSnapshot {
    param(
        [string]$SshHost,
        [string]$ProjectKey,
        [string[]]$ExpectedSlotIds,
        [string]$ExpectedAssetUrlPrefix
    )

    $slotCsv = ($ExpectedSlotIds | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $remoteScript = @'
set -euo pipefail
PROJECT_KEY="__PROJECT_KEY__"
EXPECTED_SLOT_IDS="__EXPECTED_SLOT_IDS__"
EXPECTED_ASSET_URL_PREFIX="__EXPECTED_ASSET_URL_PREFIX__"
export PROJECT_KEY EXPECTED_SLOT_IDS EXPECTED_ASSET_URL_PREFIX

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
const https = require('https');

const projectKey = process.env.PROJECT_KEY;
const headerName = process.env.ADMIN_HEADER_NAME;
const adminSecret = process.env.ADMIN_SECRET;
const expectedSlots = new Set(String(process.env.EXPECTED_SLOT_IDS || '').split(',').map((item) => item.trim()).filter(Boolean));
const expectedAssetUrlPrefix = String(process.env.EXPECTED_ASSET_URL_PREFIX || '').trim();

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

function headAsset(url) {
  return new Promise((resolve) => {
    let parsed = null;
    try {
      parsed = new URL(url);
    } catch (error) {
      resolve({ url, statusCode: 0, ok: false, error: 'invalid url' });
      return;
    }
    const lib = parsed.protocol === 'https:' ? https : http;
    const req = lib.request(parsed, { method: 'HEAD', timeout: 10000 }, (res) => {
      const contentType = String(res.headers['content-type'] || '');
      const contentLength = String(res.headers['content-length'] || '');
      resolve({
        url,
        statusCode: res.statusCode || 0,
        contentType,
        contentLength,
        ok: res.statusCode === 200 && contentType.toLowerCase().startsWith('image/'),
      });
      res.resume();
    });
    req.on('timeout', () => {
      req.destroy(new Error('timeout'));
    });
    req.on('error', (error) => {
      resolve({ url, statusCode: 0, ok: false, error: error.message });
    });
    req.end();
  });
}

function sanitizeCreative(slot, creative, assetHead) {
  return {
    slotId: slot.slotId || '',
    slotEnabled: Boolean(slot.enabled),
    manifestVersion: slot.manifest?.manifestVersion || '',
    countryCode: slot.manifest?.countryCode || '',
    regionCode: slot.manifest?.regionCode || '',
    creativeId: creative.creativeId || '',
    mediaType: creative.mediaType || '',
    assetUrl: creative.assetUrl || '',
    altText: creative.altText || '',
    active: Boolean(creative.active),
    startsAt: creative.startsAt || '',
    endsAt: creative.endsAt || '',
    updatedAt: creative.updatedAt || '',
    linkSortOrder: creative.linkSortOrder ?? 0,
    assetHead,
  };
}

(async () => {
  const response = await getJson(`/api/admin/tv-ad-slots?projectKey=${encodeURIComponent(projectKey)}`);
  const slots = response.json.items || [];
  const activeCreatives = [];
  for (const slot of slots) {
    for (const creative of slot.creatives || []) {
      if (!slot.enabled || !creative.active || !creative.assetUrl) {
        continue;
      }
      activeCreatives.push({ slot, creative });
    }
  }

  const assetHeads = await Promise.all(activeCreatives.map((entry) => headAsset(entry.creative.assetUrl)));
  const activeViews = activeCreatives.map((entry, index) => sanitizeCreative(entry.slot, entry.creative, assetHeads[index]));
  const expectedSlotResults = [...expectedSlots].map((slotId) => {
    const matching = activeViews.filter((item) => item.slotId === slotId);
    const valid = matching.filter((item) => (
      (!expectedAssetUrlPrefix || item.assetUrl.startsWith(expectedAssetUrlPrefix))
      && item.assetHead?.ok === true
    ));
    return {
      slotId,
      activeCreativeCount: matching.length,
      validAssetCreativeCount: valid.length,
      passed: valid.length > 0,
    };
  });
  const failedSlots = expectedSlotResults.filter((item) => !item.passed);
  const endpointOk = response.statusCode === 200 && response.json.status === 'ok';
  const status = !endpointOk ? 'FAIL' : (failedSlots.length === 0 ? 'PASS' : 'PENDING');
  const detail = !endpointOk
    ? `admin endpoint status mismatch statusCode=${response.statusCode}`
    : (failedSlots.length === 0
      ? `expected ad slots have active reachable creatives; activeCreativeCount=${activeViews.length}`
      : `missing active reachable creative for slots=${failedSlots.map((item) => item.slotId).join(',')}; activeCreativeCount=${activeViews.length}`);

  console.log(JSON.stringify({
    status,
    detail,
    projectKey,
    checkedAt: new Date().toISOString(),
    source: 'remote-admin',
    expected: {
      slotIds: [...expectedSlots],
      assetUrlPrefix: expectedAssetUrlPrefix,
    },
    totals: {
      slots: slots.length,
      activeCreatives: activeViews.length,
      assetHeadChecks: assetHeads.length,
    },
    expectedSlotResults,
    activeCreatives: activeViews,
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
    $remoteScript = $remoteScript.Replace('"__EXPECTED_SLOT_IDS__"', (ConvertTo-BashLiteral -Value $slotCsv))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_ASSET_URL_PREFIX__"', (ConvertTo-BashLiteral -Value $ExpectedAssetUrlPrefix))

    try {
        $output = $remoteScript | & ssh $SshHost "tr -d '\r' | bash -s" 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @($_.Exception.Message)
        $exitCode = 1
    }
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            status = "FAIL"
            detail = "remote ad publish check failed"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote ad publish check failed"
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
            detail = "remote ad publish check returned invalid JSON"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote ad publish check returned invalid JSON"
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
    $OutputRoot = Join-Path $repoRoot "artifacts\ad-publish-checks\ad-publish-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$snapshot = Invoke-RemoteAdPublishSnapshot `
    -SshHost $HomeSshHost `
    -ProjectKey $ProjectKey `
    -ExpectedSlotIds $ExpectedSlotIds `
    -ExpectedAssetUrlPrefix $ExpectedAssetUrlPrefix

Write-TextFile -Path (Join-Path $outputDir "ad-publish-evidence.json") -Content $snapshot.json
$payload = $snapshot.json | ConvertFrom-Json
$totals = if ($payload.PSObject.Properties.Name -contains "totals") { $payload.totals } else { $null }
$expectedResults = if ($payload.PSObject.Properties.Name -contains "expectedSlotResults") { @($payload.expectedSlotResults) } else { @() }
$activeCreatives = if ($payload.PSObject.Properties.Name -contains "activeCreatives") { @($payload.activeCreatives) } else { @() }

$status = [string]$payload.status
$detail = [string]$payload.detail
$expectedSummary = @($expectedResults | ForEach-Object { "$($_.slotId):active=$($_.activeCreativeCount),valid=$($_.validAssetCreativeCount),passed=$($_.passed)" }) -join "; "
$activeSummary = @($activeCreatives | Select-Object -First 5 | ForEach-Object { "$($_.slotId)/$($_.creativeId)/$($_.assetHead.statusCode)/$($_.assetHead.contentType)" }) -join "; "
$summary = @"
status=$status
checkedAt=$(Format-IsoValue -Value $payload.checkedAt)
projectKey=$ProjectKey
outputDir=$outputDir
detail=$detail
slotTotal=$(if ($totals) { $totals.slots } else { "" })
activeCreativeCount=$(if ($totals) { $totals.activeCreatives } else { "" })
assetHeadCheckCount=$(if ($totals) { $totals.assetHeadChecks } else { "" })
expectedSlots=$expectedSummary
activeCreatives=$activeSummary
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
