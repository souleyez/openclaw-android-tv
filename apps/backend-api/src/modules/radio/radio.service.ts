import {
  Injectable,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { extname, resolve } from 'node:path';

import {
  RadioBroadcastRecord,
  RadioStationRecord,
  StorageService,
} from '../../shared/storage.service';
import { RadioTaskQueueService } from './radio-task-queue.service';

type CreateBroadcastParams = {
  accountId: string;
  stationId?: string;
  title?: string;
  sourceKind: RadioBroadcastRecord['sourceKind'];
  textTranscript?: string;
  durationMs?: number;
  audioBuffer?: Buffer;
  originalFilename?: string;
};

type CreateAiBroadcastParams = {
  accountId: string;
  stationId?: string;
  text: string;
  locale?: string;
};

type EnsureRegionalStationsInput = {
  countryCode?: string;
  regionCode?: string;
  preferredMode?: 'music' | 'news' | 'any';
  preferredLanguage?: string;
};

type MiniMaxTtsResponse = {
  data?: {
    audio?: string;
  };
  extra_info?: {
    audio_length?: number;
  };
  base_resp?: {
    status_code?: number;
    status_msg?: string;
  };
};

type RadioBrowserStation = {
  stationuuid?: string;
  name?: string;
  countrycode?: string;
  state?: string;
  language?: string;
  tags?: string;
  url_resolved?: string;
  url?: string;
  homepage?: string;
  favicon?: string;
  codec?: string;
  bitrate?: number;
  votes?: number;
  clickcount?: number;
  lastcheckok?: number;
};

type StationHealthProbeResult = {
  ok: boolean;
  error?: string;
  checkedAt: string;
};

const RADIO_BROWSER_API_ROOT = 'https://de1.api.radio-browser.info/json/stations/bycountrycodeexact';
const REGION_MINIMUM_COUNTRY_STATIONS = 8;
const REGION_IMPORT_BATCH = 4;
const REGION_IMPORT_COOLDOWN_MS = 1000 * 60 * 30;
const STATION_HEALTH_CACHE_TTL_MS = 1000 * 60 * 20;
const MUSIC_STATION_FAILURE_THRESHOLD = 2;
const DEFAULT_STATION_FALLBACK_STREAMS: Record<string, string[]> = {
  'station-shanghai-dynamic-101': ['https://lhttp.qingting.fm/live/274/64k.mp3'],
  'station-beijing-music': ['https://brtv-radiolive.rbc.cn/alive/fm974.m3u8'],
  'station-zhejiang-music': ['http://ali-vl.cztv.com/channels/lantian/fm968/360p.m3u8'],
  'station-cityfm-music': ['https://lhttp-hw.qtfm.cn/live/20500153/64k.mp3'],
  'station-liangguang-music': ['https://lhttp-hw.qtfm.cn/live/20500149/64k.mp3'],
  'station-asiafm-cantonese': ['http://yyt.asiafm.net:8000/asiafm'],
  'station-cnr-music': ['https://satellitepull.cnr.cn/live/wxyyzs/playlist.m3u8'],
};

@Injectable()
export class RadioService {
  private readonly uploadsDir = resolve(process.cwd(), 'data', 'uploads', 'radio');
  private readonly lastImportAttemptByCountry = new Map<string, number>();
  private readonly stationHealthCache = new Map<string, StationHealthProbeResult>();

  constructor(
    private readonly storageService: StorageService,
    private readonly radioTaskQueueService: RadioTaskQueueService,
  ) {
    mkdirSync(this.uploadsDir, { recursive: true });
  }

  async listStations(params?: EnsureRegionalStationsInput) {
    const stations = await this.storageService.listRadioStations();
    this.scheduleDefaultMusicHealthRefresh(stations);
    const augmentation = await this.scheduleRegionalStationsImport(params, stations);
    return {
      items: this.rankStationsForRegion(stations, params),
      filters: this.buildFilters(stations),
      augmentation,
    };
  }

  async getStation(id: string) {
    const station = await this.storageService.getRadioStation(id);
    if (station == null) {
      throw new NotFoundException('Radio station was not found');
    }

    return station;
  }

  async listBroadcasts(params?: { stationId?: string; limit?: number }) {
    return this.storageService.listRadioBroadcasts(params);
  }

  async getBroadcast(id: string) {
    const record = await this.storageService.getRadioBroadcast(id);
    if (record == null) {
      throw new NotFoundException('Radio broadcast was not found');
    }

    return record;
  }

  async createBroadcast(params: CreateBroadcastParams): Promise<RadioBroadcastRecord> {
    const now = new Date().toISOString();
    const id = `radio_broadcast_${randomUUID()}`;
    const audioPath =
      params.audioBuffer == null
        ? undefined
        : this.persistAudioFile(id, params.audioBuffer, params.originalFilename);

    const record: RadioBroadcastRecord = {
      id,
      stationId: params.stationId,
      accountId: params.accountId,
      title:
        params.title?.trim() ||
        (params.sourceKind === 'ai' ? '声临 AI 广播' : '用户语音广播'),
      sourceKind: params.sourceKind,
      textTranscript: params.textTranscript?.trim() || undefined,
      audioPath,
      durationMs: Math.max(0, params.durationMs ?? 0),
      status: audioPath ? 'ready' : 'uploaded',
      createdAt: now,
      updatedAt: now,
    };

    await this.storageService.createRadioBroadcast(record);
    return record;
  }

  async createAiBroadcast(params: CreateAiBroadcastParams): Promise<RadioBroadcastRecord> {
    const trimmedText = params.text.trim().slice(0, 500);
    if (!trimmedText) {
      throw new ServiceUnavailableException('AI reply text is empty');
    }

    const record = await this.createBroadcast({
      accountId: params.accountId,
      stationId: params.stationId,
      sourceKind: 'ai',
      title: '声临 AI 广播',
      textTranscript: trimmedText,
      durationMs: 0,
    });

    this.radioTaskQueueService.enqueue(`ai-tts:${record.id}`, async () => {
      try {
        const synthesized = await this.synthesizeMiniMaxSpeech(trimmedText, params.locale);
        const audioPath = this.persistAudioFile(
          record.id,
          synthesized.audioBuffer,
          `ai-reply.${synthesized.extension}`,
        );

        await this.storageService.createRadioBroadcast({
          ...record,
          audioPath,
          durationMs: synthesized.durationMs,
          status: 'ready',
          updatedAt: new Date().toISOString(),
        });
      } catch {
        await this.storageService.createRadioBroadcast({
          ...record,
          status: 'failed',
          updatedAt: new Date().toISOString(),
        });
      }
    });

    return record;
  }

  async getAdminRadioSnapshot() {
    const stations = await this.storageService.listRadioStations();
    const byCountry = new Map<string, number>();

    for (const station of stations) {
      byCountry.set(station.country, (byCountry.get(station.country) ?? 0) + 1);
    }

    const sortedCountries = [...byCountry.entries()]
      .sort((left, right) => right[1] - left[1])
      .slice(0, 12)
      .map(([country, count]) => ({ country, count }));

    return {
      metrics: {
        totalStations: stations.length,
        activeCountries: byCountry.size,
        importedStations: stations.filter((item) =>
          (item.legalNotes ?? '').includes('Imported from public radio directory'),
        ).length,
        healthyStations: stations.filter((item) => item.lastHealthStatus === 'healthy').length,
        degradedStations: stations.filter((item) => item.lastHealthStatus === 'degraded').length,
        fallbackReadyStations: stations.filter((item) => this.hasFallbackStreams(item.id)).length,
      },
      byCountry: sortedCountries,
      latestChecked: [...stations]
        .sort((left, right) => right.lastCheckedAt.localeCompare(left.lastCheckedAt))
        .slice(0, 12),
    };
  }

  async refreshStationHealth(params?: { limit?: number; countryCode?: string }) {
    const limit = Math.max(1, Math.min(12, params?.limit ?? 6));
    const countryCode = (params?.countryCode ?? '').trim().toUpperCase();
    const stations = (await this.storageService.listRadioStations())
      .filter((station) => !countryCode || station.country.toUpperCase() === countryCode)
      .sort((left, right) => left.lastCheckedAt.localeCompare(right.lastCheckedAt))
      .slice(0, limit);

    let checked = 0;
    let reactivated = 0;
    let deactivated = 0;

    for (const station of stations) {
      const probe = await this.checkStationStreamCached(station.streamUrl, true);
      const nextFailureCount = probe.ok ? 0 : (station.consecutiveFailures ?? 0) + 1;
      const fallbackStreamUrl =
        probe.ok ? undefined : await this.resolveFallbackStream(station);
      const nextIsActive = this.shouldKeepStationActive(
        station,
        probe.ok || fallbackStreamUrl != null,
        nextFailureCount,
      );
      const updated: RadioStationRecord = {
        ...station,
        streamUrl: fallbackStreamUrl ?? station.streamUrl,
        isActive: nextIsActive,
        lastCheckedAt: new Date().toISOString(),
        lastHealthStatus: probe.ok || fallbackStreamUrl != null ? 'healthy' : 'degraded',
        consecutiveFailures: probe.ok || fallbackStreamUrl != null ? 0 : nextFailureCount,
        lastHealthError:
          probe.ok || fallbackStreamUrl != null
            ? undefined
            : probe.error ?? 'stream_unreachable',
        updatedAt: new Date().toISOString(),
      };

      if (!station.isActive && updated.isActive) {
        reactivated += 1;
      }
      if (station.isActive && !updated.isActive) {
        deactivated += 1;
      }

      await this.storageService.upsertRadioStation(updated);
      checked += 1;
    }

    return {
      checked,
      reactivated,
      deactivated,
      countryCode: countryCode || null,
    };
  }

  async getQueueSnapshot() {
    return this.radioTaskQueueService.getSnapshot();
  }

  private async scheduleRegionalStationsImport(
    params?: EnsureRegionalStationsInput,
    existingStations?: RadioStationRecord[],
  ) {
    const normalizedCountry = (params?.countryCode ?? '').trim().toUpperCase();
    if (normalizedCountry.length !== 2) {
      return {
        requestedCountryCode: null,
        requestedRegionCode: null,
        imported: 0,
        skipped: true,
      };
    }

    if (!this.isCountryAllowedForImport(normalizedCountry)) {
      return {
        requestedCountryCode: normalizedCountry,
        requestedRegionCode: params?.regionCode?.trim().toUpperCase() || null,
        imported: 0,
        skipped: true,
        reason: 'country_not_whitelisted',
      };
    }

    const remainingCooldownMs = this.getRemainingImportCooldownMs(normalizedCountry);
    if (remainingCooldownMs > 0) {
      return {
        requestedCountryCode: normalizedCountry,
        requestedRegionCode: params?.regionCode?.trim().toUpperCase() || null,
        imported: 0,
        skipped: true,
        reason: 'cooldown_active',
        cooldownMs: remainingCooldownMs,
      };
    }

    const stations = existingStations ?? (await this.storageService.listRadioStations());
    const countryStations = stations.filter(
      (station) => station.country.toUpperCase() === normalizedCountry,
    );

    if (countryStations.length >= REGION_MINIMUM_COUNTRY_STATIONS) {
      return {
        requestedCountryCode: normalizedCountry,
        requestedRegionCode: params?.regionCode?.trim().toUpperCase() || null,
        imported: 0,
        skipped: true,
        reason: 'country_threshold_reached',
      };
    }

    this.lastImportAttemptByCountry.set(normalizedCountry, Date.now());
    const queued = this.radioTaskQueueService.enqueue(
      `regional-import:${normalizedCountry}`,
      async () => {
        const latestStations = await this.storageService.listRadioStations();
        const latestCountryStations = latestStations.filter(
          (station) => station.country.toUpperCase() === normalizedCountry,
        );
        if (latestCountryStations.length >= REGION_MINIMUM_COUNTRY_STATIONS) {
          return;
        }

        await this.importStationsFromRadioBrowser({
          existingStations: latestStations,
          countryCode: normalizedCountry,
          regionCode: params?.regionCode?.trim().toUpperCase(),
          limit: REGION_IMPORT_BATCH,
        });
      },
    );

    return {
      requestedCountryCode: normalizedCountry,
      requestedRegionCode: params?.regionCode?.trim().toUpperCase() || null,
      imported: 0,
      skipped: !queued,
      queued,
      reason: queued ? 'queued_background_import' : 'already_queued',
    };
  }

  private persistAudioFile(id: string, buffer: Buffer, originalFilename?: string) {
    const fileExtension = this.normalizeExtension(originalFilename);
    const fileName = `${id}${fileExtension}`;
    const filePath = resolve(this.uploadsDir, fileName);
    writeFileSync(filePath, buffer);
    return `radio/${fileName}`;
  }

  private normalizeExtension(originalFilename?: string) {
    const value = extname(originalFilename ?? '').toLowerCase();
    if (
      value === '.mp3' ||
      value === '.m4a' ||
      value === '.wav' ||
      value === '.webm' ||
      value === '.aac'
    ) {
      return value;
    }

    return '.m4a';
  }

  private buildFilters(stations: RadioStationRecord[]) {
    return {
      countries: [...new Set(stations.map((station) => station.country))].sort(),
      languages: [...new Set(stations.map((station) => station.language))].sort(),
      bands: [...new Set(stations.map((station) => station.bandLabel))].sort(),
      genres: [...new Set(stations.map((station) => station.genre))].sort(),
    };
  }

  private rankStationsForRegion(
    stations: RadioStationRecord[],
    params?: EnsureRegionalStationsInput,
  ) {
    const normalizedCountry = (params?.countryCode ?? '').trim().toUpperCase();
    const normalizedRegion = (params?.regionCode ?? '').trim().toUpperCase();
    const preferredMode = params?.preferredMode ?? 'any';
    const preferredLanguage = (params?.preferredLanguage ?? '').trim().toLowerCase();

    return [...stations].sort((left, right) => {
      const leftScore = this.scoreStationForRegion(
        left,
        normalizedCountry,
        normalizedRegion,
        preferredMode,
        preferredLanguage,
      );
      const rightScore = this.scoreStationForRegion(
        right,
        normalizedCountry,
        normalizedRegion,
        preferredMode,
        preferredLanguage,
      );
      if (leftScore !== rightScore) {
        return rightScore - leftScore;
      }

      if (left.sortOrder !== right.sortOrder) {
        return left.sortOrder - right.sortOrder;
      }

      return left.name.localeCompare(right.name, 'zh-CN');
    });
  }

  private scoreStationForRegion(
    station: RadioStationRecord,
    normalizedCountry: string,
    normalizedRegion: string,
    preferredMode: 'music' | 'news' | 'any',
    preferredLanguage: string,
  ) {
    let score = 0;
    const language = station.language.toLowerCase();
    const genre = station.genre.toLowerCase();
    const region = (station.region ?? '').trim().toUpperCase();

    if (normalizedCountry && station.country.toUpperCase() === normalizedCountry) {
      score += 100;
    }
    if (normalizedRegion && region.includes(normalizedRegion)) {
      score += 30;
    }
    if (preferredMode === 'music' && genre.includes('music')) {
      score += 45;
      if (normalizedRegion && region.includes(normalizedRegion)) {
        score += 25;
      }
      if (language.includes('粤')) {
        score += 10;
      }
    }
    if (preferredMode === 'news' && (genre.includes('news') || genre.includes('talk'))) {
      score += 35;
    }
    if (normalizedRegion && this.isSameLanguageRegionMatch(station, normalizedRegion)) {
      score += 18;
    }
    if (preferredLanguage && this.matchesPreferredLanguage(station, preferredLanguage)) {
      score += 20;
    }
    if (station.lastHealthStatus === 'healthy') {
      score += 8;
    }
    if (station.lastHealthStatus === 'degraded') {
      score -= 40;
    }
    return score;
  }

  private async importStationsFromRadioBrowser(params: {
    existingStations: RadioStationRecord[];
    countryCode: string;
    regionCode?: string;
    limit: number;
  }) {
    const existingIds = new Set(params.existingStations.map((station) => station.id));
    const existingUrls = new Set(
      params.existingStations.map((station) => this.normalizeStreamUrl(station.streamUrl)),
    );
    const existingNameKeys = new Set(
      params.existingStations.map((station) =>
        this.buildStationIdentityKey(params.countryCode, station.name),
      ),
    );
    const existingHomepageKeys = new Set(
      params.existingStations
        .map((station) => this.normalizeHomepage(station.homepageUrl))
        .filter((value) => value.length > 0),
    );
    const nextSortBase =
      params.existingStations.reduce((max, station) => Math.max(max, station.sortOrder), 0) +
      10;

    let response: Response;
    try {
      response = await fetch(
        `${RADIO_BROWSER_API_ROOT}/${params.countryCode}?hidebroken=true&limit=24&order=clickcount&reverse=true`,
        {
          headers: {
            'User-Agent': 'ShenglinRadio/1.0',
          },
          signal: AbortSignal.timeout(15000),
        },
      );
    } catch {
      return 0;
    }

    if (!response.ok) {
      return 0;
    }

    const candidates = (await response.json()) as RadioBrowserStation[];
    const seenCandidateUrls = new Set<string>();
    const seenCandidateNames = new Set<string>();
    const seenCandidateHomepages = new Set<string>();
    const selected = candidates
      .filter((item) => (item.lastcheckok ?? 1) === 1)
      .filter((item) => !!(item.url_resolved ?? item.url))
      .filter((item) => !!item.name?.trim())
      .filter((item) => {
        const normalizedUrl = this.normalizeStreamUrl(item.url_resolved ?? item.url ?? '');
        if (!normalizedUrl || existingUrls.has(normalizedUrl) || seenCandidateUrls.has(normalizedUrl)) {
          return false;
        }

        const normalizedName = this.buildStationIdentityKey(params.countryCode, item.name);
        if (!normalizedName || existingNameKeys.has(normalizedName) || seenCandidateNames.has(normalizedName)) {
          return false;
        }

        const normalizedHomepage = this.normalizeHomepage(item.homepage);
        if (
          normalizedHomepage &&
          (existingHomepageKeys.has(normalizedHomepage) ||
            seenCandidateHomepages.has(normalizedHomepage))
        ) {
          return false;
        }

        seenCandidateUrls.add(normalizedUrl);
        seenCandidateNames.add(normalizedName);
        if (normalizedHomepage) {
          seenCandidateHomepages.add(normalizedHomepage);
        }

        return true;
      })
      .sort((left, right) => {
        const regionBias = this.regionBias(right, params.regionCode) - this.regionBias(left, params.regionCode);
        if (regionBias !== 0) {
          return regionBias;
        }
        return (Number(right.clickcount ?? 0) + Number(right.votes ?? 0)) - (Number(left.clickcount ?? 0) + Number(left.votes ?? 0));
      })
      .slice(0, params.limit);

    let imported = 0;
    for (let index = 0; index < selected.length; index += 1) {
      const station = this.mapRadioBrowserStation(
        selected[index],
        params.countryCode,
        nextSortBase + index,
      );

      const normalizedStreamUrl = this.normalizeStreamUrl(station.streamUrl);
      const normalizedNameKey = this.buildStationIdentityKey(params.countryCode, station.name);
      const normalizedHomepage = this.normalizeHomepage(station.homepageUrl);

      if (
        existingIds.has(station.id) ||
        existingUrls.has(normalizedStreamUrl) ||
        existingNameKeys.has(normalizedNameKey) ||
        (normalizedHomepage && existingHomepageKeys.has(normalizedHomepage))
      ) {
        continue;
      }

      await this.storageService.upsertRadioStation(station);
      existingIds.add(station.id);
      existingUrls.add(normalizedStreamUrl);
      existingNameKeys.add(normalizedNameKey);
      if (normalizedHomepage) {
        existingHomepageKeys.add(normalizedHomepage);
      }
      imported += 1;
    }

    return imported;
  }

  private mapRadioBrowserStation(
    station: RadioBrowserStation,
    countryCode: string,
    sortOrder: number,
  ): RadioStationRecord {
    const now = new Date().toISOString();
    const stationUuid = (station.stationuuid ?? randomUUID()).replace(/[^a-z0-9-]+/gi, '');
    const name = station.name?.trim() || `Radio ${countryCode}`;
    const language = station.language?.split(',')[0]?.trim() || 'Unknown';
    const genre = station.tags?.split(',').slice(0, 2).join(' ').trim() || 'Public Radio';
    const city = station.state?.trim() || countryCode;
    const streamUrl = (station.url_resolved ?? station.url ?? '').trim();

    return {
      id: `station_${countryCode.toLowerCase()}_${stationUuid.slice(0, 12).toLowerCase()}`,
      name,
      country: countryCode,
      region: station.state?.trim() || undefined,
      city,
      language,
      bandLabel: 'WEB',
      genre,
      streamUrl,
      homepageUrl: station.homepage?.trim() || undefined,
      logoUrl: station.favicon?.trim() || undefined,
      legalNotes: 'Imported from public radio directory',
      isActive: true,
      sortOrder,
      lastCheckedAt: now,
      createdAt: now,
      updatedAt: now,
    };
  }

  private regionBias(station: RadioBrowserStation, regionCode?: string) {
    if (!regionCode) {
      return 0;
    }

    const state = station.state?.toUpperCase() ?? '';
    return state.includes(regionCode.toUpperCase()) ? 1 : 0;
  }

  private scheduleDefaultMusicHealthRefresh(stations: RadioStationRecord[]) {
    const candidates = stations.filter((station) => this.shouldProbeDefaultMusicStation(station));

    for (const station of candidates) {
      const cacheKey = this.normalizeStreamUrl(station.streamUrl);
      const cached = this.stationHealthCache.get(cacheKey);
      if (cached && Date.now() - new Date(cached.checkedAt).getTime() < STATION_HEALTH_CACHE_TTL_MS) {
        continue;
      }

      this.radioTaskQueueService.enqueue(`station-health:${station.id}`, async () => {
        const latest = await this.storageService.getRadioStation(station.id);
        if (latest == null || !latest.isActive) {
          return;
        }

        const latestCache = this.stationHealthCache.get(cacheKey);
        if (
          latestCache &&
          Date.now() - new Date(latestCache.checkedAt).getTime() < STATION_HEALTH_CACHE_TTL_MS
        ) {
          return;
        }

        const probe = await this.checkStationStreamCached(latest.streamUrl, true);
        const nextFailureCount = probe.ok ? 0 : (latest.consecutiveFailures ?? 0) + 1;
        const fallbackStreamUrl =
          probe.ok ? undefined : await this.resolveFallbackStream(latest);
        const updated: RadioStationRecord = {
          ...latest,
          streamUrl: fallbackStreamUrl ?? latest.streamUrl,
          isActive: this.shouldKeepStationActive(
            latest,
            probe.ok || fallbackStreamUrl != null,
            nextFailureCount,
          ),
          lastCheckedAt: probe.checkedAt,
          lastHealthStatus: probe.ok || fallbackStreamUrl != null ? 'healthy' : 'degraded',
          consecutiveFailures: probe.ok || fallbackStreamUrl != null ? 0 : nextFailureCount,
          lastHealthError:
            probe.ok || fallbackStreamUrl != null
              ? undefined
              : probe.error ?? 'stream_unreachable',
          updatedAt: new Date().toISOString(),
        };

        await this.storageService.upsertRadioStation(updated);
      });
    }
  }

  private async checkStationStreamCached(
    url: string,
    forceRefresh = false,
  ): Promise<StationHealthProbeResult> {
    const cacheKey = this.normalizeStreamUrl(url);
    const cached = this.stationHealthCache.get(cacheKey);
    if (
      !forceRefresh &&
      cached &&
      Date.now() - new Date(cached.checkedAt).getTime() < STATION_HEALTH_CACHE_TTL_MS
    ) {
      return cached;
    }

    try {
      const ok = await this.checkStationStream(url);
      const result: StationHealthProbeResult = {
        ok,
        error: ok ? undefined : 'stream_unreachable',
        checkedAt: new Date().toISOString(),
      };
      this.stationHealthCache.set(cacheKey, result);
      return result;
    } catch {
      const result: StationHealthProbeResult = {
        ok: false,
        error: 'probe_failed',
        checkedAt: new Date().toISOString(),
      };
      this.stationHealthCache.set(cacheKey, result);
      return result;
    }
  }

  private shouldKeepStationActive(
    station: RadioStationRecord,
    probeOk: boolean,
    nextFailureCount: number,
  ) {
    if (probeOk) {
      return true;
    }

    if (this.isImportedStation(station)) {
      return false;
    }

    if (this.shouldProbeDefaultMusicStation(station)) {
      return nextFailureCount < MUSIC_STATION_FAILURE_THRESHOLD;
    }

    return station.isActive;
  }

  private shouldProbeDefaultMusicStation(station: RadioStationRecord) {
    return !this.isImportedStation(station) && station.genre.toLowerCase().includes('music');
  }

  private hasFallbackStreams(stationId: string) {
    return (DEFAULT_STATION_FALLBACK_STREAMS[stationId] ?? []).length > 0;
  }

  private async resolveFallbackStream(station: RadioStationRecord): Promise<string | undefined> {
    const candidates = (DEFAULT_STATION_FALLBACK_STREAMS[station.id] ?? []).filter(
      (item) => this.normalizeStreamUrl(item) !== this.normalizeStreamUrl(station.streamUrl),
    );

    for (const candidate of candidates) {
      const probe = await this.checkStationStreamCached(candidate, true);
      if (probe.ok) {
        return candidate;
      }
    }

    return undefined;
  }

  private isSameLanguageRegionMatch(station: RadioStationRecord, normalizedRegion: string) {
    const language = station.language.toLowerCase();
    if (
      ['GUANGDONG', 'FOSHAN', 'GUANGZHOU', 'HONG KONG'].includes(normalizedRegion) &&
      language.includes('粤')
    ) {
      return true;
    }
    if (
      ['SHANGHAI', 'BEIJING', 'ZHEJIANG', 'HANGZHOU', 'NATIONAL'].includes(normalizedRegion) &&
      language.includes('中')
    ) {
      return true;
    }
    return false;
  }

  private matchesPreferredLanguage(station: RadioStationRecord, preferredLanguage: string) {
    const language = station.language.toLowerCase();
    if (preferredLanguage.startsWith('zh')) {
      return language.includes('中');
    }
    if (preferredLanguage.startsWith('yue') || preferredLanguage.includes('hant-hk')) {
      return language.includes('粤');
    }
    return false;
  }

  private isCountryAllowedForImport(countryCode: string) {
    const whitelist = (process.env.RADIO_IMPORT_COUNTRY_WHITELIST ?? '')
      .split(',')
      .map((item) => item.trim().toUpperCase())
      .filter((item) => item.length === 2);

    if (whitelist.length === 0) {
      return true;
    }

    return whitelist.includes(countryCode);
  }

  private getRemainingImportCooldownMs(countryCode: string) {
    const lastAttempt = this.lastImportAttemptByCountry.get(countryCode);
    if (lastAttempt == null) {
      return 0;
    }

    return Math.max(0, REGION_IMPORT_COOLDOWN_MS - (Date.now() - lastAttempt));
  }

  private normalizeStreamUrl(url: string) {
    const trimmed = url.trim();
    if (!trimmed) {
      return '';
    }

    try {
      const parsed = new URL(trimmed);
      parsed.hash = '';
      parsed.search = '';
      return parsed.toString().replace(/\/$/, '').toLowerCase();
    } catch {
      return trimmed.replace(/\?.*$/, '').replace(/#.*$/, '').replace(/\/$/, '').toLowerCase();
    }
  }

  private normalizeHomepage(url?: string) {
    if (!url) {
      return '';
    }

    return this.normalizeStreamUrl(url);
  }

  private buildStationIdentityKey(countryCode: string, name?: string) {
    const normalizedName = (name ?? '')
      .toLowerCase()
      .replace(/[\s\-_]+/g, '')
      .replace(/[^\p{L}\p{N}]+/gu, '');

    if (!normalizedName) {
      return '';
    }

    return `${countryCode}:${normalizedName}`;
  }

  private async checkStationStream(url: string) {
    const methods: Array<'HEAD' | 'GET'> = ['HEAD', 'GET'];

    for (const method of methods) {
      try {
        const response = await fetch(url, {
          method,
          redirect: 'follow',
          headers:
            method === 'GET'
              ? {
                  Range: 'bytes=0-0',
                  'User-Agent': 'ShenglinRadio/1.0',
                }
              : {
                  'User-Agent': 'ShenglinRadio/1.0',
                },
          signal: AbortSignal.timeout(8000),
        });

        if (response.ok) {
          return true;
        }
      } catch {
        continue;
      }
    }

    return false;
  }

  private isImportedStation(station: RadioStationRecord) {
    return (station.legalNotes ?? '').includes('Imported from public radio directory');
  }

  private async synthesizeMiniMaxSpeech(text: string, locale?: string) {
    const apiKey = process.env.MINIMAX_API_KEY?.trim();
    if (!apiKey) {
      throw new ServiceUnavailableException('MINIMAX_API_KEY is not configured');
    }

    const baseUrl = (
      process.env.MINIMAX_BASE_URL?.trim() || 'https://api.minimax.io/v1'
    ).replace(/\/$/, '');
    const speed = this.resolveSpeechSpeed();

    let response: Response;
    try {
      response = await fetch(`${baseUrl}/t2a_v2`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: process.env.MINIMAX_SPEECH_MODEL?.trim() || 'speech-2.8-hd',
          text,
          stream: false,
          language_boost: this.resolveLanguageBoost(locale),
          output_format: 'hex',
          voice_setting: {
            voice_id:
              process.env.MINIMAX_SPEECH_VOICE_ID?.trim() || 'male-qn-qingse',
            speed,
            vol: 1,
            pitch: 0,
          },
          audio_setting: {
            sample_rate: 32000,
            bitrate: 128000,
            format: 'mp3',
            channel: 1,
          },
        }),
        signal: AbortSignal.timeout(30000),
      });
    } catch (error) {
      throw new ServiceUnavailableException(
        error instanceof Error
          ? `MiniMax TTS request failed: ${error.message}`
          : 'MiniMax TTS request failed',
      );
    }

    if (!response.ok) {
      throw new ServiceUnavailableException(
        `MiniMax TTS failed with status ${response.status}`,
      );
    }

    const payload = (await response.json()) as MiniMaxTtsResponse;
    if ((payload.base_resp?.status_code ?? 0) !== 0) {
      throw new ServiceUnavailableException(
        `MiniMax TTS rejected request: ${
          payload.base_resp?.status_msg ?? 'unknown error'
        }`,
      );
    }

    const audioHex = payload.data?.audio?.trim();
    if (!audioHex) {
      throw new ServiceUnavailableException('MiniMax TTS returned empty audio');
    }

    return {
      audioBuffer: Buffer.from(audioHex, 'hex'),
      durationMs: Math.max(1000, Number(payload.extra_info?.audio_length ?? 0)),
      extension: 'mp3',
    };
  }

  private resolveLanguageBoost(locale?: string) {
    if (locale?.toLowerCase().startsWith('zh')) {
      return 'Chinese';
    }

    return 'auto';
  }

  private resolveSpeechSpeed() {
    const parsed = Number.parseFloat(process.env.MINIMAX_SPEECH_SPEED ?? '1');
    if (Number.isNaN(parsed)) {
      return 1;
    }

    return Math.min(2, Math.max(0.5, parsed));
  }
}
