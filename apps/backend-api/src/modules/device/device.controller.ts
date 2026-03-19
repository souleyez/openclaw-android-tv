import { Body, Controller, Get, Headers, Post } from '@nestjs/common';
import { AuthService } from '../auth/auth.service';
import { DeviceService } from './device.service';

class RegisterDeviceDto {
  deviceUuid!: string;
  deviceName!: string;
  androidVersion!: string;
  isAndroidTv!: boolean;
}

class CreateDeviceShareDto {
  sharedAccountId!: string;
  deviceUuid!: string;
  bindingScope!: 'api_key' | 'household';
}

class AuthorizeControlDto {
  bindingScope!: 'api_key' | 'household';
  appId!: string;
  action!: string;
}

@Controller('devices')
export class DeviceController {
  constructor(
    private readonly deviceService: DeviceService,
    private readonly authService: AuthService,
  ) {}

  @Get()
  async listDevices(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      items: await this.deviceService.listDevices(account.id),
    };
  }

  @Get('token-usage')
  async listTokenUsage(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      items: await this.deviceService.listTokenUsageEvents(account.id),
      summary: await this.deviceService.getTokenUsageSummary(account.id),
    };
  }

  @Post('register')
  async registerDevice(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: RegisterDeviceDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.deviceService.registerDevice(account.id, body);
  }

  @Get('shares')
  async listShareBindings(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      ownerAccountId: account.id,
      scopeSummary: await this.deviceService.getShareScopeSummary(account.id),
      policy: this.deviceService.getSharePolicyRules(),
      items: await this.deviceService.listShareBindings(account.id),
    };
  }

  @Get('share-policy')
  async getSharePolicy() {
    return {
      items: this.deviceService.getSharePolicyRules(),
    };
  }

  @Post('authorize-control')
  async authorizeControl(@Body() body: AuthorizeControlDto) {
    return this.deviceService.authorizeControlAction({
      bindingScope: body.bindingScope,
      appId: body.appId,
      action: body.action,
    });
  }

  @Post('shares')
  async createShareBinding(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: CreateDeviceShareDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.deviceService.createShareBinding({
      ownerAccountId: account.id,
      sharedAccountId: body.sharedAccountId,
      deviceUuid: body.deviceUuid,
      bindingScope: body.bindingScope,
    });
  }
}
