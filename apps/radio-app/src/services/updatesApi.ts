import AsyncStorage from '@react-native-async-storage/async-storage';

import { API_BASE_URL, buildClientRegionHeaders } from './radioApi';

const APP_VERSION_CODE = 1;
const CONFIG_VERSION_KEY = 'radio-app-config-version';
const DEVICE_UUID_KEY = 'radio-app-device-uuid';
const OTA_QUEUE_PREFIX = 'radio-app-ota-queued:';
const PENDING_CONFIG_KEY = 'radio-app-pending-config';

type OtaBootstrapRelease = {
  id: string;
  versionName: string;
  versionCode: number;
  artifactUrl?: string;
  releaseNotes?: string;
};

type ConfigBootstrapRelease = {
  id: string;
  versionName: string;
  versionCode: number;
  applyPolicy: 'idle_apply' | 'next_boot';
  releaseNotes?: string;
};

export type UpdateBootstrapPayload = {
  checkedAt: string;
  ota: {
    available: boolean;
    release?: OtaBootstrapRelease;
    latestReport?: {
      status: string;
    };
  };
  config: {
    available: boolean;
    release?: ConfigBootstrapRelease;
    payload?: Record<string, unknown>;
  };
};

export type PendingConfigRelease = {
  releaseId: string;
  versionCode: number;
  payload: Record<string, unknown>;
};

export async function fetchUpdateBootstrap(preferredMode: 'music' | 'news' | 'any') {
  const [deviceUuid, currentConfigVersion] = await Promise.all([
    getOrCreateDeviceUuid(),
    getAppliedConfigVersion(),
  ]);
  const query = new URLSearchParams({
    deviceUuid,
    currentVersionCode: String(APP_VERSION_CODE),
    currentConfigVersion: String(currentConfigVersion),
  });

  const response = await fetch(`${API_BASE_URL}/ota/bootstrap?${query.toString()}`, {
    headers: {
      ...buildClientRegionHeaders(preferredMode),
      'x-device-user-id': 'user_demo',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch update bootstrap: ${response.status}`);
  }

  return (await response.json()) as UpdateBootstrapPayload;
}

export async function reportQueuedOtaRelease(params: {
  releaseId: string;
  targetVersionCode: number;
}) {
  const [deviceUuid, alreadyQueued] = await Promise.all([
    getOrCreateDeviceUuid(),
    AsyncStorage.getItem(`${OTA_QUEUE_PREFIX}${params.releaseId}`),
  ]);
  if (alreadyQueued === '1') {
    return;
  }

  await fetch(`${API_BASE_URL}/ota/report`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-device-user-id': 'user_demo',
    },
    body: JSON.stringify({
      deviceUuid,
      releaseId: params.releaseId,
      currentVersionCode: APP_VERSION_CODE,
      targetVersionCode: params.targetVersionCode,
      status: 'queued',
      progressPercent: 0,
      note: 'queued_for_idle_download',
    }),
  });

  await AsyncStorage.setItem(`${OTA_QUEUE_PREFIX}${params.releaseId}`, '1');
}

export async function setAppliedConfigVersion(versionCode: number) {
  await AsyncStorage.setItem(CONFIG_VERSION_KEY, String(versionCode));
}

export async function stashPendingConfigRelease(params: {
  releaseId: string;
  versionCode: number;
  payload: Record<string, unknown>;
}) {
  await AsyncStorage.setItem(PENDING_CONFIG_KEY, JSON.stringify(params));
}

export async function consumePendingConfigRelease(): Promise<PendingConfigRelease | null> {
  const raw = await AsyncStorage.getItem(PENDING_CONFIG_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as PendingConfigRelease;
    if (
      parsed &&
      typeof parsed === 'object' &&
      typeof parsed.releaseId === 'string' &&
      typeof parsed.versionCode === 'number' &&
      parsed.payload &&
      typeof parsed.payload === 'object' &&
      !Array.isArray(parsed.payload)
    ) {
      await AsyncStorage.removeItem(PENDING_CONFIG_KEY);
      return parsed;
    }
  } catch {
    // fall through and clear malformed data
  }

  await AsyncStorage.removeItem(PENDING_CONFIG_KEY);
  return null;
}

async function getAppliedConfigVersion() {
  const stored = await AsyncStorage.getItem(CONFIG_VERSION_KEY);
  return Number.parseInt(stored ?? '0', 10) || 0;
}

async function getOrCreateDeviceUuid() {
  const existing = await AsyncStorage.getItem(DEVICE_UUID_KEY);
  if (existing?.trim()) {
    return existing;
  }

  const next = `radio_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
  await AsyncStorage.setItem(DEVICE_UUID_KEY, next);
  return next;
}
