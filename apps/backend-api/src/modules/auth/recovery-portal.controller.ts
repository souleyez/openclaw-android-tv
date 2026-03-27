import { Body, Controller, Get, Header, Post } from '@nestjs/common';

import { AuthService } from './auth.service';

class RecoverPortalDto {
  txHash!: string;
  chain?: string;
  amountUsd?: number;
}

class TransferPortalDto {
  fromDeviceUserId!: string;
  toDeviceUserId!: string;
  paymentProofTxHash!: string;
}

@Controller('recovery')
export class RecoveryPortalController {
  constructor(private readonly authService: AuthService) {}

  @Get()
  @Header('Content-Type', 'text/html; charset=utf-8')
  getPortalHtml(): string {
    return `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Sonance Recovery Portal</title>
    <style>
      :root {
        --bg: #0c1420;
        --panel: #162333;
        --panel-soft: #203246;
        --text: #eef7ff;
        --muted: #9fb6cb;
        --accent: #6ae6d8;
        --accent-2: #7cc6fe;
        --warn: #ffd166;
        --danger: #ff8f8f;
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        font-family: "Segoe UI", "PingFang SC", sans-serif;
        background:
          radial-gradient(circle at top left, rgba(106,230,216,.12), transparent 28%),
          radial-gradient(circle at top right, rgba(124,198,254,.12), transparent 24%),
          var(--bg);
        color: var(--text);
      }
      .page {
        max-width: 980px;
        margin: 0 auto;
        padding: 32px 20px 72px;
      }
      .hero {
        margin-bottom: 22px;
      }
      .hero h1 {
        margin: 0 0 10px;
        font-size: 34px;
      }
      .hero p {
        margin: 0;
        color: var(--muted);
        line-height: 1.6;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
        gap: 18px;
      }
      .card {
        background: linear-gradient(180deg, rgba(255,255,255,.03), rgba(255,255,255,.01)), var(--panel);
        border: 1px solid rgba(255,255,255,.08);
        border-radius: 22px;
        padding: 22px;
      }
      h2 {
        margin: 0 0 14px;
        font-size: 22px;
      }
      .hint {
        color: var(--muted);
        font-size: 14px;
        line-height: 1.6;
        margin-bottom: 14px;
      }
      label {
        display: block;
        font-size: 13px;
        margin: 12px 0 6px;
        color: var(--muted);
      }
      input {
        width: 100%;
        border: 1px solid rgba(255,255,255,.14);
        background: var(--panel-soft);
        color: var(--text);
        border-radius: 14px;
        padding: 12px 14px;
      }
      button {
        margin-top: 16px;
        border: 0;
        border-radius: 999px;
        background: linear-gradient(135deg, var(--accent), var(--accent-2));
        color: #0d1824;
        font-weight: 700;
        padding: 12px 18px;
        cursor: pointer;
      }
      button.secondary {
        background: rgba(255,255,255,.08);
        color: var(--text);
      }
      .result {
        margin-top: 14px;
        border-radius: 16px;
        padding: 14px;
        background: rgba(255,255,255,.05);
        white-space: pre-wrap;
        font-size: 14px;
        line-height: 1.5;
      }
      .footer {
        margin-top: 22px;
        color: var(--muted);
        font-size: 13px;
      }
      .pill {
        display: inline-block;
        border-radius: 999px;
        background: rgba(255,255,255,.08);
        padding: 6px 10px;
        margin-right: 8px;
        margin-bottom: 8px;
        font-size: 12px;
      }
    </style>
  </head>
  <body>
    <div class="page">
      <div class="hero">
        <h1>Sonance Recovery Portal</h1>
        <p>
          Use this portal if a device is broken, lost, or no longer available. Normal in-device identity help should still happen through the TV assistant.
        </p>
      </div>

      <div class="grid">
        <section class="card">
          <h2>Recover Device User</h2>
          <div class="hint">
            Use the latest payment proof to find the device user behind a previous top-up.
          </div>
          <label for="recover-tx">Tx Hash</label>
          <input id="recover-tx" placeholder="0x..." />
          <label for="recover-chain">Chain (optional)</label>
          <input id="recover-chain" placeholder="Polygon / Base / TRON / BSC" />
          <label for="recover-amount">Amount USD (optional)</label>
          <input id="recover-amount" placeholder="10" />
          <button id="recover-btn">Find Device User</button>
          <div id="recover-result" class="result">Waiting for payment proof...</div>
        </section>

        <section class="card">
          <h2>Transfer Entitlements</h2>
          <div class="hint">
            Move rights from one device user to another. The source device user loses rights after a successful transfer.
          </div>
          <label for="transfer-from">Source Device User ID</label>
          <input id="transfer-from" placeholder="device-user-old" />
          <label for="transfer-to">Target Device User ID</label>
          <input id="transfer-to" placeholder="device-user-new" />
          <label for="transfer-tx">Payment Proof Tx Hash</label>
          <input id="transfer-tx" placeholder="0x..." />
          <button id="transfer-btn">Transfer Rights</button>
          <div id="transfer-result" class="result">Waiting for transfer request...</div>
        </section>
      </div>

      <div class="footer">
        <span class="pill">Closed beta only</span>
        <span class="pill">Stablecoin proof based recovery</span>
        <span class="pill">One-device inheritance model</span>
      </div>
    </div>

    <script>
      async function postJson(url, body) {
        const response = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        });
        const text = await response.text();
        let payload = text;
        try { payload = JSON.parse(text); } catch (_) {}
        if (!response.ok) {
          throw new Error(typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2));
        }
        return payload;
      }

      document.getElementById('recover-btn').addEventListener('click', async () => {
        const txHash = document.getElementById('recover-tx').value.trim();
        const chain = document.getElementById('recover-chain').value.trim();
        const amount = document.getElementById('recover-amount').value.trim();
        const box = document.getElementById('recover-result');
        box.textContent = 'Checking payment proof...';
        try {
          const payload = await postJson('/api/auth/recover-by-payment', {
            txHash,
            chain: chain || undefined,
            amountUsd: amount ? Number(amount) : undefined,
          });
          box.textContent = JSON.stringify(payload, null, 2);
        } catch (error) {
          box.textContent = String(error.message || error);
        }
      });

      document.getElementById('transfer-btn').addEventListener('click', async () => {
        const fromDeviceUserId = document.getElementById('transfer-from').value.trim();
        const toDeviceUserId = document.getElementById('transfer-to').value.trim();
        const paymentProofTxHash = document.getElementById('transfer-tx').value.trim();
        const box = document.getElementById('transfer-result');
        box.textContent = 'Submitting transfer request...';
        try {
          const payload = await postJson('/api/auth/transfer-entitlements', {
            fromDeviceUserId,
            toDeviceUserId,
            paymentProofTxHash,
          });
          box.textContent = JSON.stringify(payload, null, 2);
        } catch (error) {
          box.textContent = String(error.message || error);
        }
      });
    </script>
  </body>
</html>`;
  }

  @Post('recover')
  async recover(@Body() body: RecoverPortalDto) {
    return this.authService.recoverDeviceUserByPaymentProof(body);
  }

  @Post('transfer')
  async transfer(@Body() body: TransferPortalDto) {
    return this.authService.transferDeviceEntitlements(body);
  }
}
