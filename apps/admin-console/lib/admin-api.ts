export type ManagedClient = {
  id: string;
  label: string;
  platform: "web" | "android" | "ios";
  scope: string;
  status: "active";
};

export type AdminBroadcast = {
  id: string;
  title: string;
  sourceKind: "user" | "ai" | "system";
  status: "uploaded" | "ready" | "failed";
  stationId?: string;
  stationName?: string;
  accountId: string;
  createdAt: string;
  updatedAt: string;
};

export type DeviceUser = {
  id: string;
  displayName: string;
  planCode: string;
  status: string;
  recoveryHint?: string;
  entitlementExpiresAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type StablecoinOrder = {
  id: string;
  accountId: string;
  paymentMethod: string;
  stablecoinSymbol: string;
  chain: string;
  walletAddress: string;
  amountUsd: number;
  amountToken: number;
  status: string;
  txHash?: string;
  confirmations?: number;
  reviewNote?: string;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type EntitlementTransfer = {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  transferReason: string;
  paymentProofTxHash?: string;
  createdAt: string;
};

export type ApiPoolAccount = {
  id: string;
  provider: string;
  accountLabel: string;
  planLabel: string;
  status: string;
  renewsAt?: string;
  expiresAt?: string;
  notes?: string;
  totalApis?: number;
  inUseApis?: number;
  idleApis?: number;
  utilizationRate?: number;
  createdAt: string;
  updatedAt: string;
};

export type AssistantLog = {
  id: string;
  accountId?: string;
  kind: string;
  deviceId?: string;
  locale?: string;
  userText?: string;
  assistantText?: string;
  appId?: string;
  action?: string;
  mode?: string;
  transportMode?: string;
  modelProvider?: string;
  route?: string;
  tokenUsage?: number;
  bootstrapTokenRemaining?: number;
  createdAt: string;
};

export type AvatarProfile = {
  id: string;
  name: string;
  avatarLabel: string;
  gender: string;
  ageGroup: string;
  primaryColorHex: string;
  secondaryColorHex: string;
  accentColorHex: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type RadioStation = {
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
  lastHealthStatus?: "healthy" | "degraded" | "unknown";
  consecutiveFailures?: number;
  lastHealthError?: string;
  createdAt: string;
  updatedAt: string;
};

export type RadioSourceCandidate = {
  source: "radio_browser";
  externalId?: string;
  name: string;
  countryCode: string;
  regionCode?: string;
  city: string;
  language: string;
  genre: string;
  streamUrl: string;
  homepageUrl?: string;
  logoUrl?: string;
  popularityScore: number;
  alreadyExists: boolean;
  existingStationId?: string;
  existingStationName?: string;
};

export type AdminSummary = {
  counts: {
    deviceUsers: number;
    orders: number;
    transfers: number;
    apiPoolAccounts: number;
    logs: number;
    radioStations?: number;
    broadcasts?: number;
  };
  metrics?: {
    activeOrders: number;
    activeModelLeases?: number;
    activeLeaseUsers?: number;
    transport: {
      directLease: number;
      serverFallback: number;
      offlineLocal: number;
    };
  };
  deviceUsers: DeviceUser[];
  orders: StablecoinOrder[];
  transfers: EntitlementTransfer[];
  apiPoolAccounts: ApiPoolAccount[];
  logs: AssistantLog[];
  avatars: AvatarProfile[];
  managedClients: ManagedClient[];
  recentBroadcasts: AdminBroadcast[];
};

export type PaginatedResponse<T> = {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
};

export type WalletLedgerEntry = {
  id: string;
  accountId: string;
  entryType: "grant" | "topup" | "usage" | "adjustment";
  amount: number;
  currency: "token" | "usd";
  source: string;
  referenceId?: string;
  note?: string;
  createdAt: string;
};

export type AdminFinanceSnapshot = {
  metrics: {
    totalOrders: number;
    confirmedOrders: number;
    pendingRevenueUsd: number;
    settledRevenueUsd: number;
    tokenGranted: number;
    tokenUsed: number;
    apiPoolExpiring: number;
  };
  recentOrders: StablecoinOrder[];
  walletLedger: WalletLedgerEntry[];
  apiPoolAccounts: ApiPoolAccount[];
};

export type AdminRiskSnapshot = {
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
  expiringRights: DeviceUser[];
  failedOrders: StablecoinOrder[];
  reviewOrders: StablecoinOrder[];
  blockedEvents: AssistantLog[];
  expiringApiPoolAccounts: ApiPoolAccount[];
};

export type AdminOtaVersionRecord = {
  id: string;
  versionName: string;
  versionCode: number;
  releaseChannel: "stable" | "beta" | "internal";
  rolloutStatus: "draft" | "rolling" | "paused" | "completed" | "rolled_back";
  targetScope: string;
  rolloutPercent: number;
  deviceCount: number;
  installSuccessRate: number;
  notificationMode: "broadcast";
  downloadPolicy: "idle_background";
  installPolicy: "next_boot";
  reportPolicy: "lazy";
  reportDelayMinutes: number;
  artifactUrl?: string;
  releaseNotes?: string;
  createdAt: string;
  updatedAt: string;
};

export type AdminClientConfigReleaseRecord = {
  id: string;
  versionName: string;
  versionCode: number;
  targetScope: string;
  configKey: string;
  rolloutStatus: "draft" | "rolling" | "paused" | "completed" | "rolled_back";
  notificationMode: "broadcast";
  fetchPolicy: "idle_background";
  applyPolicy: "idle_apply" | "next_boot";
  releaseNotes?: string;
  createdAt: string;
  updatedAt: string;
};

export type AdminOtaSnapshot = {
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
    releaseChannel: "stable" | "beta" | "internal";
    rolloutStatus: "draft" | "rolling" | "paused" | "completed" | "rolled_back";
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
  configReleases: AdminClientConfigReleaseRecord[];
  rolloutNotes: string[];
};

export type TvHomeConfig = {
  id: string;
  countryCode: string;
  regionCode?: string;
  backgroundImageUrl?: string;
  featuredAppIds: string[];
  status: "active" | "draft";
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type TvHomeCatalogItem = {
  appId: string;
  displayName: string;
  packageName?: string;
  supportTier: "full" | "basic";
  supportedActions: string[];
};

export type AdminTvHomeSnapshot = {
  configs: TvHomeConfig[];
  appCatalog: TvHomeCatalogItem[];
};

export type AdminRadioSnapshot = {
  metrics: {
    totalStations: number;
    activeCountries: number;
    importedStations: number;
    healthyStations?: number;
    degradedStations?: number;
    fallbackReadyStations?: number;
  };
  byCountry: Array<{
    country: string;
    count: number;
  }>;
  latestChecked: RadioStation[];
  stations: RadioStation[];
  queue: {
    running: number;
    queued: number;
    concurrency: number;
  };
  recentBroadcasts: AdminBroadcast[];
};

export type RadioSourceSearchResult = {
  query: string;
  countryCode: string;
  items: RadioSourceCandidate[];
};

export type AdminSearchResult = {
  query: string;
  deviceUsers: DeviceUser[];
  orders: StablecoinOrder[];
  logs: AssistantLog[];
  transfers: EntitlementTransfer[];
};

export const adminNavItems = [
  { href: "/tv-home", label: "TV Home" },
  { href: "/overview", label: "总览" },
  { href: "/radio", label: "节目源" },
  { href: "/search", label: "全局搜索" },
  { href: "/finance", label: "财务" },
  { href: "/risk", label: "风险" },
  { href: "/ota", label: "OTA 与版本" },
  { href: "/device-users", label: "设备用户" },
  { href: "/api-pool", label: "模型 API 池" },
  { href: "/orders", label: "订单" },
  { href: "/logs", label: "日志与审计" },
] as const;

export async function fetchAdminSummary(): Promise<AdminSummary> {
  return fetchJson<AdminSummary>("/admin/summary", "Admin summary");
}

export async function fetchAdminDeviceUsers(
  filters: {
    q?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  } = {},
): Promise<PaginatedResponse<DeviceUser>> {
  const url = new URL(`${getAdminApiBaseUrl()}/admin/device-users`);
  appendQuery(url, filters);
  return fetchJsonFromUrl<PaginatedResponse<DeviceUser>>(url, "Admin device users");
}

export async function fetchAdminOrders(
  filters: {
    q?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  } = {},
): Promise<PaginatedResponse<StablecoinOrder>> {
  const url = new URL(`${getAdminApiBaseUrl()}/admin/orders`);
  appendQuery(url, filters);
  return fetchJsonFromUrl<PaginatedResponse<StablecoinOrder>>(url, "Admin orders");
}

export async function fetchAdminLogs(
  filters: {
    q?: string;
    mode?: string;
    transportMode?: string;
    page?: number;
    pageSize?: number;
  } = {},
): Promise<PaginatedResponse<AssistantLog>> {
  const url = new URL(`${getAdminApiBaseUrl()}/admin/logs`);
  appendQuery(url, filters);
  return fetchJsonFromUrl<PaginatedResponse<AssistantLog>>(url, "Admin logs");
}

export async function fetchAdminApiPoolAccounts(
  filters: {
    q?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  } = {},
): Promise<PaginatedResponse<ApiPoolAccount>> {
  const url = new URL(`${getAdminApiBaseUrl()}/admin/api-pool-accounts`);
  appendQuery(url, filters);
  return fetchJsonFromUrl<PaginatedResponse<ApiPoolAccount>>(url, "Admin api pool");
}

export async function fetchAdminFinanceSnapshot(): Promise<AdminFinanceSnapshot> {
  return fetchJson<AdminFinanceSnapshot>("/admin/finance", "Admin finance");
}

export async function fetchAdminRiskSnapshot(): Promise<AdminRiskSnapshot> {
  return fetchJson<AdminRiskSnapshot>("/admin/risk", "Admin risk");
}

export async function fetchAdminOtaSnapshot(): Promise<AdminOtaSnapshot> {
  return fetchJson<AdminOtaSnapshot>("/admin/ota", "Admin ota");
}

export async function fetchAdminTvHomeSnapshot(): Promise<AdminTvHomeSnapshot> {
  return fetchJson<AdminTvHomeSnapshot>("/admin/tv-home-configs", "Admin tv home");
}

export async function fetchAdminRadioSnapshot(): Promise<AdminRadioSnapshot> {
  return fetchJson<AdminRadioSnapshot>("/admin/radio", "Admin radio");
}

export async function fetchAdminRadioSourceSearch(filters: {
  q?: string;
  countryCode?: string;
  limit?: number;
}): Promise<RadioSourceSearchResult> {
  const query = filters.q?.trim() ?? "";
  const countryCode = filters.countryCode?.trim().toUpperCase() ?? "";

  if (!query && !countryCode) {
    return {
      query: "",
      countryCode: "",
      items: [],
    };
  }

  const url = new URL(`${getAdminApiBaseUrl()}/admin/radio/source-search`);
  appendQuery(url, {
    q: query,
    countryCode,
    limit: filters.limit,
  });
  return fetchJsonFromUrl<RadioSourceSearchResult>(url, "Admin radio source search");
}

export async function fetchAdminSearch(query: string): Promise<AdminSearchResult> {
  const normalizedQuery = query.trim();

  if (!normalizedQuery) {
    return {
      query: "",
      deviceUsers: [],
      orders: [],
      logs: [],
      transfers: [],
    };
  }

  const [deviceUsers, orders, logs, summary] = await Promise.all([
    fetchAdminDeviceUsers({ q: normalizedQuery }),
    fetchAdminOrders({ q: normalizedQuery }),
    fetchAdminLogs({ q: normalizedQuery }),
    fetchAdminSummary(),
  ]);

  const transfers = summary.transfers.filter((item) => {
    const haystack = [
      item.id,
      item.fromAccountId,
      item.toAccountId,
      item.transferReason,
      item.paymentProofTxHash,
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();

    return haystack.includes(normalizedQuery.toLowerCase());
  });

  return {
    query: normalizedQuery,
    deviceUsers: deviceUsers.items,
    orders: orders.items,
    logs: logs.items,
    transfers,
  };
}

export function getAdminApiBaseUrl(): string {
  const configured = process.env.ADMIN_API_BASE_URL?.trim().replace(/\/$/, "");
  return configured && configured.length > 0
    ? configured
    : "http://127.0.0.1:3000/api";
}

export function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatDaysLeft(value?: string): string {
  if (!value) {
    return "-";
  }

  const target = new Date(value).getTime();
  if (Number.isNaN(target)) {
    return "-";
  }

  const diffMs = target - Date.now();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  return `${diffDays} 天`;
}

export function formatNumber(value?: number): string {
  if (value === undefined || value === null) {
    return "-";
  }

  return new Intl.NumberFormat("zh-CN").format(value);
}

export function formatTransportMode(value?: string): string {
  switch (value) {
    case "client_direct_provider_lease":
      return "客户端直连租约";
    case "server_router_fallback":
      return "后端路由回退";
    case "offline_local":
      return "本地离线路由";
    default:
      return value ?? "-";
  }
}

export function formatGenericStatus(value?: string): string {
  switch (value) {
    case "active":
      return "正常";
    case "disabled":
      return "停用";
    case "expiring":
      return "即将到期";
    case "expired":
      return "已过期";
    case "paused":
      return "已暂停";
    case "pending":
      return "待处理";
    case "confirming":
      return "确认中";
    case "reviewing":
      return "复核中";
    case "confirmed":
      return "已确认";
    case "failed":
      return "失败";
    case "draft":
      return "草稿";
    case "rolling":
      return "灰度中";
    case "completed":
      return "已完成";
    case "rolled_back":
      return "已回滚";
    case "uploaded":
      return "已上传";
    case "ready":
      return "可播放";
    default:
      return value ?? "-";
  }
}

function appendQuery(
  url: URL,
  filters: Record<string, string | number | undefined>,
): void {
  for (const [key, rawValue] of Object.entries(filters)) {
    if (rawValue === undefined || rawValue === null || rawValue === "") {
      continue;
    }
    url.searchParams.set(key, String(rawValue));
  }
}

async function fetchJson<T>(path: string, label: string): Promise<T> {
  const response = await fetch(`${getAdminApiBaseUrl()}${path}`, {
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`${label} request failed with ${response.status}`);
  }

  return (await response.json()) as T;
}

async function fetchJsonFromUrl<T>(url: URL, label: string): Promise<T> {
  const response = await fetch(url.toString(), {
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`${label} request failed with ${response.status}`);
  }

  return (await response.json()) as T;
}
