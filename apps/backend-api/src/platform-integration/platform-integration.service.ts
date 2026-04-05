import { BadRequestException, Injectable, UnauthorizedException } from '@nestjs/common';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  PLATFORM_INTEGRATION_CAPABILITIES,
  PLATFORM_INTEGRATION_STATE_FILE,
  PLATFORM_PROJECT_KEY,
  PLATFORM_SERVICE_NAME,
} from './platform-integration.constants';

export interface PlatformBroadcastRequest {
  broadcastId: string;
  projectKey: string;
  kind: string;
  scope: string;
  title: string;
  body?: string | null;
  payload?: Record<string, unknown> | null;
  createdAt: string;
  expiresAt?: string | null;
  idempotencyKey?: string | null;
}

interface PlatformBroadcastReceipt {
  idempotencyKey: string;
  broadcastId: string;
  projectKey: string;
  kind: string;
  scope: string;
  title: string;
  createdAt: string;
  expiresAt?: string | null;
  receivedAt: string;
}

interface PlatformIntegrationState {
  lastBroadcast?: PlatformBroadcastReceipt;
  receipts: PlatformBroadcastReceipt[];
}

const MAX_RECEIPTS = 100;

@Injectable()
export class PlatformIntegrationService {
  private readonly dataDir = resolve(process.cwd(), 'data');
  private readonly statePath = resolve(this.dataDir, PLATFORM_INTEGRATION_STATE_FILE);

  getHealth(platformToken?: string) {
    this.assertAuthorized(platformToken);
    return {
      status: 'ok',
      projectKey: PLATFORM_PROJECT_KEY,
      acceptsBroadcast: true,
      capabilities: [...PLATFORM_INTEGRATION_CAPABILITIES],
      version: this.resolveVersion(),
      message: 'integration healthy',
    };
  }

  acceptBroadcast(platformToken: string | undefined, request: PlatformBroadcastRequest) {
    this.assertAuthorized(platformToken);
    const normalizedRequest = this.normalizeRequest(request);
    const state = this.readState();
    const duplicate = state.receipts.find(
      (item) =>
        item.idempotencyKey === normalizedRequest.idempotencyKey ||
        item.broadcastId === normalizedRequest.broadcastId,
    );

    if (duplicate != null) {
      return {
        status: 'accepted',
        broadcastId: duplicate.broadcastId,
        projectKey: PLATFORM_PROJECT_KEY,
        receivedAt: duplicate.receivedAt,
        mode: 'async',
        message: 'duplicate ignored',
      };
    }

    const receivedAt = new Date().toISOString();
    const receipt: PlatformBroadcastReceipt = {
      idempotencyKey: normalizedRequest.idempotencyKey,
      broadcastId: normalizedRequest.broadcastId,
      projectKey: PLATFORM_PROJECT_KEY,
      kind: normalizedRequest.kind,
      scope: normalizedRequest.scope,
      title: normalizedRequest.title,
      createdAt: normalizedRequest.createdAt,
      expiresAt: normalizedRequest.expiresAt ?? null,
      receivedAt,
    };

    const nextState: PlatformIntegrationState = {
      lastBroadcast: receipt,
      receipts: [receipt, ...state.receipts].slice(0, MAX_RECEIPTS),
    };

    this.writeState(nextState);

    return {
      status: 'accepted',
      broadcastId: receipt.broadcastId,
      projectKey: PLATFORM_PROJECT_KEY,
      receivedAt,
      mode: 'async',
      message: 'queued',
    };
  }

  private assertAuthorized(platformToken?: string) {
    const expectedToken = (process.env.HOME_PLATFORM_TOKEN ?? '').trim();
    if (!expectedToken) {
      return;
    }

    if ((platformToken ?? '').trim() !== expectedToken) {
      throw new UnauthorizedException('PLATFORM_TOKEN_INVALID');
    }
  }

  private normalizeRequest(request: PlatformBroadcastRequest) {
    if (request == null || typeof request !== 'object') {
      throw new BadRequestException('PLATFORM_BROADCAST_INVALID');
    }

    const broadcastId = String(request.broadcastId ?? '').trim();
    const projectKey = String(request.projectKey ?? '').trim();
    const kind = String(request.kind ?? '').trim();
    const scope = String(request.scope ?? '').trim();
    const title = String(request.title ?? '').trim();
    const createdAt = String(request.createdAt ?? '').trim();
    const idempotencyKey = String(
      request.idempotencyKey ?? request.broadcastId ?? '',
    ).trim();

    if (!broadcastId || !projectKey || !kind || !scope || !title || !createdAt || !idempotencyKey) {
      throw new BadRequestException('PLATFORM_BROADCAST_INVALID');
    }

    if (projectKey !== PLATFORM_PROJECT_KEY) {
      throw new BadRequestException('PLATFORM_PROJECT_KEY_MISMATCH');
    }

    return {
      broadcastId,
      projectKey,
      kind,
      scope,
      title,
      createdAt,
      expiresAt: request.expiresAt ?? null,
      idempotencyKey,
    };
  }

  private resolveVersion() {
    return (process.env.APP_VERSION ?? process.env.npm_package_version ?? '0.1.0').trim();
  }

  private readState(): PlatformIntegrationState {
    if (!existsSync(this.statePath)) {
      return { receipts: [] };
    }

    try {
      const parsed = JSON.parse(readFileSync(this.statePath, 'utf8')) as PlatformIntegrationState;
      const receipts = Array.isArray(parsed.receipts)
        ? parsed.receipts.filter((item): item is PlatformBroadcastReceipt => {
            return (
              item != null &&
              typeof item.idempotencyKey === 'string' &&
              typeof item.broadcastId === 'string' &&
              typeof item.projectKey === 'string' &&
              typeof item.kind === 'string' &&
              typeof item.scope === 'string' &&
              typeof item.title === 'string' &&
              typeof item.createdAt === 'string' &&
              typeof item.receivedAt === 'string'
            );
          })
        : [];

      return {
        lastBroadcast:
          parsed.lastBroadcast &&
          typeof parsed.lastBroadcast.broadcastId === 'string' &&
          typeof parsed.lastBroadcast.receivedAt === 'string'
            ? parsed.lastBroadcast
            : receipts[0],
        receipts,
      };
    } catch {
      return { receipts: [] };
    }
  }

  private writeState(state: PlatformIntegrationState) {
    mkdirSync(this.dataDir, { recursive: true });
    writeFileSync(this.statePath, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
  }
}
