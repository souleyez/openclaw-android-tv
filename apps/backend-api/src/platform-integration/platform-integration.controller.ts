import { Body, Controller, Get, Headers, HttpCode, Post } from '@nestjs/common';

import { PLATFORM_SECRET_HEADER } from './platform-integration.constants';
import {
  PlatformBroadcastRequest,
  PlatformIntegrationService,
} from './platform-integration.service';

@Controller('internal/platform')
export class PlatformIntegrationController {
  constructor(private readonly platformIntegrationService: PlatformIntegrationService) {}

  @Get('health')
  getHealth(@Headers(PLATFORM_SECRET_HEADER) platformToken?: string) {
    return this.platformIntegrationService.getHealth(platformToken);
  }

  @Post('broadcasts')
  @HttpCode(202)
  acceptBroadcast(
    @Headers(PLATFORM_SECRET_HEADER) platformToken: string | undefined,
    @Body() request: PlatformBroadcastRequest,
  ) {
    return this.platformIntegrationService.acceptBroadcast(platformToken, request);
  }
}
