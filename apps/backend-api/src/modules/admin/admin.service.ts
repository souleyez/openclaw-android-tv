import { Injectable } from '@nestjs/common';

import { RadioService } from '../radio/radio.service';
import {
  ApiPoolAccountRecord,
  AssistantLogRecord,
  AvatarProfileRecord,
  DeviceUserProfileRecord,
  OtaReleaseRecord,
  StablecoinPaymentOrderRecord,
  StorageService,
  TvHomeConfigRecord,
  WalletLedgerRecord,
} from '../../shared/storage.service';

export interface UpsertApiPoolAccountInput {
  id?: string;
  provider: string;
  accountLabel: string;
  planLabel: string;
  status: ApiPoolAccountRecord['status'];
  renewsAt?: string;
  expiresAt?: string;
  notes?: string;
}

export interface ImportApiPoolCredentialsInput {
  accountId: string;
  provider: string;
  baseUrl: string;
  model: string;
  rawKeys: string;
}

export interface UpdateDeviceUserEntitlementInput {
  deviceUserId: string;
  planCode: string;
  entitlementExpiresAt?: string;
  recoveryHint?: string;
  status?: DeviceUserProfileRecord['status'];
}

export interface UpdateAdminOrderStatusInput {
  orderId: string;
  status: StablecoinPaymentOrderRecord['status'];
  reviewNote?: string;
}

export interface CreateOtaReleaseInput {
  versionName: string;
  versionCode: number;
  releaseChannel: OtaReleaseRecord['releaseChannel'];
  rolloutStatus: OtaReleaseRecord['rolloutStatus'];
  targetScope: string;
  rolloutPercent: number;
  deviceCount: number;
  installSuccessRate: number;
}

export interface UpdateOtaReleaseStatusInput {
  releaseId: string;
  rolloutStatus: OtaReleaseRecord['rolloutStatus'];
  rolloutPercent?: number;
  deviceCount?: number;
  installSuccessRate?: number;
}

export interface UpsertTvHomeConfigInput {
  id?: string;
  countryCode: string;
  regionCode?: string;
  backgroundImageUrl?: string;
  featuredAppIds: string[];
  status: TvHomeConfigRecord['status'];
}

export interface AdminDeviceUserFilters {
  q?: string;
  status?: DeviceUserProfileRecord['status'] | '';
}

export interface AdminOrderFilters {
  q?: string;
  status?: StablecoinPaymentOrderRecord['status'] | '';
}

export interface AdminLogFilters {
  q?: string;
  mode?: string;
  transportMode?: string;
}

export interface AdminApiPoolFilters {
  q?: string;
  status?: ApiPoolAccountRecord['status'] | '';
}

export interface AdminApiPoolAccountItem extends ApiPoolAccountRecord {
  totalApis: number;
  inUseApis: number;
  idleApis: number;
  utilizationRate: number;
}

export interface PaginationInput {
  page?: number;
  pageSize?: number;
}

export interface PaginatedResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminFinanceSnapshot {
  metrics: {
    totalOrders: number;
    confirmedOrders: number;
    pendingRevenueUsd: number;
    settledRevenueUsd: number;
    tokenGranted: number;
    tokenUsed: number;
    apiPoolExpiring: number;
  };
  recentOrders: StablecoinPaymentOrderRecord[];
  walletLedger: WalletLedgerRecord[];
  apiPoolAccounts: ApiPoolAccountRecord[];
}

export interface AdminTransportMetrics {
  directLease: number;
  serverFallback: number;
  offlineLocal: number;
}

export interface AdminRiskSnapshot {
  metrics: {
    expiringRights: number;
    failedOrders: number;
    reviewOrders: number;
    blockedEvents: number;
    expiringApiPoolAccounts: number;
    directLease: number;
    serverFallback: number;
    offlineLocal: number;
  };
  expiringRights: DeviceUserProfileRecord[];
  failedOrders: StablecoinPaymentOrderRecord[];
  reviewOrders: StablecoinPaymentOrderRecord[];
  blockedEvents: AssistantLogRecord[];
  expiringApiPoolAccounts: ApiPoolAccountRecord[];
}

export interface AdminOtaVersionRecord {
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
  createdAt: string;
  updatedAt: string;
}

export interface AdminOtaSnapshot {
  metrics: {
    activeRollouts: number;
    pausedRollouts: number;
    versionGroups: number;
    otaReadyDevices: number;
  };
  releases: AdminOtaVersionRecord[];
      rolloutTemplates: Array<{
    id: string;
    label: string;
    releaseChannel: OtaReleaseRecord['releaseChannel'];
    rolloutStatus: OtaReleaseRecord['rolloutStatus'];
    targetScope: string;
    rolloutPercent: number;
    deviceCount: number;
    installSuccessRate: number;
  }>;
  deviceVersionDistribution: Array<{
    versionName: string;
    deviceCount: number;
    androidVersionGroup: string;
  }>;
  rolloutNotes: string[];
}

export interface AdminTvHomeCatalogItem {
  appId: string;
  displayName: string;
  packageName?: string;
  supportTier: 'full' | 'basic';
  supportedActions: string[];
}

export interface AdminTvHomeSnapshot {
  configs: TvHomeConfigRecord[];
  appCatalog: AdminTvHomeCatalogItem[];
}

@Injectable()
export class AdminService {
  constructor(
    private readonly storageService: StorageService,
    private readonly radioService: RadioService,
  ) {}

  async getDashboardSummary() {
    const [deviceUsers, orders, transfers, apiPoolAccounts, logs, avatars, radioStations, apiPoolLeases] =
      await Promise.all([
        this.storageService.listDeviceUserProfiles(),
        this.storageService.listAllStablecoinOrders(),
        this.storageService.listDeviceEntitlementTransfers(),
        this.buildApiPoolAccountItems(),
        this.storageService.listAllLogs(30),
        this.storageService.getAvatarProfiles(),
        this.storageService.listRadioStations(),
        this.storageService.listApiPoolLeases(),
      ]);
    const transport = this.buildTransportMetrics(logs);
    const activeOrders = orders.filter((item) =>
      ['pending', 'confirming', 'reviewing'].includes(item.status),
    ).length;
    const activeLeases = apiPoolLeases.filter(
      (item) => item.status === 'active' && new Date(item.expiresAt).getTime() > Date.now(),
    );
    const activeLeaseUsers = new Set(activeLeases.map((item) => item.deviceUserId));

    return {
      counts: {
        deviceUsers: deviceUsers.length,
        orders: orders.length,
        transfers: transfers.length,
        apiPoolAccounts: apiPoolAccounts.length,
        logs: logs.length,
        radioStations: radioStations.length,
      },
      metrics: {
        activeOrders,
        transport,
        activeModelLeases: activeLeases.length,
        activeLeaseUsers: activeLeaseUsers.size,
      },
      deviceUsers,
      orders: orders.slice(0, 20),
      transfers: transfers.slice(0, 20),
      apiPoolAccounts,
      logs,
      avatars,
    };
  }

  async listDeviceUsers(
    filters: AdminDeviceUserFilters = {},
    pagination: PaginationInput = {},
  ): Promise<PaginatedResult<DeviceUserProfileRecord>> {
    const deviceUsers = await this.storageService.listDeviceUserProfiles();
    const query = (filters.q ?? '').trim().toLowerCase();
    const status = (filters.status ?? '').trim().toLowerCase();

    const filtered = deviceUsers.filter((item) => {
      const matchesStatus = !status || item.status.toLowerCase() === status;
      const haystack = [
        item.id,
        item.displayName,
        item.planCode,
        item.status,
        item.recoveryHint,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return matchesStatus && (!query || haystack.includes(query));
    });

    return this.paginate(filtered, pagination);
  }

  async listOrders(
    filters: AdminOrderFilters = {},
    pagination: PaginationInput = {},
  ): Promise<PaginatedResult<StablecoinPaymentOrderRecord>> {
    const orders = await this.storageService.listAllStablecoinOrders();
    const query = (filters.q ?? '').trim().toLowerCase();
    const status = (filters.status ?? '').trim().toLowerCase();

    const filtered = orders.filter((item) => {
      const matchesStatus = !status || item.status.toLowerCase() === status;
      const haystack = [
        item.id,
        item.accountId,
        item.chain,
        item.stablecoinSymbol,
        item.txHash,
        item.walletAddress,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return matchesStatus && (!query || haystack.includes(query));
    });

    return this.paginate(filtered, pagination);
  }

  async listLogs(
    filters: AdminLogFilters = {},
    pagination: PaginationInput = {},
  ): Promise<PaginatedResult<AssistantLogRecord>> {
    const logs = await this.storageService.listAllLogs(100);
    const query = (filters.q ?? '').trim().toLowerCase();
    const mode = (filters.mode ?? '').trim().toLowerCase();
    const transportMode = (filters.transportMode ?? '').trim().toLowerCase();

    const filtered = logs.filter((item) => {
      const matchesMode = !mode || (item.mode ?? '').toLowerCase() === mode;
      const matchesTransportMode =
        !transportMode ||
        (item.transportMode ?? '').toLowerCase() === transportMode;
      const haystack = [
        item.accountId,
        item.deviceId,
        item.kind,
        item.mode,
        item.transportMode,
        item.route,
        item.userText,
        item.assistantText,
        item.appId,
        item.action,
        item.modelProvider,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return (
        matchesMode &&
        matchesTransportMode &&
        (!query || haystack.includes(query))
      );
    });

    return this.paginate(filtered, pagination);
  }

  async listApiPoolAccounts(
    filters: AdminApiPoolFilters = {},
    pagination: PaginationInput = {},
  ): Promise<PaginatedResult<AdminApiPoolAccountItem>> {
    const enriched = await this.buildApiPoolAccountItems();
    const query = (filters.q ?? '').trim().toLowerCase();
    const status = (filters.status ?? '').trim().toLowerCase();

    const filtered = enriched.filter((item) => {
      const matchesStatus = !status || item.status.toLowerCase() === status;
      const haystack = [
        item.provider,
        item.accountLabel,
        item.planLabel,
        item.status,
        item.notes,
        String(item.totalApis),
        String(item.inUseApis),
        String(item.idleApis),
        String(item.utilizationRate),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return matchesStatus && (!query || haystack.includes(query));
    });

    return this.paginate(filtered, pagination);
  }

  async getFinanceSnapshot(): Promise<AdminFinanceSnapshot> {
    const [orders, walletLedger, apiPoolAccounts] = await Promise.all([
      this.storageService.listAllStablecoinOrders(),
      this.storageService.listAllWalletLedger(),
      this.storageService.listApiPoolAccounts(),
    ]);

    const confirmedOrders = orders.filter((item) => item.status === 'confirmed');
    const pendingOrders = orders.filter(
      (item) => item.status === 'confirming' || item.status === 'reviewing' || item.status === 'pending',
    );
    const tokenGranted = walletLedger
      .filter((entry) => entry.currency === 'token' && entry.amount > 0)
      .reduce((sum, entry) => sum + entry.amount, 0);
    const tokenUsed = walletLedger
      .filter((entry) => entry.currency === 'token' && entry.amount < 0)
      .reduce((sum, entry) => sum + Math.abs(entry.amount), 0);
    const apiPoolExpiring = apiPoolAccounts.filter((item) => item.status === 'expiring').length;

    return {
      metrics: {
        totalOrders: orders.length,
        confirmedOrders: confirmedOrders.length,
        pendingRevenueUsd: pendingOrders.reduce((sum, item) => sum + item.amountUsd, 0),
        settledRevenueUsd: confirmedOrders.reduce((sum, item) => sum + item.amountUsd, 0),
        tokenGranted,
        tokenUsed,
        apiPoolExpiring,
      },
      recentOrders: orders.slice(0, 12),
      walletLedger: walletLedger.slice(0, 20),
      apiPoolAccounts: apiPoolAccounts.slice(0, 8),
    };
  }

  async getRiskSnapshot(): Promise<AdminRiskSnapshot> {
    const [deviceUsers, orders, logs, apiPoolAccounts] = await Promise.all([
      this.storageService.listDeviceUserProfiles(),
      this.storageService.listAllStablecoinOrders(),
      this.storageService.listAllLogs(100),
      this.storageService.listApiPoolAccounts(),
    ]);

    const now = Date.now();
    const expiringRights = deviceUsers.filter((item) => {
      if (!item.entitlementExpiresAt) {
        return false;
      }
      const diffDays = Math.ceil((new Date(item.entitlementExpiresAt).getTime() - now) / (1000 * 60 * 60 * 24));
      return diffDays <= 14;
    });
    const failedOrders = orders.filter((item) => item.status === 'failed' || item.status === 'expired').slice(0, 12);
    const reviewOrders = orders.filter((item) => item.status === 'reviewing').slice(0, 12);
    const blockedEvents = logs.filter((item) => item.modelProvider === 'client_policy_guard').slice(0, 12);
    const transport = this.buildTransportMetrics(logs);
    const expiringApiPoolAccounts = apiPoolAccounts.filter((item) => {
      if (item.status === 'expiring') {
        return true;
      }
      if (!item.expiresAt) {
        return false;
      }
      const diffDays = Math.ceil((new Date(item.expiresAt).getTime() - now) / (1000 * 60 * 60 * 24));
      return diffDays <= 10;
    }).slice(0, 12);

    return {
      metrics: {
        expiringRights: expiringRights.length,
        failedOrders: failedOrders.length,
        reviewOrders: reviewOrders.length,
        blockedEvents: blockedEvents.length,
        expiringApiPoolAccounts: expiringApiPoolAccounts.length,
        directLease: transport.directLease,
        serverFallback: transport.serverFallback,
        offlineLocal: transport.offlineLocal,
      },
      expiringRights: expiringRights.slice(0, 12),
      failedOrders,
      reviewOrders,
      blockedEvents,
      expiringApiPoolAccounts,
    };
  }

  async getOtaSnapshot(): Promise<AdminOtaSnapshot> {
    const [deviceUsers, releases] = await Promise.all([
      this.storageService.listDeviceUserProfiles(),
      this.storageService.listOtaReleases(),
    ]);
    const rolloutTemplates = [
      {
        id: 'stable-ring',
        label: 'Stable Ring',
        releaseChannel: 'stable' as const,
        rolloutStatus: 'draft' as const,
        targetScope: 'Stable ring / all passed devices / staged by region',
        rolloutPercent: 25,
        deviceCount: Math.max(24, deviceUsers.length * 4),
        installSuccessRate: 99.2,
      },
      {
        id: 'beta-ring',
        label: 'Beta Ring',
        releaseChannel: 'beta' as const,
        rolloutStatus: 'draft' as const,
        targetScope: 'radio-app / internal / CN',
        rolloutPercent: 10,
        deviceCount: Math.max(10, deviceUsers.length),
        installSuccessRate: 97.5,
      },
      {
        id: 'legacy-ring',
        label: 'Legacy Ring',
        releaseChannel: 'internal' as const,
        rolloutStatus: 'draft' as const,
        targetScope: 'Android 7-8 legacy ring / low-risk wave',
        rolloutPercent: 5,
        deviceCount: 6,
        installSuccessRate: 95.5,
      },
    ];

    const deviceVersionDistribution = [
      {
        versionName: releases[1]?.versionName ?? '0.1.7-stable',
        deviceCount: Math.max(2, deviceUsers.length * 3),
        androidVersionGroup: 'Android 9-11 TV',
      },
      {
        versionName: releases[0]?.versionName ?? '0.1.8-beta',
        deviceCount: Math.max(1, deviceUsers.length),
        androidVersionGroup: 'Android 9-10 TV beta ring',
      },
      {
        versionName: '0.1.6-legacy',
        deviceCount: 4,
        androidVersionGroup: 'Android 7-8 legacy',
      },
    ];

    return {
      metrics: {
        activeRollouts: releases.filter((item) => item.rolloutStatus === 'rolling').length,
        pausedRollouts: releases.filter((item) => item.rolloutStatus === 'paused').length,
        versionGroups: deviceVersionDistribution.length,
        otaReadyDevices: deviceVersionDistribution.reduce((sum, item) => sum + item.deviceCount, 0),
      },
      releases,
      rolloutTemplates,
      deviceVersionDistribution,
      rolloutNotes: [
        'Use broadcast notification first, then let clients download quietly while idle.',
        'Only stage installation after the package is fully downloaded and verified.',
        'Apply the new version on next boot or shutdown window, and report success lazily later.',
      ],
    };
  }

  async createOtaRelease(input: CreateOtaReleaseInput): Promise<OtaReleaseRecord> {
    const now = new Date().toISOString();
    const release: OtaReleaseRecord = {
      id: `ota_${input.releaseChannel}_${input.versionCode}_${Date.now()}`,
      versionName: input.versionName,
      versionCode: input.versionCode,
      releaseChannel: input.releaseChannel,
      rolloutStatus: input.rolloutStatus,
      targetScope: input.targetScope,
      rolloutPercent: input.rolloutPercent,
      deviceCount: input.deviceCount,
      installSuccessRate: input.installSuccessRate,
      notificationMode: 'broadcast',
      downloadPolicy: 'idle_background',
      installPolicy: 'next_boot',
      reportPolicy: 'lazy',
      reportDelayMinutes: 60,
      createdAt: now,
      updatedAt: now,
    };

    await this.storageService.upsertOtaRelease(release);
    return release;
  }

  async updateOtaReleaseStatus(
    input: UpdateOtaReleaseStatusInput,
  ): Promise<OtaReleaseRecord> {
    const existing = (await this.storageService.listOtaReleases()).find(
      (item) => item.id === input.releaseId,
    );
    if (existing == null) {
      throw new Error(`OTA release not found: ${input.releaseId}`);
    }

    const release: OtaReleaseRecord = {
      ...existing,
      rolloutStatus: input.rolloutStatus,
      rolloutPercent: input.rolloutPercent ?? existing.rolloutPercent,
      deviceCount: input.deviceCount ?? existing.deviceCount,
      installSuccessRate:
        input.installSuccessRate ?? existing.installSuccessRate,
      updatedAt: new Date().toISOString(),
    };

    await this.storageService.upsertOtaRelease(release);
    return release;
  }

  async upsertApiPoolAccount(
    input: UpsertApiPoolAccountInput,
  ): Promise<ApiPoolAccountRecord> {
    const now = new Date().toISOString();
    const existing =
      input.id != null
        ? (await this.storageService.listApiPoolAccounts()).find(
            (item) => item.id === input.id,
          )
        : undefined;
    const account: ApiPoolAccountRecord = {
      id:
        existing?.id ??
        input.id ??
        `pool_${input.provider.toLowerCase().replace(/[^a-z0-9]+/g, '_')}_${Date.now()}`,
      provider: input.provider,
      accountLabel: input.accountLabel,
      planLabel: input.planLabel,
      status: input.status,
      renewsAt: input.renewsAt,
      expiresAt: input.expiresAt,
      notes: input.notes,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    await this.storageService.upsertApiPoolAccount(account);
    return account;
  }

  async importApiPoolCredentials(input: ImportApiPoolCredentialsInput) {
    const rawKeys = input.rawKeys
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
    const now = new Date().toISOString();

    for (let index = 0; index < rawKeys.length; index += 1) {
      await this.storageService.upsertApiPoolCredential({
        id: `pool_credential_${Date.now()}_${index}`,
        accountId: input.accountId,
        provider: input.provider,
        label: `${input.provider.toLowerCase()}_${index + 1}`,
        baseUrl: input.baseUrl,
        model: input.model,
        apiKey: rawKeys[index],
        status: 'active',
        createdAt: now,
        updatedAt: now,
      });
    }

    return {
      imported: rawKeys.length,
      accountId: input.accountId,
      provider: input.provider,
    };
  }

  async updateDeviceUserEntitlement(
    input: UpdateDeviceUserEntitlementInput,
  ): Promise<DeviceUserProfileRecord> {
    const existing =
      await this.storageService.getDeviceUserProfile(input.deviceUserId);
    const now = new Date().toISOString();
    const profile: DeviceUserProfileRecord = {
      id: input.deviceUserId,
      displayName: existing?.displayName ?? `Device User ${input.deviceUserId}`,
      planCode: input.planCode,
      status: input.status ?? existing?.status ?? 'active',
      recoveryHint: input.recoveryHint ?? existing?.recoveryHint,
      entitlementExpiresAt:
        input.entitlementExpiresAt ?? existing?.entitlementExpiresAt,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    await this.storageService.upsertDeviceUserProfile(profile);
    return profile;
  }

  async updateOrderStatus(
    input: UpdateAdminOrderStatusInput,
  ): Promise<StablecoinPaymentOrderRecord> {
    const orders = await this.storageService.listAllStablecoinOrders();
    const existing = orders.find((order) => order.id === input.orderId);
    if (existing == null) {
      throw new Error(`Order ${input.orderId} was not found`);
    }

    const updated: StablecoinPaymentOrderRecord = {
      ...existing,
      status: input.status,
      reviewNote: input.reviewNote ?? existing.reviewNote,
      updatedAt: new Date().toISOString(),
    };
    await this.storageService.appendStablecoinOrder(updated);
    return updated;
  }

  async activateAvatarProfile(id: string): Promise<AvatarProfileRecord | undefined> {
    await this.storageService.activateAvatarProfile(id);
    return this.storageService.getActiveAvatarProfile();
  }

  async getTvHomeSnapshot(): Promise<AdminTvHomeSnapshot> {
    return {
      configs: await this.storageService.listTvHomeConfigs(),
      appCatalog: this.getTvHomeAppCatalog(),
    };
  }

  async getRadioSnapshot() {
    const [radio, queue] = await Promise.all([
      this.radioService.getAdminRadioSnapshot(),
      this.radioService.getQueueSnapshot(),
    ]);

    return {
      ...radio,
      queue,
    };
  }

  async refreshRadioHealth(params?: { limit?: number; countryCode?: string }) {
    return this.radioService.refreshStationHealth(params);
  }

  async upsertTvHomeConfig(
    input: UpsertTvHomeConfigInput,
  ): Promise<TvHomeConfigRecord> {
    const existing = input.id
      ? (await this.storageService.listTvHomeConfigs()).find(
          (item) => item.id === input.id,
        )
      : undefined;
    const now = new Date().toISOString();
    const featuredAppIds = input.featuredAppIds
      .map((item) => item.trim())
      .filter((item) => item.length > 0);

    const config: TvHomeConfigRecord = {
      id:
        existing?.id ??
        input.id ??
        `tv_home_${input.countryCode.toLowerCase()}_${(input.regionCode ?? 'global').toLowerCase()}`,
      countryCode: input.countryCode.trim().toUpperCase(),
      regionCode:
        input.regionCode == null || input.regionCode.trim().length === 0
          ? undefined
          : input.regionCode.trim().toUpperCase(),
      backgroundImageUrl:
        input.backgroundImageUrl == null || input.backgroundImageUrl.trim().length === 0
          ? undefined
          : input.backgroundImageUrl.trim(),
      featuredAppIds,
      status: input.status,
      version: (existing?.version ?? 0) + 1,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };

    await this.storageService.upsertTvHomeConfig(config);
    return config;
  }

  private paginate<T>(items: T[], pagination: PaginationInput): PaginatedResult<T> {
    const page = Math.max(1, pagination.page ?? 1);
    const pageSize = Math.max(1, Math.min(100, pagination.pageSize ?? 20));
    const start = (page - 1) * pageSize;

    return {
      items: items.slice(start, start + pageSize),
      total: items.length,
      page,
      pageSize,
    };
  }

  private buildTransportMetrics(logs: AssistantLogRecord[]): AdminTransportMetrics {
    return {
      directLease: logs.filter(
        (item) => item.transportMode === 'client_direct_provider_lease',
      ).length,
      serverFallback: logs.filter(
        (item) => item.transportMode === 'server_router_fallback',
      ).length,
      offlineLocal: logs.filter((item) => item.transportMode === 'offline_local')
        .length,
    };
  }

  private async buildApiPoolAccountItems(): Promise<AdminApiPoolAccountItem[]> {
    const [accounts, credentials, leases] = await Promise.all([
      this.storageService.listApiPoolAccounts(),
      this.storageService.listApiPoolCredentials(),
      this.storageService.listApiPoolLeases(),
    ]);
    const activeLeases = leases.filter(
      (item) =>
        item.status === 'active' &&
        new Date(item.expiresAt).getTime() > Date.now(),
    );

    return accounts.map((item) => {
      const accountCredentials = credentials.filter(
        (credential) =>
          credential.accountId === item.id && credential.status === 'active',
      );
      const accountLeases = activeLeases.filter(
        (lease) => lease.accountId === item.id,
      );
      const totalApis = accountCredentials.length;
      const inUseApis = accountLeases.length;
      const idleApis = Math.max(0, totalApis - inUseApis);
      const utilizationRate =
        totalApis === 0 ? 0 : Math.round((inUseApis / totalApis) * 100);

      return {
        ...item,
        totalApis,
        inUseApis,
        idleApis,
        utilizationRate,
      };
    });
  }

  private getTvHomeAppCatalog(): AdminTvHomeCatalogItem[] {
    return [
      {
        appId: 'youtube',
        displayName: 'YouTube',
        packageName: 'com.google.android.youtube.tv',
        supportTier: 'full',
        supportedActions: ['open_app', 'search', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'netflix',
        displayName: 'Netflix',
        packageName: 'com.netflix.ninja',
        supportTier: 'basic',
        supportedActions: ['open_app', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'prime_video',
        displayName: 'Prime Video',
        packageName: 'com.amazon.amazonvideo.livingroom',
        supportTier: 'basic',
        supportedActions: ['open_app', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'disney_plus',
        displayName: 'Disney+',
        packageName: 'com.disney.disneyplus',
        supportTier: 'basic',
        supportedActions: ['open_app', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'plex',
        displayName: 'Plex',
        packageName: 'com.plexapp.android',
        supportTier: 'basic',
        supportedActions: ['open_app', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'spotify',
        displayName: 'Spotify',
        packageName: 'com.spotify.tv.android',
        supportTier: 'full',
        supportedActions: ['open_app', 'search', 'play', 'pause', 'resume', 'dpad'],
      },
      {
        appId: 'vlc',
        displayName: 'VLC',
        packageName: 'org.videolan.vlc',
        supportTier: 'full',
        supportedActions: ['open_app', 'search', 'play', 'pause', 'resume', 'dpad'],
      },
    ];
  }
}
