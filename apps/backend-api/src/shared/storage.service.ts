import { Injectable } from '@nestjs/common';
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { RegisteredDeviceRecord } from '../modules/device/device.service';

type SQLiteDatabase = {
  exec(sql: string): void;
  prepare(sql: string): {
    all(...params: unknown[]): unknown[];
    get(...params: unknown[]): unknown;
    run(...params: unknown[]): unknown;
  };
};

const { DatabaseSync } = require('node:sqlite') as {
  DatabaseSync: new (path: string) => SQLiteDatabase;
};

export interface AssistantLogRecord {
  id: string;
  accountId: string;
  kind: 'voice_turn' | 'chat_turn';
  deviceId?: string;
  locale: string;
  userText: string;
  assistantText: string;
  appId?: string;
  action?: string;
  queryText?: string | null;
  mode: 'control' | 'chat';
  modelProvider: string;
  route?: string;
  transportMode?: string;
  tokenUsage: number;
  bootstrapTokenRemaining: number;
  createdAt: string;
}

export interface BillingPlanRecord {
  id: string;
  code: string;
  displayName: string;
  monthlyPriceUsd: number;
  tokenGrantMonthly: number;
  deviceLimit: number;
  billingCycle: string;
  active: boolean;
}

export interface WalletLedgerRecord {
  id: string;
  accountId: string;
  entryType: 'grant' | 'topup' | 'usage' | 'adjustment';
  amount: number;
  currency: 'token' | 'usd';
  source: string;
  referenceId?: string;
  note?: string;
  createdAt: string;
}

export interface StablecoinPaymentOrderRecord {
  id: string;
  accountId: string;
  paymentMethod: 'stablecoin';
  stablecoinSymbol: string;
  chain: string;
  walletAddress: string;
  amountUsd: number;
  amountToken: number;
  status:
    | 'pending'
    | 'confirming'
    | 'confirmed'
    | 'failed'
    | 'reviewing'
    | 'expired';
  txHash?: string;
  confirmations: number;
  reviewNote?: string;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceShareBindingRecord {
  id: string;
  ownerAccountId: string;
  sharedAccountId: string;
  deviceUuid: string;
  bindingScope: 'api_key' | 'household';
  status: 'active' | 'revoked';
  createdAt: string;
}

export interface DeviceTokenUsageEventRecord {
  id: string;
  accountId: string;
  deviceUuid: string;
  amountConsumed: number;
  triggerSource: string;
  bootstrapTokenRemaining: number;
  createdAt: string;
}

export interface TesterAccountRecord {
  id: string;
  email: string;
  displayName: string;
  planCode: string;
  status: 'active' | 'disabled';
  createdAt: string;
}

export interface TesterInviteCodeRecord {
  code: string;
  accountId: string;
  label: string;
  active: boolean;
  createdAt: string;
}

export interface AuthSessionRecord {
  token: string;
  accountId: string;
  createdAt: string;
  lastSeenAt: string;
}

export interface AdminAllowedEmailRecord {
  email: string;
  displayName: string;
  status: 'active' | 'disabled';
  createdAt: string;
}

export interface AdminLoginCodeRecord {
  id: string;
  email: string;
  code: string;
  expiresAt: string;
  usedAt?: string;
  createdAt: string;
}

export interface AdminSessionRecord {
  token: string;
  email: string;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
}

export interface DeviceUserProfileRecord {
  id: string;
  displayName: string;
  planCode: string;
  status: 'active' | 'disabled';
  recoveryHint?: string;
  entitlementExpiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceEntitlementTransferRecord {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  transferReason: string;
  paymentProofTxHash?: string;
  createdAt: string;
}

export interface AvatarProfileRecord {
  id: string;
  name: string;
  avatarLabel: string;
  gender: 'female' | 'male' | 'child' | 'senior' | 'neutral';
  ageGroup: 'youth' | 'adult' | 'senior';
  primaryColorHex: string;
  secondaryColorHex: string;
  accentColorHex: string;
  assetUrl?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiPoolAccountRecord {
  id: string;
  provider: string;
  accountLabel: string;
  planLabel: string;
  status: 'active' | 'expiring' | 'expired' | 'paused';
  renewsAt?: string;
  expiresAt?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApiPoolCredentialRecord {
  id: string;
  accountId: string;
  provider: string;
  label: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  status: 'active' | 'disabled' | 'revoked';
  createdAt: string;
  updatedAt: string;
}

export interface ApiPoolLeaseRecord {
  id: string;
  accountId: string;
  credentialId: string;
  deviceUserId: string;
  deviceUuid: string;
  provider: string;
  baseUrl: string;
  model: string;
  status: 'active' | 'released' | 'expired';
  leasedAt: string;
  expiresAt: string;
  releasedAt?: string;
}

export interface OtaReleaseRecord {
  id: string;
  versionName: string;
  versionCode: number;
  releaseChannel: 'stable' | 'beta' | 'internal';
  rolloutStatus: 'draft' | 'rolling' | 'paused' | 'completed' | 'rolled_back';
  targetScope: string;
  rolloutPercent: number;
  deviceCount: number;
  installSuccessRate: number;
  notificationMode: 'broadcast';
  downloadPolicy: 'idle_background';
  installPolicy: 'next_boot';
  reportPolicy: 'lazy';
  reportDelayMinutes: number;
  artifactUrl?: string;
  releaseNotes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OtaDeviceReportRecord {
  id: string;
  releaseId: string;
  accountId: string;
  deviceUuid: string;
  currentVersionCode: number;
  targetVersionCode: number;
  status:
    | 'announced'
    | 'queued'
    | 'downloading'
    | 'downloaded'
    | 'staged'
    | 'installing'
    | 'installed_pending_report'
    | 'reported'
    | 'failed';
  progressPercent: number;
  note?: string;
  reportedAt: string;
  updatedAt: string;
}

export interface TvHomeConfigRecord {
  id: string;
  countryCode: string;
  regionCode?: string;
  backgroundImageUrl?: string;
  featuredAppIds: string[];
  status: 'active' | 'draft';
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ClientConfigReleaseRecord {
  id: string;
  versionName: string;
  versionCode: number;
  targetScope: string;
  configKey: string;
  payloadJson: string;
  rolloutStatus: 'draft' | 'rolling' | 'paused' | 'completed' | 'rolled_back';
  notificationMode: 'broadcast';
  fetchPolicy: 'idle_background';
  applyPolicy: 'idle_apply' | 'next_boot';
  releaseNotes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RadioStationRecord {
  id: string;
  name: string;
  country: string;
  region?: string;
  city: string;
  language: string;
  bandLabel: string;
  genre: string;
  streamUrl: string;
  homepageUrl?: string;
  logoUrl?: string;
  legalNotes?: string;
  isActive: boolean;
  sortOrder: number;
  lastCheckedAt: string;
  lastHealthStatus?: 'healthy' | 'degraded' | 'unknown';
  consecutiveFailures?: number;
  lastHealthError?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RadioBroadcastRecord {
  id: string;
  stationId?: string;
  accountId: string;
  title: string;
  sourceKind: 'user' | 'ai' | 'system';
  textTranscript?: string;
  audioPath?: string;
  durationMs: number;
  status: 'uploaded' | 'ready' | 'failed';
  targetScope?: string;
  createdAt: string;
  updatedAt: string;
}

interface LegacyJsonState {
  devices?: RegisteredDeviceRecord[];
  logs?: AssistantLogRecord[];
}

@Injectable()
export class StorageService {
  private readonly dataDir = resolve(process.cwd(), 'data');
  private readonly dbPath = resolve(this.dataDir, 'app.db');
  private readonly legacyJsonPath = resolve(this.dataDir, 'storage.json');
  private readonly schemaVersion = 21;
  private readonly db: SQLiteDatabase;
  private readonly logFlushDelayMs = this.resolveLogFlushDelayMs();
  private readonly logFlushBatchSize = this.resolveLogFlushBatchSize();
  private bufferedLogs: AssistantLogRecord[] = [];
  private logFlushTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    mkdirSync(this.dataDir, { recursive: true });
    this.db = new DatabaseSync(this.dbPath);
    this.initialize();
  }

  async getDevices(accountId: string): Promise<RegisteredDeviceRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          device_uuid,
          device_name,
          android_version,
          is_android_tv,
          status,
          binding_status,
          bootstrap_token_grant,
          bootstrap_token_remaining
        FROM devices
        WHERE account_id = ?
        ORDER BY device_name ASC
        `,
      )
      .all(accountId) as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapDeviceRow(row));
  }

  async saveDevices(accountId: string, devices: RegisteredDeviceRecord[]): Promise<void> {
    const deleteStatement = this.db.prepare('DELETE FROM devices WHERE account_id = ?');
    const deleteByUuidStatement = this.db.prepare(
      'DELETE FROM devices WHERE device_uuid = ?',
    );
    const insertStatement = this.db.prepare(`
      INSERT INTO devices (
        id,
        account_id,
        device_uuid,
        device_name,
        android_version,
        is_android_tv,
        status,
        binding_status,
        bootstrap_token_grant,
        bootstrap_token_remaining
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    this.runInTransaction(() => {
      deleteStatement.run(accountId);
      for (const device of devices) {
        deleteByUuidStatement.run(device.deviceUuid);
        insertStatement.run(
          device.id,
          accountId,
          device.deviceUuid,
          device.deviceName,
          device.androidVersion,
          device.isAndroidTv ? 1 : 0,
          device.status,
          device.bindingStatus,
          device.bootstrapTokenGrant,
          device.bootstrapTokenRemaining,
        );
      }
    });
  }

  async appendLog(log: AssistantLogRecord): Promise<void> {
    this.bufferedLogs.push(log);

    if (this.bufferedLogs.length >= this.logFlushBatchSize) {
      this.flushBufferedLogs();
      return;
    }

    if (this.logFlushTimer == null) {
      this.logFlushTimer = setTimeout(() => {
        this.flushBufferedLogs();
      }, this.logFlushDelayMs);
    }
  }

  async getLogs(accountId: string): Promise<AssistantLogRecord[]> {
    this.flushBufferedLogs();
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          kind,
          device_id,
          locale,
          user_text,
          assistant_text,
          app_id,
          action,
          query_text,
          mode,
          model_provider,
            route,
            transport_mode,
            token_usage,
            bootstrap_token_remaining,
            created_at
        FROM assistant_logs
        WHERE account_id = ?
        ORDER BY created_at DESC
        LIMIT 100
        `,
      )
      .all(accountId) as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapLogRow(row));
  }

  async listAllLogs(limit = 100): Promise<AssistantLogRecord[]> {
    this.flushBufferedLogs();
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          kind,
          device_id,
          locale,
          user_text,
          assistant_text,
          app_id,
          action,
          query_text,
          mode,
          model_provider,
          route,
          transport_mode,
          token_usage,
          bootstrap_token_remaining,
          created_at
        FROM assistant_logs
        ORDER BY created_at DESC
        LIMIT ?
        `,
      )
      .all(limit) as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapLogRow(row));
  }

  async getBillingPlans(): Promise<BillingPlanRecord[]> {
    this.flushBufferedLogs();
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          code,
          display_name,
          monthly_price_usd,
          token_grant_monthly,
          device_limit,
          billing_cycle,
          active
        FROM billing_plans
        WHERE active = 1
        ORDER BY monthly_price_usd ASC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      code: String(row.code),
      displayName: String(row.display_name),
      monthlyPriceUsd: Number(row.monthly_price_usd),
      tokenGrantMonthly: Number(row.token_grant_monthly),
      deviceLimit: Number(row.device_limit),
      billingCycle: String(row.billing_cycle),
      active: Number(row.active) === 1,
    }));
  }

  async getWalletLedger(accountId: string): Promise<WalletLedgerRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          entry_type,
          amount,
          currency,
          source,
          reference_id,
          note,
          created_at
        FROM wallet_ledger
        WHERE account_id = ?
        ORDER BY created_at DESC
        LIMIT 100
        `,
      )
      .all(accountId) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      entryType: row.entry_type as WalletLedgerRecord['entryType'],
      amount: Number(row.amount),
      currency: row.currency as WalletLedgerRecord['currency'],
      source: String(row.source),
      referenceId: (row.reference_id as string | null) ?? undefined,
      note: (row.note as string | null) ?? undefined,
      createdAt: String(row.created_at),
    }));
  }

  async listAllWalletLedger(): Promise<WalletLedgerRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          entry_type,
          amount,
          currency,
          source,
          reference_id,
          note,
          created_at
        FROM wallet_ledger
        ORDER BY created_at DESC
        LIMIT 300
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      entryType: row.entry_type as WalletLedgerRecord['entryType'],
      amount: Number(row.amount),
      currency: row.currency as WalletLedgerRecord['currency'],
      source: String(row.source),
      referenceId: (row.reference_id as string | null) ?? undefined,
      note: (row.note as string | null) ?? undefined,
      createdAt: String(row.created_at),
    }));
  }

  async appendWalletLedger(entry: WalletLedgerRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO wallet_ledger (
          id,
          account_id,
          entry_type,
          amount,
          currency,
          source,
          reference_id,
          note,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        entry.id,
        entry.accountId,
        entry.entryType,
        entry.amount,
        entry.currency,
        entry.source,
        entry.referenceId ?? null,
        entry.note ?? null,
        entry.createdAt,
      );
  }

  async getStablecoinOrders(accountId: string): Promise<StablecoinPaymentOrderRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        FROM stablecoin_payment_orders
        WHERE account_id = ?
        ORDER BY created_at DESC
        LIMIT 100
        `,
      )
      .all(accountId) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      paymentMethod: 'stablecoin',
      stablecoinSymbol: String(row.stablecoin_symbol),
      chain: String(row.chain),
      walletAddress: String(row.wallet_address),
      amountUsd: Number(row.amount_usd),
      amountToken: Number(row.amount_token),
      status: row.status as StablecoinPaymentOrderRecord['status'],
      txHash: (row.tx_hash as string | null) ?? undefined,
      confirmations: Number(row.confirmations),
      reviewNote: (row.review_note as string | null) ?? undefined,
      expiresAt: (row.expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async getStablecoinOrderById(
    accountId: string,
    orderId: string,
  ): Promise<StablecoinPaymentOrderRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        FROM stablecoin_payment_orders
        WHERE account_id = ? AND id = ?
        LIMIT 1
        `,
      )
      .get(accountId, orderId) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      accountId: String(row.account_id),
      paymentMethod: 'stablecoin',
      stablecoinSymbol: String(row.stablecoin_symbol),
      chain: String(row.chain),
      walletAddress: String(row.wallet_address),
      amountUsd: Number(row.amount_usd),
      amountToken: Number(row.amount_token),
      status: row.status as StablecoinPaymentOrderRecord['status'],
      txHash: (row.tx_hash as string | null) ?? undefined,
      confirmations: Number(row.confirmations),
      reviewNote: (row.review_note as string | null) ?? undefined,
      expiresAt: (row.expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  async appendStablecoinOrder(order: StablecoinPaymentOrderRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO stablecoin_payment_orders (
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        order.id,
        order.accountId,
        order.paymentMethod,
        order.stablecoinSymbol,
        order.chain,
        order.walletAddress,
        order.amountUsd,
        order.amountToken,
        order.status,
        order.txHash ?? null,
        order.confirmations,
        order.reviewNote ?? null,
        order.expiresAt ?? null,
        order.createdAt,
        order.updatedAt,
      );
  }

  async getDeviceShareBindings(ownerAccountId: string): Promise<DeviceShareBindingRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          owner_account_id,
          shared_account_id,
          device_uuid,
          binding_scope,
          status,
          created_at
        FROM device_share_bindings
        WHERE owner_account_id = ?
        ORDER BY created_at DESC
        `,
      )
      .all(ownerAccountId) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      ownerAccountId: String(row.owner_account_id),
      sharedAccountId: String(row.shared_account_id),
      deviceUuid: String(row.device_uuid),
      bindingScope: row.binding_scope as DeviceShareBindingRecord['bindingScope'],
      status: row.status as DeviceShareBindingRecord['status'],
      createdAt: String(row.created_at),
    }));
  }

  async appendDeviceShareBinding(binding: DeviceShareBindingRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO device_share_bindings (
          id,
          owner_account_id,
          shared_account_id,
          device_uuid,
          binding_scope,
          status,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        binding.id,
        binding.ownerAccountId,
        binding.sharedAccountId,
        binding.deviceUuid,
        binding.bindingScope,
        binding.status,
        binding.createdAt,
      );
  }

  async getDeviceTokenUsageEvents(
    accountId: string,
    deviceUuid?: string,
  ): Promise<DeviceTokenUsageEventRecord[]> {
    const rows = (
      deviceUuid == null
        ? this.db
            .prepare(
              `
              SELECT
                id,
                account_id,
                device_uuid,
                amount_consumed,
                trigger_source,
                bootstrap_token_remaining,
                created_at
              FROM device_token_usage_events
              WHERE account_id = ?
              ORDER BY created_at DESC
              LIMIT 100
              `,
            )
            .all(accountId)
        : this.db
            .prepare(
              `
              SELECT
                id,
                account_id,
                device_uuid,
                amount_consumed,
                trigger_source,
                bootstrap_token_remaining,
                created_at
              FROM device_token_usage_events
              WHERE account_id = ? AND device_uuid = ?
              ORDER BY created_at DESC
              LIMIT 100
              `,
            )
            .all(accountId, deviceUuid)
    ) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      deviceUuid: String(row.device_uuid),
      amountConsumed: Number(row.amount_consumed),
      triggerSource: String(row.trigger_source),
      bootstrapTokenRemaining: Number(row.bootstrap_token_remaining),
      createdAt: String(row.created_at),
    }));
  }

  async appendDeviceTokenUsageEvent(event: DeviceTokenUsageEventRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO device_token_usage_events (
          id,
          account_id,
          device_uuid,
          amount_consumed,
          trigger_source,
          bootstrap_token_remaining,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        event.id,
        event.accountId,
        event.deviceUuid,
        event.amountConsumed,
        event.triggerSource,
        event.bootstrapTokenRemaining,
        event.createdAt,
      );
  }

  async getAvatarProfiles(): Promise<AvatarProfileRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          name,
          avatar_label,
          gender,
          age_group,
          primary_color_hex,
          secondary_color_hex,
          accent_color_hex,
          asset_url,
          active,
          created_at,
          updated_at
        FROM avatar_profiles
        ORDER BY active DESC, updated_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      name: String(row.name),
      avatarLabel: String(row.avatar_label),
      gender: row.gender as AvatarProfileRecord['gender'],
      ageGroup: row.age_group as AvatarProfileRecord['ageGroup'],
      primaryColorHex: String(row.primary_color_hex),
      secondaryColorHex: String(row.secondary_color_hex),
      accentColorHex: String(row.accent_color_hex),
      assetUrl: (row.asset_url as string | null) ?? undefined,
      active: Number(row.active) === 1,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async getActiveAvatarProfile(): Promise<AvatarProfileRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT
          id,
          name,
          avatar_label,
          gender,
          age_group,
          primary_color_hex,
          secondary_color_hex,
          accent_color_hex,
          asset_url,
          active,
          created_at,
          updated_at
        FROM avatar_profiles
        WHERE active = 1
        ORDER BY updated_at DESC
        LIMIT 1
        `,
      )
      .get() as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      name: String(row.name),
      avatarLabel: String(row.avatar_label),
      gender: row.gender as AvatarProfileRecord['gender'],
      ageGroup: row.age_group as AvatarProfileRecord['ageGroup'],
      primaryColorHex: String(row.primary_color_hex),
      secondaryColorHex: String(row.secondary_color_hex),
      accentColorHex: String(row.accent_color_hex),
      assetUrl: (row.asset_url as string | null) ?? undefined,
      active: Number(row.active) === 1,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  async appendAvatarProfile(profile: AvatarProfileRecord): Promise<void> {
    if (profile.active) {
      this.db.prepare('UPDATE avatar_profiles SET active = 0').run();
    }

    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO avatar_profiles (
          id,
          name,
          avatar_label,
          gender,
          age_group,
          primary_color_hex,
          secondary_color_hex,
          accent_color_hex,
          asset_url,
          active,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        profile.id,
        profile.name,
        profile.avatarLabel,
        profile.gender,
        profile.ageGroup,
        profile.primaryColorHex,
        profile.secondaryColorHex,
        profile.accentColorHex,
        profile.assetUrl ?? null,
        profile.active ? 1 : 0,
        profile.createdAt,
        profile.updatedAt,
      );
  }

  async activateAvatarProfile(id: string): Promise<void> {
    this.runInTransaction(() => {
      this.db.prepare('UPDATE avatar_profiles SET active = 0').run();
      this.db
        .prepare(
          `
          UPDATE avatar_profiles
          SET active = 1, updated_at = ?
          WHERE id = ?
          `,
        )
        .run(new Date().toISOString(), id);
    });
  }

  async getSchemaInfo(): Promise<{ version: number }> {
    return { version: this.readSchemaVersion() };
  }

  private initialize(): void {
    this.db.exec('PRAGMA journal_mode = WAL;');
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS schema_meta (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);

    const currentVersion = this.readSchemaVersion();
    for (let version = currentVersion + 1; version <= this.schemaVersion; version += 1) {
      this.applyMigration(version);
      this.writeSchemaVersion(version);
    }

    this.normalizeLegacyRadioSeedData();
  }

  private applyMigration(version: number): void {
    switch (version) {
      case 1:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            device_uuid TEXT NOT NULL UNIQUE,
            device_name TEXT NOT NULL,
            android_version TEXT NOT NULL,
            is_android_tv INTEGER NOT NULL,
            status TEXT NOT NULL,
            binding_status TEXT NOT NULL,
            bootstrap_token_grant INTEGER NOT NULL,
            bootstrap_token_remaining INTEGER NOT NULL
          );

          CREATE TABLE IF NOT EXISTS assistant_logs (
            id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            device_id TEXT,
            locale TEXT NOT NULL,
            user_text TEXT NOT NULL,
            assistant_text TEXT NOT NULL,
            app_id TEXT,
            action TEXT,
            query_text TEXT,
            mode TEXT NOT NULL,
            model_provider TEXT NOT NULL,
            route TEXT,
            token_usage INTEGER NOT NULL,
            bootstrap_token_remaining INTEGER NOT NULL,
            created_at TEXT NOT NULL
          );
        `);
        break;
      case 2:
        this.db.exec(`
          CREATE INDEX IF NOT EXISTS idx_assistant_logs_created_at
          ON assistant_logs(created_at DESC);
        `);
        this.importLegacyJsonIfPresent();
        break;
      case 3:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS billing_plans (
            id TEXT PRIMARY KEY,
            code TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            monthly_price_usd REAL NOT NULL,
            token_grant_monthly INTEGER NOT NULL,
            device_limit INTEGER NOT NULL,
            billing_cycle TEXT NOT NULL,
            active INTEGER NOT NULL DEFAULT 1
          );

          CREATE TABLE IF NOT EXISTS wallet_ledger (
            id TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            entry_type TEXT NOT NULL,
            amount REAL NOT NULL,
            currency TEXT NOT NULL,
            source TEXT NOT NULL,
            reference_id TEXT,
            note TEXT,
            created_at TEXT NOT NULL
          );

          CREATE INDEX IF NOT EXISTS idx_wallet_ledger_account_created_at
          ON wallet_ledger(account_id, created_at DESC);
        `);
        this.seedBillingPlans();
        this.seedWalletLedger();
        break;
      case 4:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS stablecoin_payment_orders (
            id TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            payment_method TEXT NOT NULL,
            stablecoin_symbol TEXT NOT NULL,
            chain TEXT NOT NULL,
            wallet_address TEXT NOT NULL,
            amount_usd REAL NOT NULL,
            amount_token REAL NOT NULL,
            status TEXT NOT NULL,
            tx_hash TEXT,
            confirmations INTEGER NOT NULL DEFAULT 0,
            review_note TEXT,
            expires_at TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS device_share_bindings (
            id TEXT PRIMARY KEY,
            owner_account_id TEXT NOT NULL,
            shared_account_id TEXT NOT NULL,
            device_uuid TEXT NOT NULL,
            binding_scope TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at TEXT NOT NULL
          );
        `);
        this.seedStablecoinOrders();
        this.seedDeviceShareBindings();
        break;
      case 5:
        this.ensureColumn(
          'stablecoin_payment_orders',
          'review_note',
          'TEXT',
        );
        this.ensureColumn(
          'stablecoin_payment_orders',
          'expires_at',
          'TEXT',
        );
        this.ensureColumn(
          'stablecoin_payment_orders',
          'updated_at',
          'TEXT',
        );
        this.db.exec(`
          UPDATE stablecoin_payment_orders
          SET
            review_note = COALESCE(review_note, NULL),
            expires_at = COALESCE(expires_at, datetime(created_at, '+30 minutes')),
            updated_at = COALESCE(updated_at, created_at)
        `);
        break;
      case 6:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS device_token_usage_events (
            id TEXT PRIMARY KEY,
            device_uuid TEXT NOT NULL,
            amount_consumed INTEGER NOT NULL,
            trigger_source TEXT NOT NULL,
            bootstrap_token_remaining INTEGER NOT NULL,
            created_at TEXT NOT NULL
          );

          CREATE INDEX IF NOT EXISTS idx_device_token_usage_device_created_at
          ON device_token_usage_events(device_uuid, created_at DESC);
        `);
        break;
      case 7:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS avatar_profiles (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            avatar_label TEXT NOT NULL,
            gender TEXT NOT NULL,
            age_group TEXT NOT NULL,
            primary_color_hex TEXT NOT NULL,
            secondary_color_hex TEXT NOT NULL,
            accent_color_hex TEXT NOT NULL,
            asset_url TEXT,
            active INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
          );
        `);
        this.seedAvatarProfiles();
        break;
      case 8:
        this.ensureColumn('devices', 'account_id', "TEXT NOT NULL DEFAULT 'user_demo'");
        this.ensureColumn(
          'assistant_logs',
          'account_id',
          "TEXT NOT NULL DEFAULT 'user_demo'",
        );
        this.ensureColumn(
          'device_token_usage_events',
          'account_id',
          "TEXT NOT NULL DEFAULT 'user_demo'",
        );
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS tester_accounts (
            id TEXT PRIMARY KEY,
            email TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            plan_code TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS tester_invite_codes (
            code TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            label TEXT NOT NULL,
            active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS auth_sessions (
            token TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            created_at TEXT NOT NULL,
            last_seen_at TEXT NOT NULL
          );
        `);
        this.db.exec(`
          UPDATE devices SET account_id = COALESCE(account_id, 'user_demo');
          UPDATE assistant_logs SET account_id = COALESCE(account_id, 'user_demo');
          UPDATE device_token_usage_events SET account_id = COALESCE(account_id, 'user_demo');
        `);
        this.seedTesterAccounts();
        break;
      case 9:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS device_user_profiles (
            id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            plan_code TEXT NOT NULL,
            status TEXT NOT NULL,
            recovery_hint TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS device_entitlement_transfers (
            id TEXT PRIMARY KEY,
            from_account_id TEXT NOT NULL,
            to_account_id TEXT NOT NULL,
            transfer_reason TEXT NOT NULL,
            payment_proof_tx_hash TEXT,
            created_at TEXT NOT NULL
          );
        `);
        this.ensureColumn('device_user_profiles', 'updated_at', 'TEXT');
        this.ensureColumn('device_user_profiles', 'recovery_hint', 'TEXT');
        this.ensureColumn(
          'device_user_profiles',
          'status',
          "TEXT NOT NULL DEFAULT 'active'",
        );
        this.ensureColumn(
          'device_user_profiles',
          'plan_code',
          "TEXT NOT NULL DEFAULT 'basic'",
        );
        this.ensureColumn(
          'device_user_profiles',
          'entitlement_expires_at',
          'TEXT',
        );
        this.seedDeviceUserProfiles();
        break;
      case 10:
        this.ensureColumn('device_user_profiles', 'updated_at', 'TEXT');
        this.ensureColumn('device_user_profiles', 'recovery_hint', 'TEXT');
        this.ensureColumn('device_user_profiles', 'status', "TEXT NOT NULL DEFAULT 'active'");
        this.ensureColumn(
          'device_user_profiles',
          'plan_code',
          "TEXT NOT NULL DEFAULT 'basic'",
        );
        this.ensureColumn(
          'device_user_profiles',
          'entitlement_expires_at',
          'TEXT',
        );
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS api_pool_accounts (
            id TEXT PRIMARY KEY,
            provider TEXT NOT NULL,
            account_label TEXT NOT NULL,
            plan_label TEXT NOT NULL,
            status TEXT NOT NULL,
            renews_at TEXT,
            expires_at TEXT,
            notes TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
          );
        `);
        this.seedApiPoolAccounts();
        break;
      case 11:
        this.db.exec(`
          CREATE TABLE IF NOT EXISTS ota_releases (
            id TEXT PRIMARY KEY,
            version_name TEXT NOT NULL,
            version_code INTEGER NOT NULL,
            release_channel TEXT NOT NULL,
            rollout_status TEXT NOT NULL,
            target_scope TEXT NOT NULL,
            rollout_percent INTEGER NOT NULL,
            device_count INTEGER NOT NULL,
            install_success_rate REAL NOT NULL,
            notification_mode TEXT,
            download_policy TEXT,
            install_policy TEXT,
            report_policy TEXT,
            report_delay_minutes INTEGER,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
          );
        `);
        this.seedOtaReleases();
        break;
        case 12:
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS admin_allowed_emails (
              email TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS admin_login_codes (
            id TEXT PRIMARY KEY,
            email TEXT NOT NULL,
            code TEXT NOT NULL,
            expires_at TEXT NOT NULL,
            used_at TEXT,
            created_at TEXT NOT NULL
          );

          CREATE TABLE IF NOT EXISTS admin_sessions (
            token TEXT PRIMARY KEY,
            email TEXT NOT NULL,
            created_at TEXT NOT NULL,
            last_seen_at TEXT NOT NULL
            );
          `);
          this.seedAdminAllowedEmails();
          break;
        case 13:
          this.db.exec(`
            ALTER TABLE admin_sessions ADD COLUMN expires_at TEXT;
          `);
          break;
        case 14:
          this.ensureColumn('ota_releases', 'notification_mode', 'TEXT');
          this.ensureColumn('ota_releases', 'download_policy', 'TEXT');
          this.ensureColumn('ota_releases', 'install_policy', 'TEXT');
          this.ensureColumn('ota_releases', 'report_policy', 'TEXT');
          this.ensureColumn('ota_releases', 'report_delay_minutes', 'INTEGER');
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS ota_device_reports (
              id TEXT PRIMARY KEY,
              release_id TEXT NOT NULL,
              account_id TEXT NOT NULL,
              device_uuid TEXT NOT NULL,
              current_version_code INTEGER NOT NULL,
              target_version_code INTEGER NOT NULL,
              status TEXT NOT NULL,
              progress_percent INTEGER NOT NULL,
              note TEXT,
              reported_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );
          `);
          break;
        case 15:
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS api_pool_credentials (
              id TEXT PRIMARY KEY,
              account_id TEXT NOT NULL,
              provider TEXT NOT NULL,
              label TEXT NOT NULL,
              base_url TEXT NOT NULL,
              model TEXT NOT NULL,
              api_key TEXT NOT NULL,
              status TEXT NOT NULL,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS api_pool_leases (
              id TEXT PRIMARY KEY,
              account_id TEXT NOT NULL,
              credential_id TEXT NOT NULL,
              device_user_id TEXT NOT NULL,
              device_uuid TEXT NOT NULL,
              provider TEXT NOT NULL,
              base_url TEXT NOT NULL,
              model TEXT NOT NULL,
              status TEXT NOT NULL,
              leased_at TEXT NOT NULL,
              expires_at TEXT NOT NULL,
              released_at TEXT
            );
          `);
          break;
      case 16:
          this.db.exec(`
            ALTER TABLE assistant_logs ADD COLUMN transport_mode TEXT;
          `);
          break;
        case 17:
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS tv_home_configs (
              id TEXT PRIMARY KEY,
              country_code TEXT NOT NULL,
              region_code TEXT,
              background_image_url TEXT,
              featured_app_ids_json TEXT NOT NULL,
              status TEXT NOT NULL,
              version INTEGER NOT NULL,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );

            CREATE UNIQUE INDEX IF NOT EXISTS idx_tv_home_configs_scope
            ON tv_home_configs(country_code, COALESCE(region_code, ''));
          `);
          this.seedTvHomeConfigs();
          break;
        case 18:
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS radio_stations (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              country TEXT NOT NULL,
              region TEXT,
              city TEXT NOT NULL,
              language TEXT NOT NULL,
              band_label TEXT NOT NULL,
              genre TEXT NOT NULL,
              stream_url TEXT NOT NULL,
              homepage_url TEXT,
              logo_url TEXT,
              legal_notes TEXT,
              is_active INTEGER NOT NULL DEFAULT 1,
              sort_order INTEGER NOT NULL DEFAULT 0,
              last_checked_at TEXT NOT NULL,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS radio_broadcasts (
              id TEXT PRIMARY KEY,
              station_id TEXT,
              account_id TEXT NOT NULL,
              title TEXT NOT NULL,
              source_kind TEXT NOT NULL,
              text_transcript TEXT,
              audio_path TEXT,
              duration_ms INTEGER NOT NULL DEFAULT 0,
              status TEXT NOT NULL,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_radio_stations_sort
            ON radio_stations(sort_order ASC, name ASC);

            CREATE INDEX IF NOT EXISTS idx_radio_broadcasts_created_at
            ON radio_broadcasts(created_at DESC);
          `);
          this.seedRadioStations();
          this.seedRadioBroadcasts();
          break;
        case 19:
          this.normalizeLegacyRadioSeedData();
          break;
        case 20:
          this.ensureColumn('radio_stations', 'last_health_status', "TEXT NOT NULL DEFAULT 'unknown'");
          this.ensureColumn(
            'radio_stations',
            'consecutive_failures',
            'INTEGER NOT NULL DEFAULT 0',
          );
          this.ensureColumn('radio_stations', 'last_health_error', 'TEXT');
          this.db.exec(`
            UPDATE radio_stations
            SET
              last_health_status = COALESCE(last_health_status, 'unknown'),
              consecutive_failures = COALESCE(consecutive_failures, 0)
          `);
          break;
        case 21:
          this.ensureColumn('radio_broadcasts', 'target_scope', 'TEXT');
          this.ensureColumn('ota_releases', 'artifact_url', 'TEXT');
          this.ensureColumn('ota_releases', 'release_notes', 'TEXT');
          this.db.exec(`
            CREATE TABLE IF NOT EXISTS client_config_releases (
              id TEXT PRIMARY KEY,
              version_name TEXT NOT NULL,
              version_code INTEGER NOT NULL,
              target_scope TEXT NOT NULL,
              config_key TEXT NOT NULL,
              payload_json TEXT NOT NULL,
              rollout_status TEXT NOT NULL,
              notification_mode TEXT NOT NULL,
              fetch_policy TEXT NOT NULL,
              apply_policy TEXT NOT NULL,
              release_notes TEXT,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_client_config_releases_created_at
            ON client_config_releases(created_at DESC);
          `);
          this.seedClientConfigReleases();
          break;
        default:
          throw new Error(`Unsupported schema migration version: ${version}`);
      }
    }

  private seedBillingPlans(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM billing_plans')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const insert = this.db.prepare(`
      INSERT INTO billing_plans (
        id,
        code,
        display_name,
        monthly_price_usd,
        token_grant_monthly,
        device_limit,
        billing_cycle,
        active
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const seedRows: BillingPlanRecord[] = [
      {
        id: 'plan_basic',
        code: 'basic',
        displayName: 'Basic Plan',
        monthlyPriceUsd: 4.99,
        tokenGrantMonthly: 50000,
        deviceLimit: 1,
        billingCycle: 'monthly',
        active: true,
      },
      {
        id: 'plan_family',
        code: 'family',
        displayName: 'Family Plan',
        monthlyPriceUsd: 12.99,
        tokenGrantMonthly: 180000,
        deviceLimit: 3,
        billingCycle: 'monthly',
        active: true,
      },
      {
        id: 'plan_premium',
        code: 'premium',
        displayName: 'Premium Plan',
        monthlyPriceUsd: 24.99,
        tokenGrantMonthly: 500000,
        deviceLimit: 8,
        billingCycle: 'monthly',
        active: true,
      },
    ];

    this.runInTransaction(() => {
      for (const plan of seedRows) {
        insert.run(
          plan.id,
          plan.code,
          plan.displayName,
          plan.monthlyPriceUsd,
          plan.tokenGrantMonthly,
          plan.deviceLimit,
          plan.billingCycle,
          plan.active ? 1 : 0,
        );
      }
    });
  }

  private seedWalletLedger(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM wallet_ledger')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    const seedEntries: WalletLedgerRecord[] = [
      {
        id: 'ledger_seed_subscription',
        accountId: 'user_demo',
        entryType: 'grant',
        amount: 180000,
        currency: 'token',
        source: 'subscription_grant',
        referenceId: 'plan_family',
        note: 'Monthly family plan token grant',
        createdAt: now,
      },
      {
        id: 'ledger_seed_topup',
        accountId: 'user_demo',
        entryType: 'topup',
        amount: 128000,
        currency: 'token',
        source: 'manual_topup',
        referenceId: 'demo_topup_001',
        note: 'Demo top-up balance',
        createdAt: now,
      },
    ];

    const insert = this.db.prepare(`
      INSERT INTO wallet_ledger (
        id,
        account_id,
        entry_type,
        amount,
        currency,
        source,
        reference_id,
        note,
        created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    this.runInTransaction(() => {
      for (const entry of seedEntries) {
        insert.run(
          entry.id,
          entry.accountId,
          entry.entryType,
          entry.amount,
          entry.currency,
          entry.source,
          entry.referenceId ?? null,
          entry.note ?? null,
          entry.createdAt,
        );
      }
    });
  }

  private seedStablecoinOrders(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM stablecoin_payment_orders')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    this.db
      .prepare(
        `
        INSERT INTO stablecoin_payment_orders (
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        'order_demo_usdc_001',
        'user_demo',
        'stablecoin',
        'USDC',
        'Polygon',
        '0xDEMO1234STABLECOINWALLET',
        25,
        25,
        'confirming',
        '0xstablecoindemotxhash001',
        8,
        null,
        new Date(Date.now() + 30 * 60 * 1000).toISOString(),
        new Date().toISOString(),
        new Date().toISOString(),
      );
  }

  private seedDeviceShareBindings(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM device_share_bindings')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    this.db
      .prepare(
        `
        INSERT INTO device_share_bindings (
          id,
          owner_account_id,
          shared_account_id,
          device_uuid,
          binding_scope,
          status,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        'share_demo_001',
        'user_demo',
        'family_member_demo',
        'device_demo_android_tv',
        'household',
        'active',
        new Date().toISOString(),
      );
  }

  private seedAvatarProfiles(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM avatar_profiles')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    const insert = this.db.prepare(
      `
      INSERT INTO avatar_profiles (
        id,
        name,
        avatar_label,
        gender,
        age_group,
        primary_color_hex,
        secondary_color_hex,
        accent_color_hex,
        asset_url,
        active,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `,
    );

    const seedProfiles: AvatarProfileRecord[] = [
      {
        id: 'avatar_warm_female',
        name: 'Warm Female',
        avatarLabel: 'Warm Female',
        gender: 'female',
        ageGroup: 'adult',
        primaryColorHex: '#6AE6D8',
        secondaryColorHex: '#12656A',
        accentColorHex: '#B0FFF4',
        assetUrl: undefined,
        active: true,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'avatar_calm_male',
        name: 'Calm Male',
        avatarLabel: 'Calm Male',
        gender: 'male',
        ageGroup: 'adult',
        primaryColorHex: '#7CC6FE',
        secondaryColorHex: '#1C4F8A',
        accentColorHex: '#D8EEFF',
        assetUrl: undefined,
        active: false,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'avatar_bright_child',
        name: 'Bright Child',
        avatarLabel: 'Bright Child',
        gender: 'child',
        ageGroup: 'youth',
        primaryColorHex: '#FFD166',
        secondaryColorHex: '#725317',
        accentColorHex: '#FFF0B5',
        assetUrl: undefined,
        active: false,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'avatar_gentle_senior',
        name: 'Gentle Senior',
        avatarLabel: 'Gentle Senior',
        gender: 'senior',
        ageGroup: 'senior',
        primaryColorHex: '#D8B4FE',
        secondaryColorHex: '#5B3A74',
        accentColorHex: '#F2E2FF',
        assetUrl: undefined,
        active: false,
        createdAt: now,
        updatedAt: now,
      },
    ];

    this.runInTransaction(() => {
      for (const profile of seedProfiles) {
        insert.run(
          profile.id,
          profile.name,
          profile.avatarLabel,
          profile.gender,
          profile.ageGroup,
          profile.primaryColorHex,
          profile.secondaryColorHex,
          profile.accentColorHex,
          profile.assetUrl ?? null,
          profile.active ? 1 : 0,
          profile.createdAt,
          profile.updatedAt,
        );
      }
    });
  }

  private seedTesterAccounts(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM tester_accounts')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    this.runInTransaction(() => {
      this.db
        .prepare(
          `
          INSERT INTO tester_accounts (
            id, email, display_name, plan_code, status, created_at
          ) VALUES (?, ?, ?, ?, ?, ?)
          `,
        )
        .run('user_demo', 'demo@sonance.local', 'Sonance Demo User', 'family', 'active', now);
      this.db
        .prepare(
          `
          INSERT INTO tester_accounts (
            id, email, display_name, plan_code, status, created_at
          ) VALUES (?, ?, ?, ?, ?, ?)
          `,
        )
        .run('user_beta_a', 'beta-a@sonance.local', 'Sonance Beta A', 'family', 'active', now);
      this.db
        .prepare(
          `
          INSERT INTO tester_invite_codes (
            code, account_id, label, active, created_at
          ) VALUES (?, ?, ?, ?, ?)
          `,
        )
        .run('SONANCE-DEMO', 'user_demo', 'Demo tester', 1, now);
      this.db
        .prepare(
          `
          INSERT INTO tester_invite_codes (
            code, account_id, label, active, created_at
          ) VALUES (?, ?, ?, ?, ?)
          `,
        )
        .run('SONANCE-BETA', 'user_beta_a', 'Beta tester', 1, now);
    });
  }

  private seedDeviceUserProfiles(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM device_user_profiles')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    this.runInTransaction(() => {
      this.db
        .prepare(
          `
          INSERT INTO device_user_profiles (
            id, display_name, plan_code, status, recovery_hint, entitlement_expires_at, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `,
        )
        .run(
          'user_demo',
          'Sonance Demo Device User',
          'family',
          'active',
          'latest payment on Polygon/USDC',
          new Date(Date.now() + 21 * 24 * 60 * 60 * 1000).toISOString(),
          now,
          now,
        );
      this.db
        .prepare(
          `
          INSERT INTO device_user_profiles (
            id, display_name, plan_code, status, recovery_hint, entitlement_expires_at, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `,
        )
        .run(
          'user_beta_a',
          'Sonance Beta User',
          'family',
          'active',
          'latest payment on Base/USDC',
          new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
          now,
          now,
        );
    });
  }

  private seedApiPoolAccounts(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM api_pool_accounts')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    this.runInTransaction(() => {
      this.db
        .prepare(
          `
          INSERT INTO api_pool_accounts (
            id, provider, account_label, plan_label, status, renews_at, expires_at, notes, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `,
        )
        .run(
          'pool_minimax_001',
          'MiniMax',
          'minimax-main-subscription',
          'Monthly low-cost pool',
          'active',
          new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
          new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
          'Primary closed beta pool account',
          now,
          now,
        );
      this.db
        .prepare(
          `
          INSERT INTO api_pool_accounts (
            id, provider, account_label, plan_label, status, renews_at, expires_at, notes, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `,
        )
        .run(
          'pool_github_models_001',
          'GitHub Models',
          'github-models-fallback',
          'Trial / fallback',
          'expiring',
          new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
          new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString(),
          'Manual fallback account for overflow traffic',
          now,
          now,
        );
    });
  }

  private importLegacyJsonIfPresent(): void {
    if (!existsSync(this.legacyJsonPath)) {
      return;
    }

    const deviceCount = Number(
      (this.db.prepare('SELECT COUNT(*) as count FROM devices').get() as Record<string, unknown>)
        .count ?? 0,
    );
    const logCount = Number(
      (
        this.db.prepare('SELECT COUNT(*) as count FROM assistant_logs').get() as Record<
          string,
          unknown
        >
      ).count ?? 0,
    );

    if (deviceCount > 0 || logCount > 0) {
      return;
    }

    const raw = readFileSync(this.legacyJsonPath, 'utf8');
    const parsed = JSON.parse(raw) as LegacyJsonState;

    if (parsed.devices?.length) {
      const insertDevice = this.db.prepare(`
        INSERT OR REPLACE INTO devices (
          id,
          device_uuid,
          device_name,
          android_version,
          is_android_tv,
          status,
          binding_status,
          bootstrap_token_grant,
          bootstrap_token_remaining
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

      this.runInTransaction(() => {
        for (const device of parsed.devices!) {
          insertDevice.run(
            device.id,
            device.deviceUuid,
            device.deviceName,
            device.androidVersion,
            device.isAndroidTv ? 1 : 0,
            device.status,
            device.bindingStatus,
            device.bootstrapTokenGrant,
            device.bootstrapTokenRemaining,
          );
        }
      });
    }

    if (parsed.logs?.length) {
      const insertLog = this.db.prepare(`
        INSERT OR REPLACE INTO assistant_logs (
          id,
          kind,
          device_id,
          locale,
          user_text,
          assistant_text,
          app_id,
          action,
          query_text,
          mode,
          model_provider,
          route,
          token_usage,
          bootstrap_token_remaining,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

      this.runInTransaction(() => {
        for (const log of parsed.logs!) {
          insertLog.run(
            log.id,
            log.kind,
            log.deviceId ?? null,
            log.locale,
            log.userText,
            log.assistantText,
            log.appId ?? null,
            log.action ?? null,
            log.queryText ?? null,
            log.mode,
            log.modelProvider,
            log.route ?? null,
            log.tokenUsage,
            log.bootstrapTokenRemaining,
            log.createdAt,
          );
        }
      });
    }
  }

  private readSchemaVersion(): number {
    const row = this.db
      .prepare(`SELECT value FROM schema_meta WHERE key = 'schema_version'`)
      .get() as Record<string, unknown> | undefined;
    return Number(row?.value ?? 0);
  }

  private writeSchemaVersion(version: number): void {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO schema_meta (key, value)
        VALUES ('schema_version', ?)
        `,
      )
      .run(String(version));
  }

  private runInTransaction(work: () => void): void {
    this.db.exec('BEGIN');
    try {
      work();
      this.db.exec('COMMIT');
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }

  private ensureColumn(tableName: string, columnName: string, definition: string): void {
    const rows = this.db
      .prepare(`PRAGMA table_info(${tableName})`)
      .all() as Array<Record<string, unknown>>;
    const exists = rows.some((row) => String(row.name) === columnName);
    if (!exists) {
      this.db.exec(
        `ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definition};`,
      );
    }
  }

  private mapDeviceRow(row: Record<string, unknown>): RegisteredDeviceRecord {
    return {
      id: String(row.id),
      accountId: String(row.account_id),
      deviceUuid: String(row.device_uuid),
      deviceName: String(row.device_name),
      androidVersion: String(row.android_version),
      isAndroidTv: Number(row.is_android_tv) === 1,
      status: String(row.status),
      bindingStatus: String(row.binding_status),
      bootstrapTokenGrant: Number(row.bootstrap_token_grant),
      bootstrapTokenRemaining: Number(row.bootstrap_token_remaining),
    };
  }

  private mapLogRow(row: Record<string, unknown>): AssistantLogRecord {
    return {
      id: String(row.id),
      accountId: String(row.account_id),
      kind: row.kind as AssistantLogRecord['kind'],
      deviceId: (row.device_id as string | null) ?? undefined,
      locale: String(row.locale),
      userText: String(row.user_text),
      assistantText: String(row.assistant_text),
      appId: (row.app_id as string | null) ?? undefined,
      action: (row.action as string | null) ?? undefined,
      queryText: (row.query_text as string | null) ?? undefined,
      mode: row.mode as AssistantLogRecord['mode'],
        modelProvider: String(row.model_provider),
        route: (row.route as string | null) ?? undefined,
        transportMode: (row.transport_mode as string | null) ?? undefined,
        tokenUsage: Number(row.token_usage),
        bootstrapTokenRemaining: Number(row.bootstrap_token_remaining),
        createdAt: String(row.created_at),
    };
  }

  async getTesterAccountByInviteCode(
    code: string,
  ): Promise<TesterAccountRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT
          a.id,
          a.email,
          a.display_name,
          a.plan_code,
          a.status,
          a.created_at
        FROM tester_invite_codes i
        JOIN tester_accounts a
          ON a.id = i.account_id
        WHERE i.code = ? AND i.active = 1 AND a.status = 'active'
        LIMIT 1
        `,
      )
      .get(code) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      email: String(row.email),
      displayName: String(row.display_name),
      planCode: String(row.plan_code),
      status: row.status as TesterAccountRecord['status'],
      createdAt: String(row.created_at),
    };
  }

  async getTesterAccount(accountId: string): Promise<TesterAccountRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT id, email, display_name, plan_code, status, created_at
        FROM tester_accounts
        WHERE id = ?
        LIMIT 1
        `,
      )
      .get(accountId) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      email: String(row.email),
      displayName: String(row.display_name),
      planCode: String(row.plan_code),
      status: row.status as TesterAccountRecord['status'],
      createdAt: String(row.created_at),
    };
  }

  async upsertAuthSession(session: AuthSessionRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO auth_sessions (
          token,
          account_id,
          created_at,
          last_seen_at
        ) VALUES (?, ?, ?, ?)
        `,
      )
      .run(
        session.token,
        session.accountId,
        session.createdAt,
        session.lastSeenAt,
      );
  }

  private seedOtaReleases(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM ota_releases')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = Date.now();
      const seeded: OtaReleaseRecord[] = [
        {
          id: 'ota_release_001',
          versionName: '0.1.8-beta',
          versionCode: 18,
          releaseChannel: 'beta',
          rolloutStatus: 'rolling',
          targetScope: 'radio-app / internal / CN',
          rolloutPercent: 35,
          deviceCount: 48,
          installSuccessRate: 97.2,
          notificationMode: 'broadcast',
          downloadPolicy: 'idle_background',
          installPolicy: 'next_boot',
          reportPolicy: 'lazy',
          reportDelayMinutes: 45,
          artifactUrl: 'https://updates.sonance.app/radio-app/0.1.8-beta/package.zip',
          releaseNotes: 'Regional broadcast notice first, then idle background download.',
          createdAt: new Date(now - 1000 * 60 * 60 * 18).toISOString(),
          updatedAt: new Date(now - 1000 * 60 * 30).toISOString(),
        },
        {
          id: 'ota_release_000',
          versionName: '0.1.7-stable',
          versionCode: 17,
          releaseChannel: 'stable',
          rolloutStatus: 'completed',
          targetScope: 'All current stable devices',
          rolloutPercent: 100,
          deviceCount: 132,
          installSuccessRate: 98.9,
          notificationMode: 'broadcast',
          downloadPolicy: 'idle_background',
          installPolicy: 'next_boot',
          reportPolicy: 'lazy',
          reportDelayMinutes: 120,
          artifactUrl: 'https://updates.sonance.app/radio-app/0.1.7-stable/package.zip',
          releaseNotes: 'Stable baseline release for all current devices.',
          createdAt: new Date(now - 1000 * 60 * 60 * 24 * 6).toISOString(),
          updatedAt: new Date(now - 1000 * 60 * 60 * 24 * 5).toISOString(),
        },
        {
          id: 'ota_release_002',
          versionName: '0.1.9-internal',
          versionCode: 19,
          releaseChannel: 'internal',
          rolloutStatus: 'paused',
          targetScope: 'Internal dogfood only',
          rolloutPercent: 10,
          deviceCount: 8,
          installSuccessRate: 100,
          notificationMode: 'broadcast',
          downloadPolicy: 'idle_background',
          installPolicy: 'next_boot',
          reportPolicy: 'lazy',
          reportDelayMinutes: 30,
          artifactUrl: 'https://updates.sonance.app/radio-app/0.1.9-internal/package.zip',
          releaseNotes: 'Internal validation wave.',
          createdAt: new Date(now - 1000 * 60 * 60 * 5).toISOString(),
          updatedAt: new Date(now - 1000 * 60 * 60 * 2).toISOString(),
        },
      ];

    const statement = this.db.prepare(`
      INSERT INTO ota_releases (
        id,
        version_name,
        version_code,
        release_channel,
        rollout_status,
        target_scope,
          rollout_percent,
          device_count,
          install_success_rate,
          notification_mode,
          download_policy,
        install_policy,
        report_policy,
        report_delay_minutes,
        artifact_url,
        release_notes,
        created_at,
        updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

    this.runInTransaction(() => {
      for (const release of seeded) {
        statement.run(
          release.id,
          release.versionName,
          release.versionCode,
          release.releaseChannel,
          release.rolloutStatus,
          release.targetScope,
          release.rolloutPercent,
          release.deviceCount,
          release.installSuccessRate,
          release.notificationMode,
          release.downloadPolicy,
          release.installPolicy,
          release.reportPolicy,
          release.reportDelayMinutes,
          release.artifactUrl ?? null,
          release.releaseNotes ?? null,
          release.createdAt,
          release.updatedAt,
        );
      }
    });
  }

  private seedClientConfigReleases(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM client_config_releases')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = Date.now();
    const releases: ClientConfigReleaseRecord[] = [
      {
        id: 'client_config_release_cn_music',
        versionName: 'cn-music-boost-v1',
        versionCode: 1,
        targetScope: 'radio-app / CN / Guangdong / music',
        configKey: 'radio-client-shell',
        payloadJson: JSON.stringify({
          preferredMode: 'music',
          backgroundImageUrl:
            'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80',
        }),
        rolloutStatus: 'rolling',
        notificationMode: 'broadcast',
        fetchPolicy: 'idle_background',
        applyPolicy: 'idle_apply',
        releaseNotes: 'Idle-applied regional shell tuning for music-first listeners.',
        createdAt: new Date(now - 1000 * 60 * 45).toISOString(),
        updatedAt: new Date(now - 1000 * 60 * 10).toISOString(),
      },
    ];

    for (const release of releases) {
      void this.upsertClientConfigRelease(release);
    }
  }

  private seedAdminAllowedEmails(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM admin_allowed_emails')
      .get() as Record<string, unknown>;
    if (Number(countRow.count ?? 0) > 0) {
      return;
    }

    const now = new Date().toISOString();
    this.db
      .prepare(
        `
        INSERT INTO admin_allowed_emails (
          email,
          display_name,
          status,
          created_at
        ) VALUES (?, ?, ?, ?)
        `,
      )
      .run('soulzyn@outlook.com', 'Soulzyn', 'active', now);
  }

  async getAuthSession(token: string): Promise<AuthSessionRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT token, account_id, created_at, last_seen_at
        FROM auth_sessions
        WHERE token = ?
        LIMIT 1
        `,
      )
      .get(token) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      token: String(row.token),
      accountId: String(row.account_id),
      createdAt: String(row.created_at),
      lastSeenAt: String(row.last_seen_at),
    };
  }

  async getAdminAllowedEmail(
    email: string,
  ): Promise<AdminAllowedEmailRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT email, display_name, status, created_at
        FROM admin_allowed_emails
        WHERE email = ?
        LIMIT 1
        `,
      )
      .get(email.toLowerCase()) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      email: String(row.email),
      displayName: String(row.display_name),
      status: row.status as AdminAllowedEmailRecord['status'],
      createdAt: String(row.created_at),
    };
  }

  async upsertAdminLoginCode(code: AdminLoginCodeRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO admin_login_codes (
          id,
          email,
          code,
          expires_at,
          used_at,
          created_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        code.id,
        code.email.toLowerCase(),
        code.code,
        code.expiresAt,
        code.usedAt ?? null,
        code.createdAt,
      );
  }

  private seedTvHomeConfigs(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM tv_home_configs')
      .get() as Record<string, unknown>;

    if (Number(countRow.count) > 0) {
      return;
    }

    const now = new Date().toISOString();
    const configs: TvHomeConfigRecord[] = [
      {
        id: 'tv_home_global_default',
        countryCode: 'GLOBAL',
        regionCode: 'GLOBAL',
        backgroundImageUrl: undefined,
        featuredAppIds: [
          'youtube',
          'netflix',
          'prime_video',
          'disney_plus',
          'plex',
        ],
        status: 'active',
        version: 1,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'tv_home_us_default',
        countryCode: 'US',
        regionCode: undefined,
        backgroundImageUrl: undefined,
        featuredAppIds: [
          'youtube',
          'netflix',
          'prime_video',
          'disney_plus',
          'plex',
        ],
        status: 'active',
        version: 1,
        createdAt: now,
        updatedAt: now,
      },
    ];

    for (const config of configs) {
      void this.upsertTvHomeConfig(config);
    }
  }

  private getDefaultRadioStations(now: string): RadioStationRecord[] {
    const legalNotes =
      'Public directory-listed Chinese radio stream for MVP development.';

    return [
      {
        id: 'station-cnr-voice',
        name: '\u4e2d\u56fd\u4e4b\u58f0',
        country: 'CN',
        region: 'Beijing',
        city: '\u5317\u4eac',
        language: '\u4e2d\u6587',
        bandLabel: 'CNR 1',
        genre: 'News Talk',
        streamUrl: 'https://ngcdn001.cnr.cn/live/zgzs/index.m3u8',
        homepageUrl:
          'https://www.radio.cn/pc-portal/home/index.html?option=default%2Cradio',
        legalNotes,
        isActive: true,
        sortOrder: 10,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-cnr-economy',
        name: '\u7ecf\u6d4e\u4e4b\u58f0',
        country: 'CN',
        region: 'Beijing',
        city: '\u5317\u4eac',
        language: '\u4e2d\u6587',
        bandLabel: 'CNR 2',
        genre: 'News Finance',
        streamUrl: 'https://ngcdn002.cnr.cn/live/jjzs/index.m3u8',
        homepageUrl:
          'https://www.radio.cn/pc-portal/home/index.html?option=default%2Cradio',
        legalNotes,
        isActive: true,
        sortOrder: 20,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-cnr-music',
        name: '\u97f3\u4e50\u4e4b\u58f0',
        country: 'CN',
        region: 'Beijing',
        city: '\u5317\u4eac',
        language: '\u4e2d\u6587',
        bandLabel: 'CNR 3',
        genre: 'Music Pop',
        streamUrl: 'https://ngcdn003.cnr.cn/live/yyzs/index.m3u8',
        homepageUrl:
          'https://www.radio.cn/pc-portal/home/index.html?option=default%2Cradio',
        legalNotes,
        isActive: true,
        sortOrder: 30,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-bay-area',
        name: '\u5927\u6e7e\u533a\u4e4b\u58f0',
        country: 'CN',
        region: 'Guangdong',
        city: '\u5e7f\u5dde',
        language: '\u4e2d\u6587',
        bandLabel: 'CNR 7',
        genre: 'News Life',
        streamUrl: 'https://ngcdn007.cnr.cn/live/hxzs/index.m3u8',
        homepageUrl:
          'https://www.radio.cn/pc-portal/home/index.html?option=default%2Cradio',
        legalNotes,
        isActive: true,
        sortOrder: 40,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-guangdong-news',
        name: '\u5e7f\u4e1c\u65b0\u95fb\u5e7f\u64ad',
        country: 'CN',
        region: 'Guangdong',
        city: '\u5e7f\u5dde',
        language: '\u4e2d\u6587',
        bandLabel: 'FM 91.4',
        genre: 'News Talk',
        streamUrl: 'https://satellitepull.cnr.cn/live/wxgdxwgb/playlist.m3u8',
        homepageUrl:
          'https://www.radio.cn/pc-portal/home/index.html?option=default%2Cradio',
        legalNotes,
        isActive: true,
        sortOrder: 50,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-shanghai-news',
        name: '\u4e0a\u6d77\u65b0\u95fb\u5e7f\u64ad',
        country: 'CN',
        region: 'Shanghai',
        city: '\u4e0a\u6d77',
        language: '\u4e2d\u6587',
        bandLabel: 'FM 93.4',
        genre: 'News Talk',
        streamUrl: 'https://lhttp-hw.qtfm.cn/live/270/64k.mp3',
        homepageUrl: 'https://www.smg.cn/review/index.html',
        legalNotes,
        isActive: true,
        sortOrder: 60,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-shanghai-dynamic-101',
        name: '\u4e0a\u6d77\u6d41\u884c\u97f3\u4e50\u5e7f\u64ad',
        country: 'CN',
        region: 'Shanghai',
        city: '\u4e0a\u6d77',
        language: '\u4e2d\u6587',
        bandLabel: 'FM 101.7',
        genre: 'Music Pop',
        streamUrl: 'https://lhttp-hw.qtfm.cn/live/274/64k.mp3',
        homepageUrl: 'https://www.smg.cn/review/index.html',
        legalNotes,
        isActive: true,
        sortOrder: 70,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-zhejiang-music',
        name: '\u6d59\u6c5f\u97f3\u4e50\u8c03\u9891',
        country: 'CN',
        region: 'Zhejiang',
        city: '\u676d\u5dde',
        language: '\u4e2d\u6587',
        bandLabel: 'FM 96.8',
        genre: 'Music Pop',
        streamUrl: 'http://ali-m-l.cztv.com/channels/lantian/fm968/128k.m3u8',
        homepageUrl: 'http://www.cztv.com/',
        legalNotes,
        isActive: true,
        sortOrder: 80,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-foshan-nanhai',
        name: '\u4f5b\u5c71\u5357\u6d77\u5e7f\u64ad',
        country: 'CN',
        region: 'Guangdong',
        city: '\u4f5b\u5c71',
        language: '\u7ca4\u8bed',
        bandLabel: 'Nanhai Live',
        genre: 'Music Life',
        streamUrl:
          'https://radiopull.radiofoshan.com.cn/live/1400820947_BSID_42_audio.m3u8',
        homepageUrl: 'https://www.radiofoshan.com.cn/',
        legalNotes,
        isActive: true,
        sortOrder: 90,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-beijing-music',
        name: '\u5317\u4eac\u97f3\u4e50\u5e7f\u64ad',
        country: 'CN',
        region: 'Beijing',
        city: '\u5317\u4eac',
        language: '\u4e2d\u6587',
        bandLabel: 'FM 97.4',
        genre: 'Music Pop',
        streamUrl: 'https://lhttp.qtfm.cn/live/332/64k.mp3',
        homepageUrl: 'https://www.rbc.cn/',
        legalNotes,
        isActive: true,
        sortOrder: 100,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-cityfm-music',
        name: 'CityFM \u57ce\u5e02\u97f3\u4e50\u53f0',
        country: 'CN',
        region: 'National',
        city: '\u5168\u56fd',
        language: '\u4e2d\u6587',
        bandLabel: 'CityFM',
        genre: 'Music Pop',
        streamUrl: 'https://lhttp.qtfm.cn/live/20500153/64k.mp3',
        homepageUrl: 'https://www.qingting.fm/radios/20500153',
        legalNotes,
        isActive: true,
        sortOrder: 110,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-liangguang-music',
        name: '\u4e24\u5e7f\u4e4b\u58f0\u97f3\u4e50\u53f0',
        country: 'CN',
        region: 'Guangdong',
        city: '\u5e7f\u5dde',
        language: '\u7ca4\u8bed',
        bandLabel: '\u4e24\u5e7f\u4e4b\u58f0',
        genre: 'Music Oldies',
        streamUrl: 'https://lhttp.qtfm.cn/live/20500149/64k.mp3',
        homepageUrl: 'https://www.qingting.fm/radios/20500149/',
        legalNotes,
        isActive: true,
        sortOrder: 120,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
      {
        id: 'station-asiafm-cantonese',
        name: 'AsiaFM \u4e9a\u6d32\u7ca4\u8bed\u53f0',
        country: 'CN',
        region: 'Hong Kong',
        city: '\u9999\u6e2f',
        language: '\u7ca4\u8bed',
        bandLabel: 'AsiaFM',
        genre: 'Music Pop',
        streamUrl: 'https://lhttp.qtfm.cn/live/15318569/64k.mp3',
        homepageUrl: 'https://superradio.cc/',
        legalNotes,
        isActive: true,
        sortOrder: 130,
        lastCheckedAt: now,
        lastHealthStatus: 'unknown',
        consecutiveFailures: 0,
        createdAt: now,
        updatedAt: now,
      },
    ];
  }

  private seedRadioStations(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM radio_stations')
      .get() as Record<string, unknown>;
    if (Number(countRow.count) > 0) {
      return;
    }
    const now = new Date().toISOString();
    const stations = this.getDefaultRadioStations(now);
    const insert = this.db.prepare(`
      INSERT INTO radio_stations (
        id,
        name,
        country,
        region,
        city,
        language,
        band_label,
        genre,
        stream_url,
        homepage_url,
        logo_url,
        legal_notes,
        is_active,
        sort_order,
        last_checked_at,
        last_health_status,
        consecutive_failures,
        last_health_error,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    this.runInTransaction(() => {
      for (const station of stations) {
        insert.run(
          station.id,
          station.name,
          station.country,
          station.region ?? null,
          station.city,
          station.language,
          station.bandLabel,
          station.genre,
          station.streamUrl,
          station.homepageUrl ?? null,
          station.logoUrl ?? null,
          station.legalNotes ?? null,
          station.isActive ? 1 : 0,
          station.sortOrder,
          station.lastCheckedAt,
          station.lastHealthStatus ?? 'unknown',
          station.consecutiveFailures ?? 0,
          station.lastHealthError ?? null,
          station.createdAt,
          station.updatedAt,
        );
      }
    });
  }

  private seedRadioBroadcasts(): void {
    const countRow = this.db
      .prepare('SELECT COUNT(*) as count FROM radio_broadcasts')
      .get() as Record<string, unknown>;
    if (Number(countRow.count) > 0) {
      return;
    }
    const now = new Date();
    const broadcasts: RadioBroadcastRecord[] = [
      {
        id: 'broadcast-ai-demo-1',
        stationId: 'station-cnr-voice',
        accountId: 'system',
        title: 'AI \u4e3b\u64ad\uff1a\u4eca\u665a 9 \u70b9\u540e\u8fdb\u5165\u591c\u95f4\u7f16\u6392',
        sourceKind: 'ai',
        textTranscript: '\u4eca\u665a 9 \u70b9\u540e\u5c06\u5207\u5165\u591c\u95f4\u7535\u53f0\u7f16\u6392\uff0c\u6b22\u8fce\u7ee7\u7eed\u6536\u542c\u3002',
        durationMs: 28000,
        status: 'ready',
        createdAt: new Date(now.getTime() - 2 * 60 * 1000).toISOString(),
        updatedAt: new Date(now.getTime() - 2 * 60 * 1000).toISOString(),
      },
      {
        id: 'broadcast-user-demo-1',
        stationId: 'station-bay-area',
        accountId: 'user_demo',
        title: '\u7528\u6237\u6295\u7a3f\uff1a\u5e7f\u5dde\u591c\u8272\u91cc\u7684\u8857\u5934\u58f0\u97f3',
        sourceKind: 'user',
        textTranscript: '\u5e7f\u5dde\u591c\u8272\u91cc\u6709\u8def\u53e3\u98ce\u58f0\u3001\u8f66\u6d41\u58f0\u548c\u8fdc\u5904\u7684\u4eba\u7fa4\u58f0\u3002',
        durationMs: 17000,
        status: 'ready',
        createdAt: new Date(now.getTime() - 8 * 60 * 1000).toISOString(),
        updatedAt: new Date(now.getTime() - 8 * 60 * 1000).toISOString(),
      },
    ];
    const insert = this.db.prepare(`
      INSERT INTO radio_broadcasts (
        id,
        station_id,
        account_id,
        title,
        source_kind,
        text_transcript,
        audio_path,
        duration_ms,
        status,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    this.runInTransaction(() => {
      for (const record of broadcasts) {
        insert.run(
          record.id,
          record.stationId ?? null,
          record.accountId,
          record.title,
          record.sourceKind,
          record.textTranscript ?? null,
          record.audioPath ?? null,
          record.durationMs,
          record.status,
          record.createdAt,
          record.updatedAt,
        );
      }
    });
  }

  private normalizeLegacyRadioSeedData(): void {
    const now = new Date().toISOString();
    const upsertStation = this.db.prepare(`
      INSERT OR REPLACE INTO radio_stations (
        id,
        name,
        country,
        region,
        city,
        language,
        band_label,
        genre,
        stream_url,
        homepage_url,
        logo_url,
        legal_notes,
        is_active,
        sort_order,
        last_checked_at,
        last_health_status,
        consecutive_failures,
        last_health_error,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM radio_stations WHERE id = ?), ?), ?)
    `);
    const stations = this.getDefaultRadioStations(now);
    this.runInTransaction(() => {
      for (const station of stations) {
        upsertStation.run(
          station.id,
          station.name,
          station.country,
          station.region ?? null,
          station.city,
          station.language,
          station.bandLabel,
          station.genre,
          station.streamUrl,
          station.homepageUrl ?? null,
          station.logoUrl ?? null,
          station.legalNotes ?? null,
          1,
          station.sortOrder,
          now,
          station.lastHealthStatus ?? 'unknown',
          station.consecutiveFailures ?? 0,
          station.lastHealthError ?? null,
          station.id,
          now,
          now,
        );
      }
      this.db.prepare(`
        UPDATE radio_stations
        SET is_active = 0,
            updated_at = ?
        WHERE id IN ('station-shanghai-night', 'station-tokyo-signal', 'station-pacific-open-line')
      `).run(now);
      this.db.prepare(`
        UPDATE radio_broadcasts
        SET station_id = 'station-cnr-voice',
            title = 'AI \u4e3b\u64ad\uff1a\u4eca\u665a 9 \u70b9\u540e\u8fdb\u5165\u591c\u95f4\u7f16\u6392',
            text_transcript = '\u4eca\u665a 9 \u70b9\u540e\u5c06\u5207\u5165\u591c\u95f4\u7535\u53f0\u7f16\u6392\uff0c\u6b22\u8fce\u7ee7\u7eed\u6536\u542c\u3002',
            updated_at = ?
        WHERE id = 'broadcast-ai-demo-1'
      `).run(now);
      this.db.prepare(`
        UPDATE radio_broadcasts
        SET station_id = 'station-bay-area',
            title = '\u7528\u6237\u6295\u7a3f\uff1a\u5e7f\u5dde\u591c\u8272\u91cc\u7684\u8857\u5934\u58f0\u97f3',
            text_transcript = '\u5e7f\u5dde\u591c\u8272\u91cc\u6709\u8def\u53e3\u98ce\u58f0\u3001\u8f66\u6d41\u58f0\u548c\u8fdc\u5904\u7684\u4eba\u7fa4\u58f0\u3002',
            updated_at = ?
        WHERE id = 'broadcast-user-demo-1'
      `).run(now);
    });
  }

  async getLatestAdminLoginCode(
    email: string,
  ): Promise<AdminLoginCodeRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT id, email, code, expires_at, used_at, created_at
        FROM admin_login_codes
        WHERE email = ?
        ORDER BY created_at DESC
        LIMIT 1
        `,
      )
      .get(email.toLowerCase()) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      email: String(row.email),
      code: String(row.code),
      expiresAt: String(row.expires_at),
      usedAt: (row.used_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
    };
  }

  async markAdminLoginCodeUsed(id: string, usedAt: string): Promise<void> {
    this.db
      .prepare(
        `
        UPDATE admin_login_codes
        SET used_at = ?
        WHERE id = ?
        `,
      )
      .run(usedAt, id);
  }

  async upsertAdminSession(session: AdminSessionRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO admin_sessions (
          token,
          email,
          created_at,
          last_seen_at,
          expires_at
        ) VALUES (?, ?, ?, ?, ?)
        `,
      )
      .run(
        session.token,
        session.email.toLowerCase(),
        session.createdAt,
        session.lastSeenAt,
        session.expiresAt,
      );
  }

  async getAdminSession(
    token: string,
  ): Promise<AdminSessionRecord | undefined> {
    const row = this.db
      .prepare(
        `
          SELECT token, email, created_at, last_seen_at, expires_at
          FROM admin_sessions
          WHERE token = ?
          LIMIT 1
        `,
      )
      .get(token) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

      return {
        token: String(row.token),
        email: String(row.email),
        createdAt: String(row.created_at),
        lastSeenAt: String(row.last_seen_at),
        expiresAt: String(row.expires_at ?? row.last_seen_at),
      };
    }

  async getDeviceUserProfile(
    accountId: string,
  ): Promise<DeviceUserProfileRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT id, display_name, plan_code, status, recovery_hint, entitlement_expires_at, created_at, updated_at
        FROM device_user_profiles
        WHERE id = ?
        LIMIT 1
        `,
      )
      .get(accountId) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      displayName: String(row.display_name),
      planCode: String(row.plan_code),
      status: row.status as DeviceUserProfileRecord['status'],
      recoveryHint: (row.recovery_hint as string | null) ?? undefined,
      entitlementExpiresAt:
        (row.entitlement_expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  async listDeviceUserProfiles(): Promise<DeviceUserProfileRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT id, display_name, plan_code, status, recovery_hint, entitlement_expires_at, created_at, updated_at
        FROM device_user_profiles
        ORDER BY updated_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      displayName: String(row.display_name),
      planCode: String(row.plan_code),
      status: row.status as DeviceUserProfileRecord['status'],
      recoveryHint: (row.recovery_hint as string | null) ?? undefined,
      entitlementExpiresAt:
        (row.entitlement_expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async upsertDeviceUserProfile(profile: DeviceUserProfileRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO device_user_profiles (
          id,
          display_name,
          plan_code,
          status,
          recovery_hint,
          entitlement_expires_at,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        profile.id,
        profile.displayName,
        profile.planCode,
        profile.status,
        profile.recoveryHint ?? null,
        profile.entitlementExpiresAt ?? null,
        profile.createdAt,
        profile.updatedAt,
      );
  }

  async getStablecoinOrderByTxHash(
    txHash: string,
  ): Promise<StablecoinPaymentOrderRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        FROM stablecoin_payment_orders
        WHERE tx_hash = ?
        ORDER BY updated_at DESC
        LIMIT 1
        `,
      )
      .get(txHash) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return {
      id: String(row.id),
      accountId: String(row.account_id),
      paymentMethod: 'stablecoin',
      stablecoinSymbol: String(row.stablecoin_symbol),
      chain: String(row.chain),
      walletAddress: String(row.wallet_address),
      amountUsd: Number(row.amount_usd),
      amountToken: Number(row.amount_token),
      status: row.status as StablecoinPaymentOrderRecord['status'],
      txHash: (row.tx_hash as string | null) ?? undefined,
      confirmations: Number(row.confirmations),
      reviewNote: (row.review_note as string | null) ?? undefined,
      expiresAt: (row.expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  async transferAccountData(params: {
    fromAccountId: string;
    toAccountId: string;
    transferReason: string;
    paymentProofTxHash?: string;
  }): Promise<void> {
    const timestamp = new Date().toISOString();
    this.runInTransaction(() => {
      this.db
        .prepare(`UPDATE devices SET account_id = ? WHERE account_id = ?`)
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(`UPDATE wallet_ledger SET account_id = ? WHERE account_id = ?`)
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(
          `UPDATE stablecoin_payment_orders SET account_id = ? WHERE account_id = ?`,
        )
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(`UPDATE assistant_logs SET account_id = ? WHERE account_id = ?`)
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(
          `UPDATE device_token_usage_events SET account_id = ? WHERE account_id = ?`,
        )
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(
          `UPDATE device_share_bindings SET owner_account_id = ? WHERE owner_account_id = ?`,
        )
        .run(params.toAccountId, params.fromAccountId);
      this.db
        .prepare(
          `
          INSERT INTO device_entitlement_transfers (
            id,
            from_account_id,
            to_account_id,
            transfer_reason,
            payment_proof_tx_hash,
            created_at
          ) VALUES (?, ?, ?, ?, ?, ?)
          `,
        )
        .run(
          `transfer_${Date.now()}`,
          params.fromAccountId,
          params.toAccountId,
          params.transferReason,
          params.paymentProofTxHash ?? null,
          timestamp,
        );
    });
  }

  async listDeviceEntitlementTransfers(): Promise<DeviceEntitlementTransferRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT id, from_account_id, to_account_id, transfer_reason, payment_proof_tx_hash, created_at
        FROM device_entitlement_transfers
        ORDER BY created_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      fromAccountId: String(row.from_account_id),
      toAccountId: String(row.to_account_id),
      transferReason: String(row.transfer_reason),
      paymentProofTxHash:
        (row.payment_proof_tx_hash as string | null) ?? undefined,
      createdAt: String(row.created_at),
    }));
  }

  async listAllStablecoinOrders(): Promise<StablecoinPaymentOrderRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          account_id,
          payment_method,
          stablecoin_symbol,
          chain,
          wallet_address,
          amount_usd,
          amount_token,
          status,
          tx_hash,
          confirmations,
          review_note,
          expires_at,
          created_at,
          updated_at
        FROM stablecoin_payment_orders
        ORDER BY updated_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      paymentMethod: 'stablecoin',
      stablecoinSymbol: String(row.stablecoin_symbol),
      chain: String(row.chain),
      walletAddress: String(row.wallet_address),
      amountUsd: Number(row.amount_usd),
      amountToken: Number(row.amount_token),
      status: row.status as StablecoinPaymentOrderRecord['status'],
      txHash: (row.tx_hash as string | null) ?? undefined,
      confirmations: Number(row.confirmations),
      reviewNote: (row.review_note as string | null) ?? undefined,
      expiresAt: (row.expires_at as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async listApiPoolAccounts(): Promise<ApiPoolAccountRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          provider,
          account_label,
          plan_label,
          status,
          renews_at,
          expires_at,
          notes,
          created_at,
          updated_at
        FROM api_pool_accounts
        ORDER BY updated_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      provider: String(row.provider),
      accountLabel: String(row.account_label),
      planLabel: String(row.plan_label),
      status: row.status as ApiPoolAccountRecord['status'],
      renewsAt: (row.renews_at as string | null) ?? undefined,
      expiresAt: (row.expires_at as string | null) ?? undefined,
      notes: (row.notes as string | null) ?? undefined,
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async upsertApiPoolAccount(account: ApiPoolAccountRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO api_pool_accounts (
          id,
          provider,
          account_label,
          plan_label,
          status,
          renews_at,
          expires_at,
          notes,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        account.id,
        account.provider,
        account.accountLabel,
        account.planLabel,
        account.status,
        account.renewsAt ?? null,
        account.expiresAt ?? null,
        account.notes ?? null,
          account.createdAt,
          account.updatedAt,
        );
    }

  async listApiPoolCredentials(
    accountId?: string,
  ): Promise<ApiPoolCredentialRecord[]> {
    const rows = (accountId == null || accountId.trim().length === 0
      ? this.db
          .prepare(
            `
            SELECT
              id,
              account_id,
              provider,
              label,
              base_url,
              model,
              api_key,
              status,
              created_at,
              updated_at
            FROM api_pool_credentials
            ORDER BY updated_at DESC
            `,
          )
          .all()
      : this.db
          .prepare(
            `
            SELECT
              id,
              account_id,
              provider,
              label,
              base_url,
              model,
              api_key,
              status,
              created_at,
              updated_at
            FROM api_pool_credentials
            WHERE account_id = ?
            ORDER BY updated_at DESC
            `,
          )
          .all(accountId)) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      provider: String(row.provider),
      label: String(row.label),
      baseUrl: String(row.base_url),
      model: String(row.model),
      apiKey: String(row.api_key),
      status: row.status as ApiPoolCredentialRecord['status'],
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async upsertApiPoolCredential(
    credential: ApiPoolCredentialRecord,
  ): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO api_pool_credentials (
          id,
          account_id,
          provider,
          label,
          base_url,
          model,
          api_key,
          status,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        credential.id,
        credential.accountId,
        credential.provider,
        credential.label,
        credential.baseUrl,
        credential.model,
        credential.apiKey,
        credential.status,
        credential.createdAt,
        credential.updatedAt,
      );
  }

  async listApiPoolLeases(accountId?: string): Promise<ApiPoolLeaseRecord[]> {
    const rows = (accountId == null || accountId.trim().length === 0
      ? this.db
          .prepare(
            `
            SELECT
              id,
              account_id,
              credential_id,
              device_user_id,
              device_uuid,
              provider,
              base_url,
              model,
              status,
              leased_at,
              expires_at,
              released_at
            FROM api_pool_leases
            ORDER BY leased_at DESC
            `,
          )
          .all()
      : this.db
          .prepare(
            `
            SELECT
              id,
              account_id,
              credential_id,
              device_user_id,
              device_uuid,
              provider,
              base_url,
              model,
              status,
              leased_at,
              expires_at,
              released_at
            FROM api_pool_leases
            WHERE account_id = ?
            ORDER BY leased_at DESC
            `,
          )
          .all(accountId)) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      accountId: String(row.account_id),
      credentialId: String(row.credential_id),
      deviceUserId: String(row.device_user_id),
      deviceUuid: String(row.device_uuid),
      provider: String(row.provider),
      baseUrl: String(row.base_url),
      model: String(row.model),
      status: row.status as ApiPoolLeaseRecord['status'],
      leasedAt: String(row.leased_at),
      expiresAt: String(row.expires_at),
      releasedAt:
        row.released_at == null ? undefined : String(row.released_at),
    }));
  }

  async upsertApiPoolLease(lease: ApiPoolLeaseRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO api_pool_leases (
          id,
          account_id,
          credential_id,
          device_user_id,
          device_uuid,
          provider,
          base_url,
          model,
          status,
          leased_at,
          expires_at,
          released_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        lease.id,
        lease.accountId,
        lease.credentialId,
        lease.deviceUserId,
        lease.deviceUuid,
        lease.provider,
        lease.baseUrl,
        lease.model,
        lease.status,
        lease.leasedAt,
        lease.expiresAt,
        lease.releasedAt ?? null,
      );
  }

  async listOtaReleases(): Promise<OtaReleaseRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          version_name,
          version_code,
          release_channel,
          rollout_status,
          target_scope,
          rollout_percent,
          device_count,
          install_success_rate,
          notification_mode,
          download_policy,
          install_policy,
          report_policy,
          report_delay_minutes,
          artifact_url,
          release_notes,
          created_at,
          updated_at
        FROM ota_releases
        ORDER BY created_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      versionName: String(row.version_name),
      versionCode: Number(row.version_code),
      releaseChannel: row.release_channel as OtaReleaseRecord['releaseChannel'],
      rolloutStatus: row.rollout_status as OtaReleaseRecord['rolloutStatus'],
        targetScope: String(row.target_scope),
        rolloutPercent: Number(row.rollout_percent),
        deviceCount: Number(row.device_count),
        installSuccessRate: Number(row.install_success_rate),
        notificationMode:
          (row.notification_mode as OtaReleaseRecord['notificationMode'] | null) ??
          'broadcast',
        downloadPolicy:
          (row.download_policy as OtaReleaseRecord['downloadPolicy'] | null) ??
          'idle_background',
        installPolicy:
          (row.install_policy as OtaReleaseRecord['installPolicy'] | null) ??
          'next_boot',
        reportPolicy:
          (row.report_policy as OtaReleaseRecord['reportPolicy'] | null) ?? 'lazy',
        reportDelayMinutes: Number(row.report_delay_minutes ?? 60),
        artifactUrl: row.artifact_url == null ? undefined : String(row.artifact_url),
        releaseNotes: row.release_notes == null ? undefined : String(row.release_notes),
        createdAt: String(row.created_at),
        updatedAt: String(row.updated_at),
      }));
    }

  async upsertOtaRelease(release: OtaReleaseRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO ota_releases (
          id,
          version_name,
          version_code,
          release_channel,
          rollout_status,
            target_scope,
            rollout_percent,
            device_count,
            install_success_rate,
            notification_mode,
            download_policy,
            install_policy,
            report_policy,
            report_delay_minutes,
            artifact_url,
            release_notes,
            created_at,
            updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `,
        )
      .run(
        release.id,
        release.versionName,
        release.versionCode,
        release.releaseChannel,
        release.rolloutStatus,
          release.targetScope,
          release.rolloutPercent,
          release.deviceCount,
          release.installSuccessRate,
          release.notificationMode,
          release.downloadPolicy,
          release.installPolicy,
          release.reportPolicy,
          release.reportDelayMinutes,
          release.artifactUrl ?? null,
          release.releaseNotes ?? null,
          release.createdAt,
          release.updatedAt,
        );
  }

  async listOtaDeviceReports(deviceUuid?: string): Promise<OtaDeviceReportRecord[]> {
    const rows = (deviceUuid == null || deviceUuid.trim().length === 0
      ? this.db
          .prepare(
            `
            SELECT
              id,
              release_id,
              account_id,
              device_uuid,
              current_version_code,
              target_version_code,
              status,
              progress_percent,
              note,
              reported_at,
              updated_at
            FROM ota_device_reports
            ORDER BY updated_at DESC
            `,
          )
          .all()
      : this.db
          .prepare(
            `
            SELECT
              id,
              release_id,
              account_id,
              device_uuid,
              current_version_code,
              target_version_code,
              status,
              progress_percent,
              note,
              reported_at,
              updated_at
            FROM ota_device_reports
            WHERE device_uuid = ?
            ORDER BY updated_at DESC
            `,
          )
          .all(deviceUuid)) as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      releaseId: String(row.release_id),
      accountId: String(row.account_id),
      deviceUuid: String(row.device_uuid),
      currentVersionCode: Number(row.current_version_code),
      targetVersionCode: Number(row.target_version_code),
      status: row.status as OtaDeviceReportRecord['status'],
      progressPercent: Number(row.progress_percent),
      note: row.note == null ? undefined : String(row.note),
      reportedAt: String(row.reported_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async upsertOtaDeviceReport(report: OtaDeviceReportRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO ota_device_reports (
          id,
          release_id,
          account_id,
          device_uuid,
          current_version_code,
          target_version_code,
          status,
          progress_percent,
          note,
          reported_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        report.id,
        report.releaseId,
        report.accountId,
        report.deviceUuid,
        report.currentVersionCode,
        report.targetVersionCode,
        report.status,
        report.progressPercent,
        report.note,
        report.reportedAt,
        report.updatedAt,
      );
  }

  async listTvHomeConfigs(): Promise<TvHomeConfigRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          country_code,
          region_code,
          background_image_url,
          featured_app_ids_json,
          status,
          version,
          created_at,
          updated_at
        FROM tv_home_configs
        ORDER BY country_code ASC, COALESCE(region_code, '') ASC, updated_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapTvHomeConfigRow(row));
  }

  async resolveTvHomeConfig(params: {
    countryCode?: string;
    regionCode?: string;
  }): Promise<TvHomeConfigRecord | undefined> {
    const normalizedCountry = (params.countryCode ?? '').trim().toUpperCase();
    const normalizedRegion = (params.regionCode ?? '').trim().toUpperCase();
    const configs = await this.listTvHomeConfigs();
    const activeConfigs = configs.filter((item) => item.status === 'active');

    const exactScoped = activeConfigs.find(
      (item) =>
        item.countryCode === normalizedCountry &&
        (item.regionCode ?? '') === normalizedRegion,
    );
    if (exactScoped != null) {
      return exactScoped;
    }

    const countryWide = activeConfigs.find(
      (item) =>
        item.countryCode === normalizedCountry &&
        (!item.regionCode || item.regionCode === 'GLOBAL'),
    );
    if (countryWide != null) {
      return countryWide;
    }

    return (
      activeConfigs.find(
        (item) =>
          item.countryCode === 'GLOBAL' &&
          (!item.regionCode || item.regionCode === 'GLOBAL'),
      ) ?? activeConfigs[0]
    );
  }

  async upsertTvHomeConfig(config: TvHomeConfigRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO tv_home_configs (
          id,
          country_code,
          region_code,
          background_image_url,
          featured_app_ids_json,
          status,
          version,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        config.id,
        config.countryCode,
        config.regionCode ?? null,
        config.backgroundImageUrl ?? null,
        JSON.stringify(config.featuredAppIds),
        config.status,
        config.version,
        config.createdAt,
        config.updatedAt,
      );
  }

  async listClientConfigReleases(): Promise<ClientConfigReleaseRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          version_name,
          version_code,
          target_scope,
          config_key,
          payload_json,
          rollout_status,
          notification_mode,
          fetch_policy,
          apply_policy,
          release_notes,
          created_at,
          updated_at
        FROM client_config_releases
        ORDER BY created_at DESC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => ({
      id: String(row.id),
      versionName: String(row.version_name),
      versionCode: Number(row.version_code),
      targetScope: String(row.target_scope),
      configKey: String(row.config_key),
      payloadJson: String(row.payload_json),
      rolloutStatus: row.rollout_status as ClientConfigReleaseRecord['rolloutStatus'],
      notificationMode:
        (row.notification_mode as ClientConfigReleaseRecord['notificationMode'] | null) ??
        'broadcast',
      fetchPolicy:
        (row.fetch_policy as ClientConfigReleaseRecord['fetchPolicy'] | null) ??
        'idle_background',
      applyPolicy:
        (row.apply_policy as ClientConfigReleaseRecord['applyPolicy'] | null) ?? 'idle_apply',
      releaseNotes: row.release_notes == null ? undefined : String(row.release_notes),
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    }));
  }

  async upsertClientConfigRelease(config: ClientConfigReleaseRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO client_config_releases (
          id,
          version_name,
          version_code,
          target_scope,
          config_key,
          payload_json,
          rollout_status,
          notification_mode,
          fetch_policy,
          apply_policy,
          release_notes,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        config.id,
        config.versionName,
        config.versionCode,
        config.targetScope,
        config.configKey,
        config.payloadJson,
        config.rolloutStatus,
        config.notificationMode,
        config.fetchPolicy,
        config.applyPolicy,
        config.releaseNotes ?? null,
        config.createdAt,
        config.updatedAt,
      );
  }

  async listRadioStations(): Promise<RadioStationRecord[]> {
    const rows = this.db
      .prepare(
        `
        SELECT
          id,
          name,
          country,
          region,
          city,
          language,
          band_label,
          genre,
          stream_url,
          homepage_url,
          logo_url,
          legal_notes,
          is_active,
          sort_order,
          last_checked_at,
          last_health_status,
          consecutive_failures,
          last_health_error,
          created_at,
          updated_at
        FROM radio_stations
        WHERE is_active = 1
        ORDER BY sort_order ASC, name ASC
        `,
      )
      .all() as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapRadioStationRow(row));
  }

  async getRadioStation(id: string): Promise<RadioStationRecord | undefined> {
    const row = this.db
      .prepare(
        `
        SELECT
          id,
          name,
          country,
          region,
          city,
          language,
          band_label,
          genre,
          stream_url,
          homepage_url,
          logo_url,
          legal_notes,
          is_active,
          sort_order,
          last_checked_at,
          last_health_status,
          consecutive_failures,
          last_health_error,
          created_at,
          updated_at
        FROM radio_stations
        WHERE id = ?
        LIMIT 1
        `,
      )
      .get(id) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return this.mapRadioStationRow(row);
  }

  async upsertRadioStation(record: RadioStationRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO radio_stations (
          id,
          name,
          country,
          region,
          city,
          language,
          band_label,
          genre,
          stream_url,
          homepage_url,
          logo_url,
          legal_notes,
          is_active,
          sort_order,
          last_checked_at,
          last_health_status,
          consecutive_failures,
          last_health_error,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        record.id,
        record.name,
        record.country,
        record.region ?? null,
        record.city,
        record.language,
        record.bandLabel,
        record.genre,
        record.streamUrl,
        record.homepageUrl ?? null,
        record.logoUrl ?? null,
        record.legalNotes ?? null,
        record.isActive ? 1 : 0,
        record.sortOrder,
        record.lastCheckedAt,
        record.lastHealthStatus ?? 'unknown',
        record.consecutiveFailures ?? 0,
        record.lastHealthError ?? null,
        record.createdAt,
        record.updatedAt,
      );
  }

  async listRadioBroadcasts(params?: {
    stationId?: string;
    limit?: number;
  }): Promise<RadioBroadcastRecord[]> {
    this.flushBufferedLogs();
    const limit = Math.max(1, Math.min(100, params?.limit ?? 30));
    const stationId = params?.stationId?.trim();
    const rows = (stationId == null || stationId.length === 0
      ? this.db
          .prepare(
            `
            SELECT
              id,
              station_id,
              account_id,
              title,
              source_kind,
              text_transcript,
              audio_path,
              duration_ms,
              status,
              target_scope,
              created_at,
              updated_at
            FROM radio_broadcasts
            ORDER BY created_at DESC
            LIMIT ?
            `,
          )
          .all(limit)
      : this.db
          .prepare(
            `
            SELECT
              id,
              station_id,
              account_id,
              title,
              source_kind,
              text_transcript,
              audio_path,
              duration_ms,
              status,
              target_scope,
              created_at,
              updated_at
            FROM radio_broadcasts
            WHERE station_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            `,
          )
          .all(stationId, limit)) as Array<Record<string, unknown>>;

    return rows.map((row) => this.mapRadioBroadcastRow(row));
  }

  async createRadioBroadcast(record: RadioBroadcastRecord): Promise<void> {
    this.db
      .prepare(
        `
        INSERT OR REPLACE INTO radio_broadcasts (
          id,
          station_id,
          account_id,
          title,
          source_kind,
          text_transcript,
          audio_path,
          duration_ms,
          status,
          target_scope,
          created_at,
          updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
      )
      .run(
        record.id,
        record.stationId ?? null,
        record.accountId,
        record.title,
        record.sourceKind,
        record.textTranscript ?? null,
        record.audioPath ?? null,
        record.durationMs,
        record.status,
        record.targetScope ?? null,
        record.createdAt,
        record.updatedAt,
      );
  }

  async getRadioBroadcast(id: string): Promise<RadioBroadcastRecord | undefined> {
    this.flushBufferedLogs();
    const row = this.db
      .prepare(
        `
        SELECT
          id,
          station_id,
          account_id,
          title,
          source_kind,
          text_transcript,
          audio_path,
          duration_ms,
          status,
          target_scope,
          created_at,
          updated_at
        FROM radio_broadcasts
        WHERE id = ?
        LIMIT 1
        `,
      )
      .get(id) as Record<string, unknown> | undefined;

    if (row == null) {
      return undefined;
    }

    return this.mapRadioBroadcastRow(row);
  }

  private mapTvHomeConfigRow(row: Record<string, unknown>): TvHomeConfigRecord {
    const rawFeaturedAppIds = row.featured_app_ids_json as string | null;
    let featuredAppIds: string[] = [];

    if (rawFeaturedAppIds) {
      try {
        const parsed = JSON.parse(rawFeaturedAppIds) as unknown;
        if (Array.isArray(parsed)) {
          featuredAppIds = parsed
            .map((item) => String(item).trim())
            .filter((item) => item.length > 0);
        }
      } catch {
        featuredAppIds = [];
      }
    }

    return {
      id: String(row.id),
      countryCode: String(row.country_code).toUpperCase(),
      regionCode:
        row.region_code == null ? undefined : String(row.region_code).toUpperCase(),
      backgroundImageUrl:
        row.background_image_url == null
          ? undefined
          : String(row.background_image_url),
      featuredAppIds,
      status: row.status as TvHomeConfigRecord['status'],
      version: Number(row.version),
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  private mapRadioStationRow(row: Record<string, unknown>): RadioStationRecord {
    return {
      id: String(row.id),
      name: String(row.name),
      country: String(row.country),
      region: row.region == null ? undefined : String(row.region),
      city: String(row.city),
      language: String(row.language),
      bandLabel: String(row.band_label),
      genre: String(row.genre),
      streamUrl: String(row.stream_url),
      homepageUrl: row.homepage_url == null ? undefined : String(row.homepage_url),
      logoUrl: row.logo_url == null ? undefined : String(row.logo_url),
      legalNotes: row.legal_notes == null ? undefined : String(row.legal_notes),
      isActive: Number(row.is_active) === 1,
      sortOrder: Number(row.sort_order),
      lastCheckedAt: String(row.last_checked_at),
      lastHealthStatus:
        row.last_health_status == null
          ? 'unknown'
          : (String(row.last_health_status) as RadioStationRecord['lastHealthStatus']),
      consecutiveFailures: Number(row.consecutive_failures ?? 0),
      lastHealthError:
        row.last_health_error == null ? undefined : String(row.last_health_error),
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  private mapRadioBroadcastRow(row: Record<string, unknown>): RadioBroadcastRecord {
    return {
      id: String(row.id),
      stationId: row.station_id == null ? undefined : String(row.station_id),
      accountId: String(row.account_id),
      title: String(row.title),
      sourceKind: row.source_kind as RadioBroadcastRecord['sourceKind'],
      textTranscript:
        row.text_transcript == null ? undefined : String(row.text_transcript),
      audioPath: row.audio_path == null ? undefined : String(row.audio_path),
      durationMs: Number(row.duration_ms),
      status: row.status as RadioBroadcastRecord['status'],
      targetScope: row.target_scope == null ? undefined : String(row.target_scope),
      createdAt: String(row.created_at),
      updatedAt: String(row.updated_at),
    };
  }

  private flushBufferedLogs() {
    if (this.logFlushTimer != null) {
      clearTimeout(this.logFlushTimer);
      this.logFlushTimer = null;
    }

    if (this.bufferedLogs.length === 0) {
      return;
    }

    const pending = this.bufferedLogs.splice(0, this.bufferedLogs.length);
    const insert = this.db.prepare(
      `
      INSERT OR REPLACE INTO assistant_logs (
        id,
        account_id,
        kind,
        device_id,
        locale,
        user_text,
        assistant_text,
        app_id,
        action,
        query_text,
        mode,
        model_provider,
        route,
        transport_mode,
        token_usage,
        bootstrap_token_remaining,
        created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `,
    );
    const trim = this.db.prepare(
      `
      DELETE FROM assistant_logs
      WHERE id NOT IN (
        SELECT id
        FROM assistant_logs
        ORDER BY created_at DESC
        LIMIT 100
      )
      `,
    );

    this.runInTransaction(() => {
      for (const log of pending) {
        insert.run(
          log.id,
          log.accountId,
          log.kind,
          log.deviceId ?? null,
          log.locale,
          log.userText,
          log.assistantText,
          log.appId ?? null,
          log.action ?? null,
          log.queryText ?? null,
          log.mode,
          log.modelProvider,
          log.route ?? null,
          log.transportMode ?? null,
          log.tokenUsage,
          log.bootstrapTokenRemaining,
          log.createdAt,
        );
      }
      trim.run();
    });
  }

  private resolveLogFlushDelayMs() {
    const raw = Number.parseInt(process.env.LOG_FLUSH_DELAY_MS ?? '1200', 10);
    if (Number.isNaN(raw)) {
      return 1200;
    }

    return Math.max(200, Math.min(5000, raw));
  }

  private resolveLogFlushBatchSize() {
    const raw = Number.parseInt(process.env.LOG_FLUSH_BATCH_SIZE ?? '20', 10);
    if (Number.isNaN(raw)) {
      return 20;
    }

    return Math.max(5, Math.min(100, raw));
  }
}

