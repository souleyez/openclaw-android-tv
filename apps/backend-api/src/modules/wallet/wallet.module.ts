import { Module } from '@nestjs/common';

import { StorageModule } from '../../shared/storage.module';
import { AuthModule } from '../auth/auth.module';
import { WalletController } from './wallet.controller';
import { WalletService } from './wallet.service';

@Module({
  imports: [StorageModule, AuthModule],
  controllers: [WalletController],
  providers: [WalletService],
  exports: [WalletService],
})
export class WalletModule {}
