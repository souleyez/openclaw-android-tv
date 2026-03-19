import { Body, Controller, Headers, Post } from '@nestjs/common';

import { AuthService } from '../auth/auth.service';
import { ModelRouterService } from '../model-router/model-router.service';

class ChatRequestDto {
  text!: string;
  locale!: string;
  deviceId?: string;
}

@Controller('chat')
export class ChatController {
  constructor(
    private readonly modelRouterService: ModelRouterService,
    private readonly authService: AuthService,
  ) {}

  @Post('respond')
  async respond(
    @Headers('x-device-user-id') deviceUserId: string | undefined,
    @Headers('x-session-token') sessionToken: string | undefined,
    @Body() body: ChatRequestDto,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return this.modelRouterService.respondChat({
      ...body,
      accountId: account.id,
    });
  }
}
