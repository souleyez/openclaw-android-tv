import { Body, Controller, Get, Header, Post, Query } from '@nestjs/common';

import { AdminService } from './admin.service';

class UpsertApiPoolAccountDto {
  id?: string;
  provider!: string;
  accountLabel!: string;
  planLabel!: string;
  status!: 'active' | 'expiring' | 'expired' | 'paused';
  renewsAt?: string;
  expiresAt?: string;
  notes?: string;
}

class ImportApiPoolCredentialsDto {
  accountId!: string;
  provider!: string;
  baseUrl!: string;
  model!: string;
  rawKeys!: string;
}

class UpdateDeviceUserEntitlementDto {
  deviceUserId!: string;
  planCode!: string;
  entitlementExpiresAt?: string;
  recoveryHint?: string;
  status?: 'active' | 'disabled';
}

class UpdateOrderStatusDto {
  orderId!: string;
  status!: 'pending' | 'confirming' | 'confirmed' | 'failed' | 'reviewing' | 'expired';
  reviewNote?: string;
}

class ActivateAvatarDto {
  avatarId!: string;
}

class CreateOtaReleaseDto {
  versionName!: string;
  versionCode!: number;
  releaseChannel!: 'stable' | 'beta' | 'internal';
  rolloutStatus!: 'draft' | 'rolling' | 'paused' | 'completed' | 'rolled_back';
  targetScope!: string;
  rolloutPercent!: number;
  deviceCount!: number;
  installSuccessRate!: number;
}

class UpdateOtaReleaseStatusDto {
  releaseId!: string;
  rolloutStatus!: 'draft' | 'rolling' | 'paused' | 'completed' | 'rolled_back';
  rolloutPercent?: number;
  deviceCount?: number;
  installSuccessRate?: number;
}

function normalizePage(input?: string): number | undefined {
  if (input == null || input === '') {
    return undefined;
  }

  const value = Number.parseInt(input, 10);
  return Number.isFinite(value) ? value : undefined;
}

@Controller('admin')
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get()
  @Header('Content-Type', 'text/html; charset=utf-8')
  getAdminHtml(): string {
    return `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>OpenClaw Admin</title>
    <style>
      :root {
        --bg: #09111b;
        --panel: #132131;
        --panel-2: #1c2f43;
        --text: #eef6ff;
        --muted: #9bb1c7;
        --accent: #6ae6d8;
        --accent2: #7cc6fe;
        --warn: #ffd166;
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        font-family: "Segoe UI", "PingFang SC", sans-serif;
        background: linear-gradient(180deg, #09111b, #0d1723);
        color: var(--text);
      }
      .page {
        max-width: 1200px;
        margin: 0 auto;
        padding: 28px 20px 56px;
      }
      h1 { margin: 0 0 10px; font-size: 34px; }
      p { color: var(--muted); line-height: 1.6; }
      .cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 14px;
        margin: 20px 0;
      }
      .card, .panel {
        background: linear-gradient(180deg, rgba(255,255,255,.03), rgba(255,255,255,.01)), var(--panel);
        border: 1px solid rgba(255,255,255,.08);
        border-radius: 20px;
        padding: 18px;
      }
      .metric { font-size: 28px; font-weight: 700; margin-top: 10px; }
      .layout {
        display: grid;
        grid-template-columns: 1.2fr .8fr;
        gap: 18px;
      }
      .stack { display: grid; gap: 18px; }
      .triple {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 18px;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;
      }
      th, td {
        text-align: left;
        padding: 10px 8px;
        border-bottom: 1px solid rgba(255,255,255,.06);
      }
      .pill {
        display: inline-block;
        padding: 5px 10px;
        border-radius: 999px;
        background: rgba(255,255,255,.08);
        font-size: 12px;
      }
      input, select, textarea {
        width: 100%;
        margin-top: 6px;
        border: 1px solid rgba(255,255,255,.12);
        border-radius: 12px;
        padding: 10px 12px;
        background: var(--panel-2);
        color: var(--text);
      }
      textarea { min-height: 90px; resize: vertical; }
      label { display: block; margin-top: 12px; color: var(--muted); font-size: 13px; }
      button {
        margin-top: 16px;
        border: 0;
        border-radius: 999px;
        padding: 12px 16px;
        background: linear-gradient(135deg, var(--accent), var(--accent2));
        color: #0b1621;
        font-weight: 700;
        cursor: pointer;
      }
      .result {
        margin-top: 14px;
        border-radius: 14px;
        padding: 12px;
        background: rgba(255,255,255,.04);
        white-space: pre-wrap;
        font-size: 13px;
      }
      @media (max-width: 900px) {
        .layout { grid-template-columns: 1fr; }
      }
    </style>
  </head>
  <body>
    <div class="page">
      <h1>OpenClaw Admin Console</h1>
      <p>Minimal operator view for closed beta. Track device users, recent orders, entitlement transfers, and manually managed model API pool accounts.</p>

      <div id="metrics" class="cards"></div>

      <div class="layout">
        <div class="stack">
          <section class="panel">
            <h2>Device Users</h2>
            <div id="device-users"></div>
          </section>
          <section class="panel">
            <h2>Recent Stablecoin Orders</h2>
            <div id="orders"></div>
          </section>
          <section class="panel">
            <h2>Entitlement Transfers</h2>
            <div id="transfers"></div>
          </section>
          <section class="panel">
            <h2>Recent Assistant Logs</h2>
            <div id="logs"></div>
          </section>
        </div>

        <div class="stack">
          <section class="panel">
            <h2>Model API Pool</h2>
            <div id="api-pool"></div>
          </section>
          <section class="panel">
            <h2>Add API Pool Account</h2>
            <label>Provider<input id="provider" placeholder="MiniMax" /></label>
            <label>Account Label<input id="accountLabel" placeholder="minimax-main-subscription" /></label>
            <label>Plan Label<input id="planLabel" placeholder="Monthly low-cost pool" /></label>
            <label>Status
              <select id="status">
                <option value="active">active</option>
                <option value="expiring">expiring</option>
                <option value="expired">expired</option>
                <option value="paused">paused</option>
              </select>
            </label>
            <label>Renews At<input id="renewsAt" placeholder="2026-04-01T00:00:00.000Z" /></label>
            <label>Expires At<input id="expiresAt" placeholder="2026-04-30T00:00:00.000Z" /></label>
            <label>Notes<textarea id="notes" placeholder="Manual operator notes"></textarea></label>
            <button id="save-btn">Save Pool Account</button>
            <div id="save-result" class="result">Waiting for manual input...</div>
          </section>
          <section class="panel">
            <h2>Adjust Device User Entitlement</h2>
            <label>Device User ID<input id="deviceUserId" placeholder="device-user-beta-a" /></label>
            <label>Plan Code<input id="devicePlanCode" placeholder="family" /></label>
            <label>Status
              <select id="deviceStatus">
                <option value="active">active</option>
                <option value="disabled">disabled</option>
              </select>
            </label>
            <label>Entitlement Expires At<input id="deviceExpiresAt" placeholder="2026-04-30T00:00:00.000Z" /></label>
            <label>Recovery Hint<textarea id="deviceRecoveryHint" placeholder="latest payment on Base/USDC"></textarea></label>
            <button id="device-save-btn">Save Device User</button>
            <div id="device-save-result" class="result">Waiting for entitlement update...</div>
          </section>
          <section class="panel">
            <h2>Update Order Status</h2>
            <label>Order ID<input id="orderId" placeholder="order_123" /></label>
            <label>Status
              <select id="orderStatus">
                <option value="reviewing">reviewing</option>
                <option value="failed">failed</option>
                <option value="expired">expired</option>
                <option value="confirmed">confirmed</option>
              </select>
            </label>
            <label>Review Note<textarea id="orderReviewNote" placeholder="manual operator note"></textarea></label>
            <button id="order-save-btn">Save Order Status</button>
            <div id="order-save-result" class="result">Waiting for order update...</div>
          </section>
          <section class="panel">
            <h2>Avatar Profiles</h2>
            <div id="avatars"></div>
            <label>Avatar ID<input id="avatarId" placeholder="avatar_warm_female" /></label>
            <button id="avatar-activate-btn">Activate Avatar</button>
            <div id="avatar-save-result" class="result">Waiting for avatar switch...</div>
          </section>
        </div>
      </div>
    </div>

    <script>
      function table(headers, rows) {
        if (!rows.length) return '<div class="pill">No data</div>';
        return '<table><thead><tr>' + headers.map(h => '<th>' + h + '</th>').join('') + '</tr></thead><tbody>' +
          rows.map(row => '<tr>' + row.map(cell => '<td>' + (cell ?? '') + '</td>').join('') + '</tr>').join('') +
          '</tbody></table>';
      }

      async function loadSummary() {
        const response = await fetch('/api/admin/summary');
        const payload = await response.json();

        document.getElementById('metrics').innerHTML = [
          ['Device Users', payload.counts.deviceUsers],
          ['Orders', payload.counts.orders],
          ['Transfers', payload.counts.transfers],
          ['API Pool', payload.counts.apiPoolAccounts],
        ].map(([label, value]) => '<div class="card"><div>' + label + '</div><div class="metric">' + value + '</div></div>').join('');

        document.getElementById('device-users').innerHTML = table(
          ['ID', 'Plan', 'Status', 'Entitlement Expires', 'Recovery Hint'],
          payload.deviceUsers.map(item => [item.id, item.planCode, item.status, item.entitlementExpiresAt || '-', item.recoveryHint || '-']),
        );
        document.getElementById('orders').innerHTML = table(
          ['Account', 'Asset', 'Status', 'Expires', 'Tx Hash'],
          payload.orders.map(item => [item.accountId, item.stablecoinSymbol + ' / ' + item.chain, item.status, item.expiresAt || '-', item.txHash || '-']),
        );
        document.getElementById('transfers').innerHTML = table(
          ['From', 'To', 'Reason', 'Tx Hash', 'Created'],
          payload.transfers.map(item => [item.fromAccountId, item.toAccountId, item.transferReason, item.paymentProofTxHash || '-', item.createdAt]),
        );
        document.getElementById('api-pool').innerHTML = table(
          ['Provider', 'Label', 'Plan', 'Status', 'Renews', 'Expires'],
          payload.apiPoolAccounts.map(item => [item.provider, item.accountLabel, item.planLabel, item.status, item.renewsAt || '-', item.expiresAt || '-']),
        );
        document.getElementById('logs').innerHTML = table(
          ['Time', 'Account', 'Kind', 'Mode', 'Route', 'User'],
          payload.logs.map(item => [item.createdAt, item.accountId, item.kind, item.mode, item.route || '-', item.userText || '-']),
        );
        document.getElementById('avatars').innerHTML = table(
          ['ID', 'Label', 'Type', 'Active', 'Updated'],
          payload.avatars.map(item => [item.id, item.avatarLabel, item.gender + ' / ' + item.ageGroup, item.active ? 'yes' : '-', item.updatedAt]),
        );
      }

      document.getElementById('save-btn').addEventListener('click', async () => {
        const result = document.getElementById('save-result');
        result.textContent = 'Saving...';
        try {
          const response = await fetch('/api/admin/api-pool-accounts', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              provider: document.getElementById('provider').value.trim(),
              accountLabel: document.getElementById('accountLabel').value.trim(),
              planLabel: document.getElementById('planLabel').value.trim(),
              status: document.getElementById('status').value,
              renewsAt: document.getElementById('renewsAt').value.trim() || undefined,
              expiresAt: document.getElementById('expiresAt').value.trim() || undefined,
              notes: document.getElementById('notes').value.trim() || undefined,
            }),
          });
          const payload = await response.json();
          result.textContent = JSON.stringify(payload, null, 2);
          await loadSummary();
        } catch (error) {
          result.textContent = String(error.message || error);
        }
      });

      document.getElementById('device-save-btn').addEventListener('click', async () => {
        const result = document.getElementById('device-save-result');
        result.textContent = 'Saving...';
        try {
          const response = await fetch('/api/admin/device-users/entitlement', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              deviceUserId: document.getElementById('deviceUserId').value.trim(),
              planCode: document.getElementById('devicePlanCode').value.trim(),
              status: document.getElementById('deviceStatus').value,
              entitlementExpiresAt: document.getElementById('deviceExpiresAt').value.trim() || undefined,
              recoveryHint: document.getElementById('deviceRecoveryHint').value.trim() || undefined,
            }),
          });
          const payload = await response.json();
          result.textContent = JSON.stringify(payload, null, 2);
          await loadSummary();
        } catch (error) {
          result.textContent = String(error.message || error);
        }
      });

      document.getElementById('order-save-btn').addEventListener('click', async () => {
        const result = document.getElementById('order-save-result');
        result.textContent = 'Saving...';
        try {
          const response = await fetch('/api/admin/orders/status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              orderId: document.getElementById('orderId').value.trim(),
              status: document.getElementById('orderStatus').value,
              reviewNote: document.getElementById('orderReviewNote').value.trim() || undefined,
            }),
          });
          const payload = await response.json();
          result.textContent = JSON.stringify(payload, null, 2);
          await loadSummary();
        } catch (error) {
          result.textContent = String(error.message || error);
        }
      });

      document.getElementById('avatar-activate-btn').addEventListener('click', async () => {
        const result = document.getElementById('avatar-save-result');
        result.textContent = 'Saving...';
        try {
          const response = await fetch('/api/admin/avatar/activate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              avatarId: document.getElementById('avatarId').value.trim(),
            }),
          });
          const payload = await response.json();
          result.textContent = JSON.stringify(payload, null, 2);
          await loadSummary();
        } catch (error) {
          result.textContent = String(error.message || error);
        }
      });

      loadSummary();
    </script>
  </body>
</html>`;
  }

  @Get('summary')
  async getSummary() {
    return this.adminService.getDashboardSummary();
  }

  @Get('finance')
  async getFinanceSnapshot() {
    return this.adminService.getFinanceSnapshot();
  }

  @Get('risk')
  async getRiskSnapshot() {
    return this.adminService.getRiskSnapshot();
  }

  @Get('ota')
  async getOtaSnapshot() {
    return this.adminService.getOtaSnapshot();
  }

  @Post('ota/releases')
  async createOtaRelease(@Body() body: CreateOtaReleaseDto) {
    return this.adminService.createOtaRelease({
      versionName: body.versionName,
      versionCode: Number(body.versionCode),
      releaseChannel: body.releaseChannel,
      rolloutStatus: body.rolloutStatus,
      targetScope: body.targetScope,
      rolloutPercent: Number(body.rolloutPercent),
      deviceCount: Number(body.deviceCount),
      installSuccessRate: Number(body.installSuccessRate),
    });
  }

  @Post('ota/releases/status')
  async updateOtaReleaseStatus(@Body() body: UpdateOtaReleaseStatusDto) {
    return this.adminService.updateOtaReleaseStatus({
      releaseId: body.releaseId,
      rolloutStatus: body.rolloutStatus,
      rolloutPercent:
        body.rolloutPercent == null ? undefined : Number(body.rolloutPercent),
      deviceCount: body.deviceCount == null ? undefined : Number(body.deviceCount),
      installSuccessRate:
        body.installSuccessRate == null
          ? undefined
          : Number(body.installSuccessRate),
    });
  }

  @Get('device-users')
  async listDeviceUsers(
    @Query('q') q?: string,
    @Query('status') status?: 'active' | 'disabled',
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.adminService.listDeviceUsers(
      { q, status },
      { page: normalizePage(page), pageSize: normalizePage(pageSize) },
    );
  }

  @Get('orders')
  async listOrders(
    @Query('q') q?: string,
    @Query('status')
    status?: 'pending' | 'confirming' | 'confirmed' | 'failed' | 'reviewing' | 'expired',
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.adminService.listOrders(
      { q, status },
      { page: normalizePage(page), pageSize: normalizePage(pageSize) },
    );
  }

  @Get('logs')
  async listLogs(
    @Query('q') q?: string,
    @Query('mode') mode?: string,
    @Query('transportMode') transportMode?: string,
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.adminService.listLogs(
      { q, mode, transportMode },
      { page: normalizePage(page), pageSize: normalizePage(pageSize) },
    );
  }

  @Get('api-pool-accounts')
  async listApiPoolAccounts(
    @Query('q') q?: string,
    @Query('status') status?: 'active' | 'expiring' | 'expired' | 'paused',
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ) {
    return this.adminService.listApiPoolAccounts(
      { q, status },
      { page: normalizePage(page), pageSize: normalizePage(pageSize) },
    );
  }

  @Post('api-pool-accounts')
  async upsertApiPoolAccount(@Body() body: UpsertApiPoolAccountDto) {
    return this.adminService.upsertApiPoolAccount(body);
  }

  @Post('api-pool-accounts/import')
  async importApiPoolCredentials(@Body() body: ImportApiPoolCredentialsDto) {
    return this.adminService.importApiPoolCredentials(body);
  }

  @Post('device-users/entitlement')
  async updateDeviceUserEntitlement(@Body() body: UpdateDeviceUserEntitlementDto) {
    return this.adminService.updateDeviceUserEntitlement(body);
  }

  @Post('orders/status')
  async updateOrderStatus(@Body() body: UpdateOrderStatusDto) {
    return this.adminService.updateOrderStatus(body);
  }

  @Post('avatar/activate')
  async activateAvatar(@Body() body: ActivateAvatarDto) {
    return this.adminService.activateAvatarProfile(body.avatarId);
  }
}
