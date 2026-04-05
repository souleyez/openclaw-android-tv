import { Module } from '@nestjs/common';

import { HealthModule } from './shared/health.module';
import { AdminModule } from './modules/admin/admin.module';
import { AvatarModule } from './modules/avatar/avatar.module';
import { AuthModule } from './modules/auth/auth.module';
import { BillingModule } from './modules/billing/billing.module';
import { ChatModule } from './modules/chat/chat.module';
import { DeviceModule } from './modules/device/device.module';
import { ModelRouterModule } from './modules/model-router/model-router.module';
import { OtaModule } from './modules/ota/ota.module';
import { UserModule } from './modules/user/user.module';
import { PlatformIntegrationModule } from './platform-integration/platform-integration.module';
import { WalletModule } from './modules/wallet/wallet.module';
import { StorageModule } from './shared/storage.module';

@Module({
  imports: [
    StorageModule,
    HealthModule,
    AdminModule,
    AvatarModule,
    AuthModule,
    BillingModule,
    WalletModule,
    UserModule,
    DeviceModule,
    OtaModule,
    ModelRouterModule,
    ChatModule,
    PlatformIntegrationModule,
  ],
})
export class AppModule {}
