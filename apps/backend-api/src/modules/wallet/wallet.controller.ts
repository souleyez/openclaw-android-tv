import { Controller, Get, Headers } from '@nestjs/common';

import { AuthService } from '../auth/auth.service';
import { WalletService } from './wallet.service';

@Controller('wallet')
export class WalletController {
  constructor(
    private readonly walletService: WalletService,
    private readonly authService: AuthService,
  ) {}

  @Get()
  async getWallet(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    const ledger = await this.walletService.getLedger(account.id);
    const tokenBalance = await this.walletService.getTokenBalance(account.id);

    return {
      accountId: account.id,
      tokenBalance,
      items: ledger,
    };
  }
}
