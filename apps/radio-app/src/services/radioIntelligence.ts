import { getLocales } from 'expo-localization';

import type { Station } from '../data/mockRadio';

export type VoiceIntent =
  | { kind: 'next' }
  | { kind: 'play_music' }
  | { kind: 'play_news' }
  | { kind: 'ban_current' }
  | { kind: 'unknown' };

export type ListenerProfile = {
  country?: string;
  region?: string;
  preferredGenres: string[];
  blockedStationIds: string[];
  listeningDurations: Record<string, number>;
  stationSkipCounts: Record<string, number>;
  recentStationIds: string[];
  preferredMode: 'music' | 'news' | 'any';
  localHour: number;
};

const NEXT_PATTERNS = ['\u4e0b\u4e00\u4e2a', '\u6362\u4e00\u4e2a', '\u6362\u53f0', 'next', 'skip'];
const MUSIC_PATTERNS = ['\u542c\u6b4c', '\u97f3\u4e50', 'music', 'song'];
const NEWS_PATTERNS = ['\u542c\u65b0\u95fb', '\u65b0\u95fb', 'news'];
const BAN_PATTERNS = ['\u4ee5\u540e\u4e0d\u542c\u8fd9\u4e2a', '\u4e0d\u542c\u8fd9\u4e2a', '\u62c9\u9ed1\u8fd9\u4e2a', 'ban this', 'dont play this'];

export function detectVoiceIntent(input: string): VoiceIntent {
  const normalized = input.trim().toLowerCase();
  if (!normalized) {
    return { kind: 'unknown' };
  }

  if (hasAny(normalized, NEXT_PATTERNS)) {
    return { kind: 'next' };
  }
  if (hasAny(normalized, MUSIC_PATTERNS)) {
    return { kind: 'play_music' };
  }
  if (hasAny(normalized, NEWS_PATTERNS)) {
    return { kind: 'play_news' };
  }
  if (hasAny(normalized, BAN_PATTERNS)) {
    return { kind: 'ban_current' };
  }

  return { kind: 'unknown' };
}

export function resolveListenerProfile(
  listeningDurations: Record<string, number>,
  blockedStationIds: string[],
  stations: Station[],
  stationSkipCounts: Record<string, number>,
  recentStationIds: string[],
  preferredMode: 'music' | 'news' | 'any',
): ListenerProfile {
  const locale = getLocales()[0];
  const rankedGenres = derivePreferredGenres(listeningDurations, stations);
  return {
    country: locale?.regionCode?.toUpperCase(),
    region: locale?.regionCode?.toUpperCase(),
    preferredGenres: rankedGenres,
    blockedStationIds,
    listeningDurations,
    stationSkipCounts,
    recentStationIds,
    preferredMode,
    localHour: new Date().getHours(),
  };
}

export function recommendStartupStation(params: { stations: Station[]; profile: ListenerProfile }) {
  return recommendNextStation({
    stations: params.stations,
    profile: params.profile,
    requestedMode: inferAmbientMode(params.profile),
  });
}

export function inferAmbientMode(profile: ListenerProfile): 'music' | 'news' | 'any' {
  if (profile.preferredMode !== 'any') {
    return profile.preferredMode;
  }
  if (profile.localHour >= 6 && profile.localHour <= 10) {
    return 'news';
  }
  if (profile.localHour >= 20 && profile.localHour <= 23) {
    return 'music';
  }
  return 'any';
}

export function buildBootPrompt(params: { profile: ListenerProfile; station?: Station }) {
  const preferred = inferAmbientMode(params.profile);
  const modeHint = preferred === 'news' ? '\u65b0\u95fb' : preferred === 'music' ? '\u97f3\u4e50' : '\u65b0\u95fb\u6216\u97f3\u4e50';
  const stationHint = params.station ? `\u5f53\u524d\u504f\u5411 ${params.station.genre}.` : '';
  return [
    'You are a concise radio host.',
    'Greet the user in Chinese.',
    `Ask whether they want ${modeHint}.`,
    stationHint,
  ]
    .filter(Boolean)
    .join(' ');
}

export function buildFollowupReplyPrompt(params: { profile: ListenerProfile; station?: Station }) {
  const preferred = inferAmbientMode(params.profile);
  const preferenceHint = preferred === 'news' ? 'Prefer a short news-oriented response.' : preferred === 'music' ? 'Prefer a short music-oriented response.' : 'Prefer a short neutral response.';
  const stationHint = params.station ? `Current station genre: ${params.station.genre}.` : '';
  return [
    'The user just sent a short voice message.',
    'Reply with one short spoken sentence in Chinese.',
    preferenceHint,
    stationHint,
  ]
    .filter(Boolean)
    .join(' ');
}

export function recommendNextStation(params: {
  currentStationId?: string;
  stations: Station[];
  profile: ListenerProfile;
  requestedMode?: 'music' | 'news' | 'any';
}) {
  const requestedMode = params.requestedMode ?? 'any';
  const currentStationId = params.currentStationId;

  const candidates = params.stations
    .filter((station) => !params.profile.blockedStationIds.includes(station.id))
    .filter((station) => station.id !== currentStationId)
    .map((station) => ({
      station,
      score: scoreStation(station, params.profile, requestedMode),
    }))
    .sort((left, right) => right.score - left.score);

  return candidates[0]?.station;
}

function scoreStation(station: Station, profile: ListenerProfile, requestedMode: 'music' | 'news' | 'any') {
  const genre = station.genre.toLowerCase();
  let score = 0;

  if (requestedMode === 'music' && genre.includes('music')) {
    score += 90;
  }
  if (requestedMode === 'news' && (genre.includes('news') || genre.includes('talk'))) {
    score += 90;
  }
  if (requestedMode !== 'any' && score === 0) {
    score -= 40;
  }
  if (profile.country && station.country.toUpperCase() === profile.country) {
    score += 55;
  }
  if (profile.preferredMode === 'music' && genre.includes('music')) {
    score += 26;
  }
  if (profile.preferredMode === 'news' && (genre.includes('news') || genre.includes('talk'))) {
    score += 26;
  }

  const listenedMs = profile.listeningDurations[station.id] ?? 0;
  score += Math.min(45, Math.round(listenedMs / 60000) * 6);

  if (profile.preferredGenres.some((preferred) => genre.includes(preferred))) {
    score += 28;
  }

  const skipCount = profile.stationSkipCounts[station.id] ?? 0;
  score -= Math.min(48, skipCount * 12);

  const recencyIndex = profile.recentStationIds.indexOf(station.id);
  if (recencyIndex >= 0) {
    score -= Math.max(8, 32 - recencyIndex * 6);
  }

  if (profile.localHour >= 6 && profile.localHour <= 10 && (genre.includes('news') || genre.includes('talk'))) {
    score += 16;
  }
  if (profile.localHour >= 20 && profile.localHour <= 23 && genre.includes('music')) {
    score += 12;
  }

  return score;
}

function derivePreferredGenres(listeningDurations: Record<string, number>, stations: Station[]) {
  const genreScores = new Map<string, number>();
  for (const station of stations) {
    const duration = listeningDurations[station.id] ?? 0;
    if (duration <= 0) {
      continue;
    }
    for (const keyword of extractGenreKeywords(station.genre)) {
      genreScores.set(keyword, (genreScores.get(keyword) ?? 0) + duration);
    }
  }

  return [...genreScores.entries()]
    .sort((left, right) => right[1] - left[1])
    .slice(0, 3)
    .map(([genre]) => genre);
}

function extractGenreKeywords(genre: string) {
  return genre
    .toLowerCase()
    .split(/[\s,/]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function hasAny(input: string, patterns: string[]) {
  return patterns.some((pattern) => input.includes(pattern));
}
