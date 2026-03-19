import { Body, Controller, Get, Headers, Post } from '@nestjs/common';
import { AuthService } from '../auth/auth.service';
import { ModelRouterService } from './model-router.service';

class IntentRequestDto {
  text!: string;
  locale!: string;
  deviceId?: string;
}

class ControlBlockLogRequestDto {
  locale!: string;
  userText!: string;
  assistantText!: string;
  appId!: string;
  action!: string;
  route!: string;
  deviceId?: string;
}

class DirectTransportLogRequestDto {
  locale!: string;
  userText!: string;
  assistantText!: string;
  appId!: string;
  action!: string;
  route!: string;
  modelProvider!: string;
  transportMode!: string;
  deviceId?: string;
}

class LeaseProviderRequestDto {
  provider!: string;
  deviceUuid!: string;
}

class ReleaseProviderLeaseDto {
  leaseId!: string;
}

@Controller('router')
export class ModelRouterController {
  constructor(
    private readonly modelRouterService: ModelRouterService,
    private readonly authService: AuthService,
  ) {}

  @Get('status')
  async getStatus() {
    return this.modelRouterService.getStatus();
  }

  @Get('logs')
  async getLogs(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      items: await this.modelRouterService.getRecentLogs(account.id),
    };
  }

  @Post('intent')
  async resolveIntent(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: IntentRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.modelRouterService.resolveVoiceTurn({
      ...body,
      accountId: account.id,
    });
  }

  @Post('voice-turn')
  async resolveVoiceTurn(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: IntentRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.modelRouterService.resolveVoiceTurn({
      ...body,
      accountId: account.id,
    });
  }

  @Post('control-block')
  async logControlBlock(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: ControlBlockLogRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.modelRouterService.logControlBlock({
      ...body,
      accountId: account.id,
    });
  }

  @Post('direct-log')
  async logDirectTransport(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: DirectTransportLogRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.modelRouterService.logDirectTransport({
      ...body,
      accountId: account.id,
    });
  }

  @Post('provider-lease')
  async leaseProviderCredential(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: LeaseProviderRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });

    return this.modelRouterService.leaseProviderCredential({
      deviceUserId: account.id,
      deviceUuid: body.deviceUuid,
      provider: body.provider,
    });
  }

  @Post('provider-lease/release')
  async releaseProviderCredential(@Body() body: ReleaseProviderLeaseDto) {
    return this.modelRouterService.releaseProviderCredential(body.leaseId);
  }
}
