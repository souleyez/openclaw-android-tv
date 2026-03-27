import { Platform } from 'react-native';
import { getLocales } from 'expo-localization';

import type { BroadcastItem, Station } from '../data/mockRadio';

type RadioStationsResponse = {
  items: Station[];
};

type RadioBroadcastApiItem = {
  id: string;
  title: string;
  sourceKind: 'user' | 'ai' | 'system';
  durationMs: number;
  createdAt: string;
  status: 'uploaded' | 'ready' | 'failed';
  textTranscript?: string;
  audioUrl?: string | null;
};

type RadioBroadcastsResponse = {
  items: RadioBroadcastApiItem[];
};

export const API_BASE_URL =
  Platform.OS === 'android' ? 'http://10.0.2.2:3000/api' : 'http://127.0.0.1:3000/api';

export async function fetchStations(preferredMode: 'music' | 'news' | 'any' = 'any') {
  const locale = getLocales()[0];
  const response = await fetch(`${API_BASE_URL}/radio/stations`, {
    headers: {
      'x-client-country': locale?.regionCode?.toUpperCase() ?? '',
      'x-client-region': locale?.regionCode?.toUpperCase() ?? '',
      'x-client-preferred-mode': preferredMode,
      'x-client-language': locale?.languageTag?.toLowerCase() ?? locale?.languageCode?.toLowerCase() ?? '',
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch stations: ${response.status}`);
  }

  const payload = (await response.json()) as RadioStationsResponse;
  return payload.items;
}

export async function fetchBroadcasts() {
  const response = await fetch(`${API_BASE_URL}/radio/broadcasts`);
  if (!response.ok) {
    throw new Error(`Failed to fetch broadcasts: ${response.status}`);
  }

  const payload = (await response.json()) as RadioBroadcastsResponse;
  return payload.items.map(mapBroadcastItem);
}

export async function fetchBroadcastById(id: string) {
  const response = await fetch(`${API_BASE_URL}/radio/broadcasts/${id}`, {
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch broadcast: ${response.status}`);
  }

  const payload = (await response.json()) as RadioBroadcastApiItem;
  return mapBroadcastItem(payload);
}

export async function uploadBroadcast(params: {
  audioUri: string;
  stationId?: string;
  title?: string;
  textTranscript?: string;
  durationMs?: number;
}) {
  const formData = new FormData();
  formData.append('stationId', params.stationId ?? '');
  formData.append('title', params.title ?? 'voice message');
  formData.append('sourceKind', 'user');
  formData.append('textTranscript', params.textTranscript ?? '');
  formData.append('durationMs', String(params.durationMs ?? 0));

  if (Platform.OS === 'web') {
    const response = await fetch(params.audioUri);
    const blob = await response.blob();
    formData.append('audio', blob, 'broadcast.webm');
  } else {
    formData.append(
      'audio',
      {
        uri: params.audioUri,
        name: 'broadcast.m4a',
        type: 'audio/m4a',
      } as unknown as Blob,
    );
  }

  const response = await fetch(`${API_BASE_URL}/radio/broadcasts/upload`, {
    method: 'POST',
    body: formData,
    headers: {
      'x-device-user-id': 'user_demo',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to upload broadcast: ${response.status}`);
  }

  return response.json();
}

export async function createAiBroadcast(params: {
  text: string;
  stationId?: string;
  locale?: string;
}) {
  const response = await fetch(`${API_BASE_URL}/radio/ai/respond`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-device-user-id': 'user_demo',
    },
    body: JSON.stringify({
      text: params.text,
      stationId: params.stationId,
      locale: params.locale,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to create AI broadcast: ${response.status}`);
  }

  const item = (await response.json()) as RadioBroadcastApiItem;
  return mapBroadcastItem(item);
}

function mapBroadcastItem(item: RadioBroadcastApiItem): BroadcastItem {
  return {
    id: item.id,
    title: item.title,
    sourceKind: item.sourceKind === 'system' ? 'ai' : item.sourceKind,
    durationMs: item.durationMs,
    createdLabel: formatRelativeTime(item.createdAt),
    audioUri: item.audioUrl ?? null,
    status: item.status,
  };
}

function formatRelativeTime(value: string) {
  const timestamp = new Date(value).getTime();
  const diffSeconds = Math.max(0, Math.round((Date.now() - timestamp) / 1000));

  if (diffSeconds < 60) {
    return 'just now';
  }

  if (diffSeconds < 3600) {
    return `${Math.floor(diffSeconds / 60)} min ago`;
  }

  if (diffSeconds < 86400) {
    return `${Math.floor(diffSeconds / 3600)} hr ago`;
  }

  return `${Math.floor(diffSeconds / 86400)} day ago`;
}
