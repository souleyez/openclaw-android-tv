import { Body, Controller, Get, Headers, Post } from '@nestjs/common';

import { AuthService } from './auth.service';

class LoginDto {
  inviteCode!: string;
}

class RecoverByPaymentDto {
  txHash!: string;
  chain?: string;
  amountUsd?: number;
}

class TransferEntitlementsDto {
  fromDeviceUserId!: string;
  toDeviceUserId!: string;
  paymentProofTxHash!: string;
}

class RequestAdminLoginCodeDto {
  email!: string;
}

class VerifyAdminLoginCodeDto {
  email!: string;
  code!: string;
}

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('login')
  async login(@Body() body: LoginDto) {
    const { session, account } = await this.authService.loginWithInviteCode({
      inviteCode: body.inviteCode,
    });

    return {
      accessToken: session.token,
      refreshToken: session.token,
      user: {
        id: account.id,
        email: account.email,
        displayName: account.displayName,
        plan: account.planCode,
      },
    };
  }

  @Get('session')
  async getSession(@Headers('x-session-token') sessionToken?: string) {
    const account = await this.authService.resolveAccount(sessionToken);
    return {
      user: {
        id: account.id,
        email: account.email,
        displayName: account.displayName,
        plan: account.planCode,
      },
    };
  }

  @Get('device-user')
  async getDeviceUser(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const deviceUser = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    return {
      deviceUser,
    };
  }

  @Post('recover-by-payment')
  async recoverByPayment(@Body() body: RecoverByPaymentDto) {
    return this.authService.recoverDeviceUserByPaymentProof(body);
  }

  @Post('transfer-entitlements')
  async transferEntitlements(@Body() body: TransferEntitlementsDto) {
    return this.authService.transferDeviceEntitlements(body);
  }

  @Post('admin/request-code')
  async requestAdminLoginCode(@Body() body: RequestAdminLoginCodeDto) {
    return this.authService.requestAdminLoginCode({
      email: body.email,
    });
  }

  @Post('admin/verify-code')
  async verifyAdminLoginCode(@Body() body: VerifyAdminLoginCodeDto) {
    const { session, admin } = await this.authService.verifyAdminLoginCode({
      email: body.email,
      code: body.code,
    });

    return {
      accessToken: session.token,
      admin: {
        email: admin.email,
        displayName: admin.displayName,
      },
    };
  }

  @Get('admin/session')
  async getAdminSession(@Headers('x-admin-session-token') sessionToken?: string) {
    const admin = await this.authService.resolveAdminSession(sessionToken);
    return {
      admin: {
        email: admin.email,
        displayName: admin.displayName,
      },
    };
  }
}
