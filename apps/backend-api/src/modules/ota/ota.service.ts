import { Injectable } from '@nestjs/common';

import {
  ClientConfigReleaseRecord,
  OtaDeviceReportRecord,
  OtaReleaseRecord,
  StorageService,
} from '../../shared/storage.service';

export interface OtaManifestResult {
  available: boolean;
  release?: OtaReleaseRecord;
  latestReport?: OtaDeviceReportRecord;
  policy?: {
    notificationMode: 'broadcast';
    downloadPolicy: 'idle_background';
    installPolicy: 'next_boot';
    reportPolicy: 'lazy';
    reportDelayMinutes: number;
  };
  directive?: {
    shouldDownloadWhenIdle: boolean;
    shouldStageForNextBoot: boolean;
    shouldReportLazily: boolean;
  };
}

export interface ClientConfigManifestResult {
  available: boolean;
  release?: ClientConfigReleaseRecord;
  payload?: Record<string, unknown>;
  policy?: {
    notificationMode: 'broadcast';
    fetchPolicy: 'idle_background';
    applyPolicy: 'idle_apply' | 'next_boot';
  };
}

export interface UpdateBootstrapResult {
  checkedAt: string;
  ota: OtaManifestResult;
  config: ClientConfigManifestResult;
}

export interface ReportOtaStateInput {
  accountId: string;
  deviceUuid: string;
  releaseId: string;
  currentVersionCode: number;
  targetVersionCode: number;
  status: OtaDeviceReportRecord['status'];
  progressPercent?: number;
  note?: string;
}

type ScopeMatchInput = {
  targetScope?: string;
  countryCode?: string;
  regionCode?: string;
  platform?: string;
  preferredMode?: string;
};

@Injectable()
export class OtaService {
  constructor(private readonly storageService: StorageService) {}

  async getManifest(params: {
    accountId: string;
    deviceUuid: string;
    currentVersionCode: number;
    countryCode?: string;
    regionCode?: string;
    platform?: string;
    preferredMode?: string;
  }): Promise<OtaManifestResult> {
    const releases = await this.storageService.listOtaReleases();
    const release = releases
      .filter((item) => item.rolloutStatus === 'rolling' || item.rolloutStatus === 'completed')
      .filter((item) => item.versionCode > params.currentVersionCode)
      .filter((item) =>
        this.matchesTargetScope({
          targetScope: item.targetScope,
          countryCode: params.countryCode,
          regionCode: params.regionCode,
          platform: params.platform,
          preferredMode: params.preferredMode,
        }),
      )
      .sort((left, right) => right.versionCode - left.versionCode)[0];

    if (release == null) {
      return { available: false };
    }

    const latestReport = (await this.storageService.listOtaDeviceReports(params.deviceUuid)).find(
      (item) => item.releaseId === release.id,
    );

    return {
      available: true,
      release,
      latestReport,
      policy: {
        notificationMode: release.notificationMode,
        downloadPolicy: release.downloadPolicy,
        installPolicy: release.installPolicy,
        reportPolicy: release.reportPolicy,
        reportDelayMinutes: release.reportDelayMinutes,
      },
      directive: {
        shouldDownloadWhenIdle: true,
        shouldStageForNextBoot: true,
        shouldReportLazily: true,
      },
    };
  }

  async getUpdateBootstrap(params: {
    accountId: string;
    deviceUuid: string;
    currentVersionCode: number;
    currentConfigVersion: number;
    countryCode?: string;
    regionCode?: string;
    platform?: string;
    preferredMode?: string;
  }): Promise<UpdateBootstrapResult> {
    const [ota, configReleases] = await Promise.all([
      this.getManifest(params),
      this.storageService.listClientConfigReleases(),
    ]);

    const matchedConfig = configReleases
      .filter((item) => item.rolloutStatus === 'rolling' || item.rolloutStatus === 'completed')
      .filter((item) => item.versionCode > params.currentConfigVersion)
      .filter((item) =>
        this.matchesTargetScope({
          targetScope: item.targetScope,
          countryCode: params.countryCode,
          regionCode: params.regionCode,
          platform: params.platform,
          preferredMode: params.preferredMode,
        }),
      )
      .sort((left, right) => right.versionCode - left.versionCode)[0];

    return {
      checkedAt: new Date().toISOString(),
      ota,
      config:
        matchedConfig == null
          ? { available: false }
          : {
              available: true,
              release: matchedConfig,
              payload: this.parsePayloadJson(matchedConfig),
              policy: {
                notificationMode: matchedConfig.notificationMode,
                fetchPolicy: matchedConfig.fetchPolicy,
                applyPolicy: matchedConfig.applyPolicy,
              },
            },
    };
  }

  async reportState(input: ReportOtaStateInput): Promise<OtaDeviceReportRecord> {
    const now = new Date().toISOString();
    const report: OtaDeviceReportRecord = {
      id: `ota_report_${input.releaseId}_${input.deviceUuid}`,
      releaseId: input.releaseId,
      accountId: input.accountId,
      deviceUuid: input.deviceUuid,
      currentVersionCode: input.currentVersionCode,
      targetVersionCode: input.targetVersionCode,
      status: input.status,
      progressPercent: input.progressPercent ?? 0,
      note: input.note,
      reportedAt: now,
      updatedAt: now,
    };

    await this.storageService.upsertOtaDeviceReport(report);
    return report;
  }

  private parsePayloadJson(release: ClientConfigReleaseRecord): Record<string, unknown> {
    try {
      const parsed = JSON.parse(release.payloadJson) as unknown;
      if (parsed != null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // ignore malformed payloads and keep a safe empty object
    }

    return {};
  }

  private matchesTargetScope(input: ScopeMatchInput): boolean {
    const rawScope = (input.targetScope ?? '').trim();
    if (!rawScope) {
      return true;
    }

    const tokens = rawScope
      .split(/[^a-zA-Z0-9]+/)
      .map((item) => item.trim().toLowerCase())
      .filter((item) => item.length > 0);

    if (
      tokens.length === 0 ||
      tokens.includes('all') ||
      tokens.includes('global') ||
      tokens.includes('default')
    ) {
      return true;
    }

    const country = (input.countryCode ?? '').trim().toLowerCase();
    const region = (input.regionCode ?? '').trim().toLowerCase();
    const platform = (input.platform ?? '').trim().toLowerCase();
    const preferredMode = (input.preferredMode ?? '').trim().toLowerCase();

    const hasCountryOrRegionTokens = tokens.some(
      (item) =>
        item.length === 2 ||
        ['beijing', 'guangdong', 'shanghai', 'zhejiang', 'japan', 'tokyo'].includes(item),
    );
    const hasPlatformTokens = tokens.some((item) =>
      ['web', 'android', 'ios', 'radio', 'app', 'radioapp', 'radio-app'].includes(item),
    );
    const hasModeTokens = tokens.some((item) => ['music', 'news', 'any'].includes(item));
    const isRadioAppScope =
      tokens.includes('radioapp') ||
      tokens.includes('radio-app') ||
      (tokens.includes('radio') && tokens.includes('app'));

    const regionMatch =
      !hasCountryOrRegionTokens ||
      (!!country && tokens.includes(country)) ||
      (!!region && tokens.includes(region));
    const platformMatch =
      !hasPlatformTokens ||
      (!!platform &&
        (tokens.includes(platform) ||
          (platform === 'web' && isRadioAppScope) ||
          (platform === 'android' && isRadioAppScope) ||
          (platform === 'ios' && isRadioAppScope)));
    const modeMatch =
      !hasModeTokens ||
      (!!preferredMode && (tokens.includes(preferredMode) || tokens.includes('any')));

    return regionMatch && platformMatch && modeMatch;
  }
}
