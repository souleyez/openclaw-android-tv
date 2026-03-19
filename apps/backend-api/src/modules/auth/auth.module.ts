import { Module } from '@nestjs/common';

import { StorageModule } from '../../shared/storage.module';
import { AuthController } from './auth.controller';
import { RecoveryPortalController } from './recovery-portal.controller';
import { AuthService } from './auth.service';

@Module({
  imports: [StorageModule],
  controllers: [AuthController, RecoveryPortalController],
  providers: [AuthService],
  exports: [AuthService],
})
export class AuthModule {}
