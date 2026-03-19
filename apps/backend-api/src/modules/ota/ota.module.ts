import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { StorageModule } from '../../shared/storage.module';
import { OtaController } from './ota.controller';
import { OtaService } from './ota.service';

@Module({
  imports: [StorageModule, AuthModule],
  controllers: [OtaController],
  providers: [OtaService],
})
export class OtaModule {}
