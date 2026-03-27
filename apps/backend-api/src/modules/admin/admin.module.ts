import { Module } from '@nestjs/common';

import { RadioModule } from '../radio/radio.module';
import { StorageModule } from '../../shared/storage.module';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';

@Module({
  imports: [StorageModule, RadioModule],
  controllers: [AdminController],
  providers: [AdminService],
})
export class AdminModule {}
