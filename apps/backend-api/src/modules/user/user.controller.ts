import { Controller, Get, Headers, Query } from '@nestjs/common';
import { AuthService } from '../auth/auth.service';
import { BillingService } from '../billing/billing.service';
import { DeviceService } from '../device/device.service';
import { WalletService } from '../wallet/wallet.service';
import { StorageService } from '../../shared/storage.service';

@Controller('me')
export class UserController {
  constructor(
    private readonly authService: AuthService,
    private readonly deviceService: DeviceService,
    private readonly billingService: BillingService,
    private readonly walletService: WalletService,
    private readonly storageService: StorageService,
  ) {}

  @Get()
  async getCurrentUser(
    @Headers('x-device-user-id') deviceUserId?: string,
    @Headers('x-session-token') sessionToken?: string,
  ) {
    const account = await this.authService.resolveDeviceUser({
      deviceUserId,
      sessionToken,
    });
    const devices = await this.deviceService.listDevices(account.id);
    const plan = await this.billingService.getPlanByCode(account.planCode);
    const sharedBindings =
      await this.deviceService.listShareBindings(account.id);
    const shareScopeSummary =
      await this.deviceService.getShareScopeSummary(account.id);
    const sharePolicy = this.deviceService.getSharePolicyRules();
    const tokenUsageEvents = await this.deviceService.listTokenUsageEvents(account.id);
    const tokenUsageSummary = await this.deviceService.getTokenUsageSummary(account.id);
    const paymentOrders =
      await this.billingService.listStablecoinOrders(account.id);
    const tokenBalance = await this.walletService.getTokenBalance(account.id);
    const bootstrapRemaining = devices.reduce(
      (sum, device) => sum + device.bootstrapTokenRemaining,
      0,
    );

    return {
      id: account.id,
      email: `${account.id}@device.local`,
      displayName: account.displayName,
      plan: plan?.code ?? account.planCode,
      tokenBalance,
      deviceLimit: plan?.deviceLimit ?? 3,
      activeDevices: devices.length,
      bootstrapTokenPool: bootstrapRemaining,
      paymentPriority: 'stablecoin',
      sharedDeviceBindings: sharedBindings.length,
      shareScopeSummary,
      sharePolicy,
      entitlementExpiresAt: account.entitlementExpiresAt ?? null,
      recentBootstrapTokenUsage: tokenUsageEvents.slice(0, 6),
      bootstrapTokenUsageSummary: tokenUsageSummary,
      latestStablecoinOrderStatus: paymentOrders[0]?.status ?? null,
    };
  }

  @Get('tv-home-config')
  async getTvHomeConfig(
    @Query('countryCode') countryCode?: string,
    @Query('regionCode') regionCode?: string,
  ) {
    const config = await this.storageService.resolveTvHomeConfig({
      countryCode,
      regionCode,
    });

    return (
      config ?? {
        id: 'tv_home_empty',
        countryCode: 'GLOBAL',
        regionCode: 'GLOBAL',
        backgroundImageUrl: null,
        featuredAppIds: [],
        status: 'draft',
        version: 0,
        createdAt: new Date(0).toISOString(),
        updatedAt: new Date(0).toISOString(),
      }
    );
  }
}
