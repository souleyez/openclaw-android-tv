import {
  BadRequestException,
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { RadioService } from '../radio/radio.service';

type PlatformBroadcastEnvelope = {
  broadcastId?: string;
  projectKey?: string;
  kind?: string;
  scope?: string;
  title?: string;
  body?: string;
  payload?: Record<string, unknown>;
  createdAt?: string;
  expiresAt?: string | null;
  idempotencyKey?: string;
};

const PROJECT_KEY = 'sonance';
const SHARED_RUNTIME_PROJECT_KEY = 'openclaw-android-tv';
const SHARED_RUNTIME_PROJECT_ALIASES = ['android-tv'];
const HEALTH_CAPABILITIES = ['health', 'broadcasts'];
const DEFAULT_VERSION = '2026.04.06';

@Injectable()
export class PlatformIntegrationService {
  private readonly version = this.resolveVersion();

  constructor(private readonly radioService: RadioService) {}

  getHealth(token: string | undefined, requestedProjectKey?: string) {
    this.assertPlatformToken(token);
    const projectKey = this.resolveAcceptedProjectKey(requestedProjectKey);

    return {
      status: 'ok',
      projectKey,
      acceptsBroadcast: true,
      capabilities: [...HEALTH_CAPABILITIES],
      version: this.version,
      message:
        projectKey === PROJECT_KEY
          ? 'integration healthy'
          : `integration healthy via shared ${PROJECT_KEY} runtime`,
    };
  }

  async acceptBroadcast(token: string | undefined, body: PlatformBroadcastEnvelope) {
    this.assertPlatformToken(token);

    const broadcastId = body.broadcastId?.trim();
    if (!broadcastId) {
      throw new BadRequestException('broadcastId is required');
    }

    const projectKey = this.resolveAcceptedProjectKey(body.projectKey);
    if (!projectKey) {
      throw new BadRequestException('projectKey is required');
    }

    const title = body.title?.trim();
    if (!title) {
      throw new BadRequestException('title is required');
    }

    const textTranscript = this.buildBroadcastBody(body);
    if (!textTranscript) {
      throw new BadRequestException('body or payload is required');
    }

    await this.radioService.createSystemBroadcastNotice({
      id: broadcastId,
      title,
      textTranscript,
      targetScope: this.normalizeScope(projectKey, body.scope),
    });

    return {
      status: 'accepted',
      broadcastId,
      projectKey,
      receivedAt: new Date().toISOString(),
      mode: 'async',
      message: 'queued',
    };
  }

  private assertPlatformToken(input: string | undefined) {
    const configuredToken = process.env.HOME_PLATFORM_TOKEN?.trim();
    if (!configuredToken) {
      throw new ServiceUnavailableException('HOME_PLATFORM_TOKEN is not configured');
    }

    const providedToken = input?.trim();
    if (!providedToken) {
      throw new UnauthorizedException('Missing home platform token');
    }

    if (providedToken !== configuredToken) {
      throw new UnauthorizedException('Invalid home platform token');
    }
  }

  private buildBroadcastBody(body: PlatformBroadcastEnvelope) {
    const text = body.body?.trim();
    if (text) {
      return text;
    }

    const payload = body.payload;
    if (payload == null || typeof payload !== 'object') {
      return '';
    }

    const serialized = JSON.stringify(payload);
    return serialized.length > 1200 ? `${serialized.slice(0, 1197)}...` : serialized;
  }

  private normalizeScope(projectKey: string, scope: string | undefined) {
    const trimmed = scope?.trim();
    if (!trimmed) {
      return this.defaultProjectScope(projectKey);
    }

    const normalized = trimmed.toLowerCase();
    if (normalized === 'global' || normalized === 'all' || normalized === 'default') {
      return this.defaultProjectScope(projectKey);
    }

    const segments = trimmed
      .split('/')
      .map((item) => item.trim())
      .filter((item) => item.length > 0);

    if (segments.length === 0) {
      return undefined;
    }

    const [first, ...rest] = segments;
    const firstToken = first.toLowerCase();
    if (
      rest.length > 0 &&
      (firstToken === PROJECT_KEY ||
        firstToken === SHARED_RUNTIME_PROJECT_KEY ||
        SHARED_RUNTIME_PROJECT_ALIASES.includes(firstToken) ||
        firstToken === 'radio-app' ||
        firstToken === 'radio app' ||
        firstToken === 'sonance-app')
    ) {
      return this.applyProjectScope(projectKey, rest.join(' / '));
    }

    return this.applyProjectScope(projectKey, segments.join(' / '));
  }

  private resolveAcceptedProjectKey(input: string | undefined) {
    const normalized = input?.trim().toLowerCase();
    if (!normalized || normalized === PROJECT_KEY) {
      return PROJECT_KEY;
    }
    if (
      normalized === SHARED_RUNTIME_PROJECT_KEY ||
      SHARED_RUNTIME_PROJECT_ALIASES.includes(normalized)
    ) {
      return SHARED_RUNTIME_PROJECT_KEY;
    }
    throw new BadRequestException(
      `projectKey must be ${PROJECT_KEY} or ${SHARED_RUNTIME_PROJECT_KEY}`,
    );
  }

  private defaultProjectScope(projectKey: string) {
    return projectKey === SHARED_RUNTIME_PROJECT_KEY ? 'android-tv' : undefined;
  }

  private applyProjectScope(projectKey: string, scope: string | undefined) {
    const trimmed = scope?.trim();
    if (projectKey !== SHARED_RUNTIME_PROJECT_KEY) {
      return trimmed || undefined;
    }
    if (!trimmed) {
      return 'android-tv';
    }
    if (trimmed.toLowerCase().startsWith('android-tv')) {
      return trimmed;
    }
    return `android-tv / ${trimmed}`;
  }

  private resolveVersion() {
    const envVersion = process.env.APP_VERSION?.trim();
    if (envVersion) {
      return envVersion;
    }

    const packageJsonPath = resolve(process.cwd(), 'package.json');
    if (existsSync(packageJsonPath)) {
      try {
        const raw = JSON.parse(readFileSync(packageJsonPath, 'utf8')) as {
          version?: unknown;
        };
        if (typeof raw.version === 'string' && raw.version.trim().length > 0) {
          return raw.version.trim();
        }
      } catch {}
    }

    return DEFAULT_VERSION;
  }
}
