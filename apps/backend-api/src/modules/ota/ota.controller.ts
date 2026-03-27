import { Body, Controller, Get, Headers, Post, Query } from '@nestjs/common';

import { AuthService } from '../auth/auth.service';
import { OtaService } from './ota.service';

class ReportOtaStateDto {
  deviceUuid!: string;
  releaseId!: string;
  currentVersionCode!: number;
  targetVersionCode!: number;
  status!:
    | 'announced'
    | 'queued'
    | 'downloading'
    | 'downloaded'
    | 'staged'
    | 'installing'
    | 'installed_pending_report'
    | 'reported'
    | 'failed';
  progressPercent?: number;
  note?: string;
}

@Controller('ota')
export class OtaController {
  constructor(
    private readonly otaService: OtaService,
    private readonly authService: AuthService,
  ) {}

  @Get('manifest')
  async getManifest(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-client-country') clientCountry?: string,
    @Headers('x-client-region') clientRegion?: string,
    @Headers('x-client-platform') clientPlatform?: string,
    @Headers('x-client-preferred-mode') preferredMode?: string,
    @Query('deviceUuid') deviceUuid?: string,
    @Query('currentVersionCode') currentVersionCode?: string,
  ) {
    const deviceUser = await this.authService.resolveDeviceUser({ deviceUserId });
    return this.otaService.getManifest({
      accountId: deviceUser.id,
      deviceUuid: deviceUuid?.trim() || 'unknown_device',
      currentVersionCode: Number.parseInt(currentVersionCode ?? '0', 10) || 0,
      countryCode: clientCountry,
      regionCode: clientRegion,
      platform: clientPlatform,
      preferredMode,
    });
  }

  @Get('bootstrap')
  async getBootstrap(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-client-country') clientCountry?: string,
    @Headers('x-client-region') clientRegion?: string,
    @Headers('x-client-platform') clientPlatform?: string,
    @Headers('x-client-preferred-mode') preferredMode?: string,
    @Query('deviceUuid') deviceUuid?: string,
    @Query('currentVersionCode') currentVersionCode?: string,
    @Query('currentConfigVersion') currentConfigVersion?: string,
  ) {
    const deviceUser = await this.authService.resolveDeviceUser({ deviceUserId });
    return this.otaService.getUpdateBootstrap({
      accountId: deviceUser.id,
      deviceUuid: deviceUuid?.trim() || 'unknown_device',
      currentVersionCode: Number.parseInt(currentVersionCode ?? '0', 10) || 0,
      currentConfigVersion: Number.parseInt(currentConfigVersion ?? '0', 10) || 0,
      countryCode: clientCountry,
      regionCode: clientRegion,
      platform: clientPlatform,
      preferredMode,
    });
  }

  @Post('report')
  async reportState(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Body() body?: ReportOtaStateDto,
  ) {
    const deviceUser = await this.authService.resolveDeviceUser({ deviceUserId });
    return this.otaService.reportState({
      accountId: deviceUser.id,
      deviceUuid: body?.deviceUuid ?? 'unknown_device',
      releaseId: body?.releaseId ?? 'unknown_release',
      currentVersionCode: Number(body?.currentVersionCode ?? 0),
      targetVersionCode: Number(body?.targetVersionCode ?? 0),
      status: body?.status ?? 'announced',
      progressPercent:
        body?.progressPercent == null ? undefined : Number(body.progressPercent),
      note: body?.note,
    });
  }
}
