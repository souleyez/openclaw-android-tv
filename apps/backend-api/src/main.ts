import 'dotenv/config';
import { RequestMethod } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';

import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const port = Number.parseInt(process.env.PORT ?? '3000', 10) || 3000;
  app.setGlobalPrefix('api', {
    exclude: [
      { path: 'internal/platform/health', method: RequestMethod.GET },
      { path: 'internal/platform/broadcasts', method: RequestMethod.POST },
    ],
  });
  await app.listen(port);
}

void bootstrap();
