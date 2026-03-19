import { Module } from '@nestjs/common';

import { StorageModule } from '../../shared/storage.module';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';

@Module({
  imports: [StorageModule],
  controllers: [AdminController],
  providers: [AdminService],
})
export class AdminModule {}
