param(
    [string]$OutputRoot = "",
    [string]$ProjectKey = "openclaw-android-tv",
    [string]$HomeSshHost = "root@8.155.8.7",
    [int]$ExpectedSmokeAmountCents = 1,
    [string]$ExpectedSmokeCurrency = "CNY",
    [string[]]$ExpectedSmokeSkus = @("openclaw-tv-ai-service-30d", "openclaw-tv-vip-30d", "openclaw-tv-model-renewal-30d"),
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

function Invoke-RemotePaymentRenewalSnapshot {
    param(
        [string]$SshHost,
        [string]$ProjectKey,
        [int]$ExpectedAmountCents,
        [string]$ExpectedCurrency,
        [string[]]$ExpectedSkus
    )

    $skuCsv = ($ExpectedSkus | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ","
    $remoteScript = @'
set -euo pipefail
PROJECT_KEY="__PROJECT_KEY__"
EXPECTED_AMOUNT_CENTS="__EXPECTED_AMOUNT_CENTS__"
EXPECTED_CURRENCY="__EXPECTED_CURRENCY__"
EXPECTED_SMOKE_SKUS="__EXPECTED_SMOKE_SKUS__"
export PROJECT_KEY EXPECTED_AMOUNT_CENTS EXPECTED_CURRENCY EXPECTED_SMOKE_SKUS

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

const projectKey = process.env.PROJECT_KEY;
const headerName = process.env.ADMIN_HEADER_NAME;
const adminSecret = process.env.ADMIN_SECRET;
const expectedAmountCents = Number(process.env.EXPECTED_AMOUNT_CENTS || 0);
const expectedCurrency = String(process.env.EXPECTED_CURRENCY || '').toUpperCase();
const expectedSkus = new Set(String(process.env.EXPECTED_SMOKE_SKUS || '').split(',').map((item) => item.trim()).filter(Boolean));

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

function sortByTimeDesc(items, key) {
  return [...(items || [])].sort((left, right) => String(right[key] || '').localeCompare(String(left[key] || '')));
}

function countBy(items, key) {
  return (items || []).reduce((acc, item) => {
    const value = String(item[key] || 'unknown');
    acc[value] = (acc[value] || 0) + 1;
    return acc;
  }, {});
}

function sanitizePaidOrder(order) {
  if (!order) {
    return null;
  }
  return {
    sku: order.sku || '',
    title: order.title || '',
    paymentState: order.paymentState || '',
    amountCents: Number(order.amountCents || 0),
    currency: order.currency || '',
    renewalPlanCode: order.renewalPlanCode || '',
    renewalPriorityClass: order.renewalPriorityClass || '',
    renewalDurationSeconds: Number(order.renewalDurationSeconds || 0),
    paidAt: order.paidAt || '',
    updatedAt: order.updatedAt || '',
    hasProviderOrderId: Boolean(order.providerOrderId),
    hasProviderTransactionId: Boolean(order.providerTransactionId),
    accountIdSuffix: String(order.accountId || '').slice(-8),
    deviceIdSuffix: String(order.deviceId || '').slice(-8),
  };
}

function sanitizeLease(lease) {
  if (!lease) {
    return null;
  }
  return {
    provider: lease.provider || '',
    model: lease.model || '',
    leaseMode: lease.leaseMode || '',
    providerScope: lease.providerScope || '',
    active: Boolean(lease.active),
    expiresAt: lease.expiresAt || '',
    updatedAt: lease.updatedAt || '',
    lastRenewedAt: lease.lastRenewedAt || '',
  };
}

(async () => {
  const [ordersResponse, leasesResponse, sessionsResponse] = await Promise.all([
    getJson(`/api/admin/model-renewal-payment-orders?projectKey=${encodeURIComponent(projectKey)}`),
    getJson(`/api/admin/model-leases?projectKey=${encodeURIComponent(projectKey)}`),
    getJson(`/api/admin/resource-sessions?projectKey=${encodeURIComponent(projectKey)}`),
  ]);
  const orders = ordersResponse.json.items || [];
  const leases = leasesResponse.json.items || [];
  const sessions = sessionsResponse.json.items || [];
  const paidOrders = orders.filter((item) => item.paymentState === 'paid');
  const smokePaidOrders = paidOrders.filter((item) => (
    expectedSkus.has(item.sku)
    && Number(item.amountCents || 0) === expectedAmountCents
    && String(item.currency || '').toUpperCase() === expectedCurrency
    && Boolean(item.providerOrderId)
    && Boolean(item.providerTransactionId)
  ));
  const latestPaid = sortByTimeDesc(paidOrders, 'paidAt')[0] || sortByTimeDesc(paidOrders, 'updatedAt')[0] || null;
  const latestSmokePaid = sortByTimeDesc(smokePaidOrders, 'paidAt')[0] || sortByTimeDesc(smokePaidOrders, 'updatedAt')[0] || null;
  const activeLeases = leases.filter((item) => item.active === true || (!item.revokedAt && (!item.expiresAt || Date.parse(item.expiresAt) > Date.now())));
  const latestActiveLease = sortByTimeDesc(activeLeases, 'updatedAt')[0] || sortByTimeDesc(activeLeases, 'lastRenewedAt')[0] || sortByTimeDesc(activeLeases, 'expiresAt')[0] || null;
  const endpointStatusOk = [ordersResponse, leasesResponse, sessionsResponse].every((item) => item.statusCode === 200 && item.json.status === 'ok');
  const status = !endpointStatusOk ? 'FAIL' : (smokePaidOrders.length > 0 ? 'PASS' : 'PENDING');
  const detail = !endpointStatusOk
    ? `admin endpoint status mismatch orders=${ordersResponse.statusCode} leases=${leasesResponse.statusCode} sessions=${sessionsResponse.statusCode}`
    : (smokePaidOrders.length > 0
      ? `paid smoke order found; paidCount=${paidOrders.length}; activeLeaseCount=${activeLeases.length}`
      : `no paid smoke order found; paidCount=${paidOrders.length}; activeLeaseCount=${activeLeases.length}`);

  console.log(JSON.stringify({
    status,
    detail,
    projectKey,
    checkedAt: new Date().toISOString(),
    source: 'remote-admin',
    expectedSmoke: {
      amountCents: expectedAmountCents,
      currency: expectedCurrency,
      skus: [...expectedSkus],
    },
    orders: {
      endpointStatusCode: ordersResponse.statusCode,
      total: orders.length,
      byPaymentState: countBy(orders, 'paymentState'),
      paidCount: paidOrders.length,
      smokePaidCount: smokePaidOrders.length,
      latestPaid: sanitizePaidOrder(latestPaid),
      latestSmokePaid: sanitizePaidOrder(latestSmokePaid),
    },
    modelLeases: {
      endpointStatusCode: leasesResponse.statusCode,
      total: leases.length,
      activeCount: activeLeases.length,
      latestActive: sanitizeLease(latestActiveLease),
    },
    resourceSessions: {
      endpointStatusCode: sessionsResponse.statusCode,
      total: sessions.length,
      byQueueStatus: countBy(sessions, 'queueStatus'),
    },
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
    $remoteScript = $remoteScript.Replace('"__EXPECTED_AMOUNT_CENTS__"', (ConvertTo-BashLiteral -Value ([string]$ExpectedAmountCents)))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_CURRENCY__"', (ConvertTo-BashLiteral -Value $ExpectedCurrency))
    $remoteScript = $remoteScript.Replace('"__EXPECTED_SMOKE_SKUS__"', (ConvertTo-BashLiteral -Value $skuCsv))

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
            detail = "remote payment renewal check failed"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote payment renewal check failed"
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
            detail = "remote payment renewal check returned invalid JSON"
            json = ([pscustomobject]@{
                status = "FAIL"
                detail = "remote payment renewal check returned invalid JSON"
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
    $OutputRoot = Join-Path $repoRoot "artifacts\payment-renewal-checks\payment-renewal-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputDir = (Resolve-Path $OutputRoot).Path

$snapshot = Invoke-RemotePaymentRenewalSnapshot `
    -SshHost $HomeSshHost `
    -ProjectKey $ProjectKey `
    -ExpectedAmountCents $ExpectedSmokeAmountCents `
    -ExpectedCurrency $ExpectedSmokeCurrency `
    -ExpectedSkus $ExpectedSmokeSkus

Write-TextFile -Path (Join-Path $outputDir "payment-renewal-evidence.json") -Content $snapshot.json
$payload = $snapshot.json | ConvertFrom-Json
$orders = if ($payload.PSObject.Properties.Name -contains "orders") { $payload.orders } else { $null }
$leases = if ($payload.PSObject.Properties.Name -contains "modelLeases") { $payload.modelLeases } else { $null }
$sessions = if ($payload.PSObject.Properties.Name -contains "resourceSessions") { $payload.resourceSessions } else { $null }

$status = [string]$payload.status
$detail = [string]$payload.detail
$summary = @"
status=$status
checkedAt=$(Format-IsoValue -Value $payload.checkedAt)
projectKey=$ProjectKey
outputDir=$outputDir
detail=$detail
orderTotal=$(if ($orders) { $orders.total } else { "" })
paidCount=$(if ($orders) { $orders.paidCount } else { "" })
smokePaidCount=$(if ($orders) { $orders.smokePaidCount } else { "" })
latestSmokePaidAt=$(if ($orders -and $orders.latestSmokePaid) { Format-IsoValue -Value $orders.latestSmokePaid.paidAt } else { "" })
activeModelLeaseCount=$(if ($leases) { $leases.activeCount } else { "" })
resourceSessionTotal=$(if ($sessions) { $sessions.total } else { "" })
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
