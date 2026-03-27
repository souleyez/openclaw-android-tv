import AsyncStorage from '@react-native-async-storage/async-storage';
import { getLocales } from 'expo-localization';
import { Platform } from 'react-native';

import { API_BASE_URL } from './radioApi';

const STORAGE_KEY = 'radio-app-model-lease';
const DEFAULT_PROVIDER = 'minimax';
const DEFAULT_LOCALE = 'zh-CN';
const DEFAULT_DEVICE_USER_ID = 'user_demo';
const DEFAULT_DEVICE_UUID = `${Platform.OS}-radio-client`;

export type ModelLeasePayload = {
  leaseId: string;
  provider: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  expiresAt: string;
  leaseMode: string;
  maxConcurrency: number;
};

export type BootstrapSessionPayload = {
  sessionMode: 'local_first';
  provider: string;
  locale: string;
  lease: ModelLeasePayload | null;
  modelAccess: {
    connectAtBootOnly: boolean;
    renewWhenExpired: boolean;
    releaseOnShutdownBestEffort: boolean;
  };
  localDecisionPolicy: {
    defaultRoute: string;
    keepHabitsOnClient: boolean;
    keepStationRankingOnClient: boolean;
    radioCommands: string[];
    chatFallback: string;
    leaseTtlMinutes: number;
    renewWindowSeconds: number;
  };
  radioPolicy: {
    switchStrategy: string[];
    avoidBackendForStationSwitch: boolean;
    usePublicInternetStations: boolean;
  };
};

function getDeviceUuid(value?: string) {
  return value ?? DEFAULT_DEVICE_UUID;
}

function getProvider(value?: string) {
  return value ?? DEFAULT_PROVIDER;
}

function getLocale() {
  return getLocales()[0]?.languageTag ?? DEFAULT_LOCALE;
}

function createLocalFirstSession(params: {
  provider: string;
  locale: string;
  lease?: ModelLeasePayload | null;
}): BootstrapSessionPayload {
  return {
    sessionMode: 'local_first',
    provider: params.provider,
    locale: params.locale,
    lease: params.lease ?? null,
    modelAccess: {
      connectAtBootOnly: true,
      renewWhenExpired: true,
      releaseOnShutdownBestEffort: true,
    },
    localDecisionPolicy: {
      defaultRoute: 'client_local_rules',
      keepHabitsOnClient: true,
      keepStationRankingOnClient: true,
      radioCommands: ['next', 'play_music', 'play_news', 'ban_current'],
      chatFallback: 'direct_provider_with_temporary_lease',
      leaseTtlMinutes: 5,
      renewWindowSeconds: 45,
    },
    radioPolicy: {
      switchStrategy: [
        'blocked_station_filter',
        'same_region_bias',
        'listening_duration_bias',
        'genre_preference_bias',
      ],
      avoidBackendForStationSwitch: true,
      usePublicInternetStations: true,
    },
  };
}

async function cacheModelSession(session: BootstrapSessionPayload) {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export async function bootstrapModelSession(params?: {
  deviceUuid?: string;
  provider?: string;
}) {
  const locale = getLocale();
  const payload = {
    deviceUuid: getDeviceUuid(params?.deviceUuid),
    provider: getProvider(params?.provider),
    locale,
  };

  const response = await fetch(`${API_BASE_URL}/router/bootstrap-session`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-device-user-id': DEFAULT_DEVICE_USER_ID,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Failed to bootstrap model session: ${response.status}`);
  }

  const result = (await response.json()) as BootstrapSessionPayload;
  await cacheModelSession(result);
  return result;
}

export async function requestModelLease(params?: {
  deviceUuid?: string;
  provider?: string;
}) {
  const provider = getProvider(params?.provider);
  const payload = {
    deviceUuid: getDeviceUuid(params?.deviceUuid),
    provider,
  };

  const response = await fetch(`${API_BASE_URL}/router/provider-lease`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-device-user-id': DEFAULT_DEVICE_USER_ID,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Failed to request model lease: ${response.status}`);
  }

  const lease = (await response.json()) as ModelLeasePayload | null | {
    leaseId: null;
    denied?: boolean;
  };

  if (lease && 'leaseId' in lease && typeof lease.leaseId === 'string') {
    const cached = await getCachedModelSession();
    const locale = cached?.locale ?? getLocale();
    const session = cached ?? createLocalFirstSession({ provider, locale });

    const next = {
      ...session,
      lease,
      provider,
    };
    await cacheModelSession(next);
    return next;
  }

  return null;
}

export async function releaseModelLease(leaseId: string) {
  const response = await fetch(`${API_BASE_URL}/router/provider-lease/release`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-device-user-id': DEFAULT_DEVICE_USER_ID,
    },
    body: JSON.stringify({ leaseId }),
  });

  if (!response.ok) {
    throw new Error(`Failed to release model lease: ${response.status}`);
  }

  const cached = await getCachedModelSession();
  if (cached != null) {
    await cacheModelSession({
      ...cached,
      lease: null,
    });
  }

  return response.json();
}

export async function getCachedModelSession() {
  const raw = await AsyncStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  const parsed = JSON.parse(raw) as BootstrapSessionPayload;
  if (parsed.lease == null) {
    return parsed;
  }

  if (new Date(parsed.lease.expiresAt).getTime() <= Date.now()) {
    await AsyncStorage.removeItem(STORAGE_KEY);
    return null;
  }

  return parsed;
}

export async function ensureActiveModelSession(params?: {
  deviceUuid?: string;
  provider?: string;
}) {
  const cached = await getCachedModelSession();
  if (cached?.lease != null) {
    return cached;
  }

  return bootstrapModelSession(params);
}

export async function clearCachedModelSession() {
  await AsyncStorage.removeItem(STORAGE_KEY);
}

export function createFallbackModelSession(params?: {
  locale?: string;
  provider?: string;
}) {
  return createLocalFirstSession({
    provider: getProvider(params?.provider),
    locale: params?.locale ?? getLocale(),
  });
}
