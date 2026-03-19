import { Injectable } from '@nestjs/common';

import {
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

@Injectable()
export class OtaService {
  constructor(private readonly storageService: StorageService) {}

  async getManifest(params: {
    accountId: string;
    deviceUuid: string;
    currentVersionCode: number;
  }): Promise<OtaManifestResult> {
    const releases = await this.storageService.listOtaReleases();
    const release = releases
      .filter(
        (item) =>
          (item.rolloutStatus === 'rolling' || item.rolloutStatus === 'completed') &&
          item.versionCode > params.currentVersionCode,
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
}
