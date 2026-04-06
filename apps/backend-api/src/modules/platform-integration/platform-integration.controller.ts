import { Body, Controller, Get, Headers, Post } from '@nestjs/common';

import { PlatformIntegrationService } from './platform-integration.service';

class PlatformBroadcastDto {
  broadcastId!: string;
  projectKey!: string;
  kind?: string;
  scope?: string;
  title!: string;
  body?: string;
  payload?: Record<string, unknown>;
  createdAt?: string;
  expiresAt?: string | null;
  idempotencyKey?: string;
}

@Controller('internal/platform')
export class PlatformIntegrationController {
  constructor(
    private readonly platformIntegrationService: PlatformIntegrationService,
  ) {}

  @Get('health')
  getHealth(@Headers('x-home-platform-token') token?: string) {
    return this.platformIntegrationService.getHealth(token);
  }

  @Post('broadcasts')
  acceptBroadcast(
    @Headers('x-home-platform-token') token: string | undefined,
    @Body() body: PlatformBroadcastDto,
  ) {
    return this.platformIntegrationService.acceptBroadcast(token, body);
  }
}
