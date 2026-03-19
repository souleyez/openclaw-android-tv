import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { BillingModule } from '../billing/billing.module';
import { DeviceModule } from '../device/device.module';
import { WalletModule } from '../wallet/wallet.module';
import { UserController } from './user.controller';

@Module({
  imports: [AuthModule, DeviceModule, BillingModule, WalletModule],
  controllers: [UserController],
})
export class UserModule {}
