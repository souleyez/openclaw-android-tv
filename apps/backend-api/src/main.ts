import 'dotenv/config';
import { resolve } from 'node:path';
import { NestFactory } from '@nestjs/core';

import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const express = require('express') as typeof import('express');

  app.enableCors();
  app.setGlobalPrefix('api');
  app.use('/uploads', express.static(resolve(process.cwd(), 'data', 'uploads')));
  await app.listen(3000);
}

void bootstrap();
