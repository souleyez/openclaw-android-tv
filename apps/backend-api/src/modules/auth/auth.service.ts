import { Injectable, UnauthorizedException } from '@nestjs/common';
import { randomUUID } from 'node:crypto';

import {
  AdminAllowedEmailRecord,
  AdminLoginCodeRecord,
  AdminSessionRecord,
  AuthSessionRecord,
  DeviceUserProfileRecord,
  StablecoinPaymentOrderRecord,
  StorageService,
  TesterAccountRecord,
} from '../../shared/storage.service';

@Injectable()
export class AuthService {
  constructor(private readonly storageService: StorageService) {}
  private readonly adminCodeTtlMinutes = 10;
  private readonly adminSessionTtlDays = 30;

  async loginWithInviteCode(params: {
    inviteCode: string;
  }): Promise<{ session: AuthSessionRecord; account: TesterAccountRecord }> {
    const account = await this.storageService.getTesterAccountByInviteCode(
      params.inviteCode,
    );
    if (account == null) {
      throw new UnauthorizedException('Invalid invite code');
    }

    const now = new Date().toISOString();
    const session: AuthSessionRecord = {
      token: `session_${randomUUID()}`,
      accountId: account.id,
      createdAt: now,
      lastSeenAt: now,
    };
    await this.storageService.upsertAuthSession(session);
    return { session, account };
  }

  async resolveDeviceUser(params: {
    deviceUserId?: string;
    sessionToken?: string;
  }): Promise<DeviceUserProfileRecord> {
    if (params.deviceUserId != null && params.deviceUserId.trim().length > 0) {
      return this.ensureDeviceUserProfile(params.deviceUserId.trim());
    }

    const fallback = await this.storageService.getDeviceUserProfile('user_demo');
    if (params.sessionToken == null || params.sessionToken.trim().length === 0) {
      if (fallback == null) {
        throw new UnauthorizedException('Missing device user identity');
      }
      return fallback;
    }

    const session = await this.storageService.getAuthSession(params.sessionToken);
    if (session == null) {
      throw new UnauthorizedException('Session token is invalid');
    }

    return this.ensureDeviceUserProfile(session.accountId);
  }

  async recoverDeviceUserByPaymentProof(params: {
    txHash: string;
    chain?: string;
    amountUsd?: number;
  }): Promise<{ deviceUser: DeviceUserProfileRecord; order: StablecoinPaymentOrderRecord }> {
    const order = await this.storageService.getStablecoinOrderByTxHash(params.txHash);
    if (order == null) {
      throw new UnauthorizedException('Payment proof was not found');
    }
    if (params.chain != null && params.chain !== order.chain) {
      throw new UnauthorizedException('Payment chain does not match');
    }
    if (params.amountUsd != null && Number(params.amountUsd) !== Number(order.amountUsd)) {
      throw new UnauthorizedException('Payment amount does not match');
    }

    const deviceUser = await this.ensureDeviceUserProfile(order.accountId);
    return { deviceUser, order };
  }

  async transferDeviceEntitlements(params: {
    fromDeviceUserId: string;
    toDeviceUserId: string;
    paymentProofTxHash: string;
  }): Promise<{ fromDeviceUser: string; toDeviceUser: string; transferred: true }> {
    const recovery = await this.recoverDeviceUserByPaymentProof({
      txHash: params.paymentProofTxHash,
    });
    if (recovery.deviceUser.id !== params.fromDeviceUserId) {
      throw new UnauthorizedException('Payment proof does not belong to the source device user');
    }

    await this.ensureDeviceUserProfile(params.toDeviceUserId);
    await this.storageService.transferAccountData({
      fromAccountId: params.fromDeviceUserId,
      toAccountId: params.toDeviceUserId,
      transferReason: 'device_inheritance',
      paymentProofTxHash: params.paymentProofTxHash,
    });

    return {
      fromDeviceUser: params.fromDeviceUserId,
      toDeviceUser: params.toDeviceUserId,
      transferred: true,
    };
  }

  async resolveAccount(token?: string): Promise<TesterAccountRecord> {
    const fallback = await this.storageService.getTesterAccount('user_demo');
    if (token == null || token.trim().length === 0) {
      if (fallback == null) {
        throw new UnauthorizedException('Missing session token');
      }
      return fallback;
    }

    const session = await this.storageService.getAuthSession(token);
    if (session == null) {
      throw new UnauthorizedException('Session token is invalid');
    }

    const account = await this.storageService.getTesterAccount(session.accountId);
    if (account == null) {
      throw new UnauthorizedException('Session account was not found');
    }

    await this.storageService.upsertAuthSession({
      ...session,
      lastSeenAt: new Date().toISOString(),
    });

    return account;
  }

  async requestAdminLoginCode(params: {
    email: string;
  }): Promise<{ sent: true; email: string; expiresInMinutes: number }> {
    const email = params.email.trim().toLowerCase();
    const admin = await this.storageService.getAdminAllowedEmail(email);
    if (admin == null || admin.status !== 'active') {
      throw new UnauthorizedException('Admin email is not allowed');
    }

    const now = new Date();
    const code = String(Math.floor(100000 + Math.random() * 900000));
    const record: AdminLoginCodeRecord = {
      id: `admin_code_${randomUUID()}`,
      email,
      code,
      expiresAt: new Date(
        now.getTime() + this.adminCodeTtlMinutes * 60 * 1000,
      ).toISOString(),
      createdAt: now.toISOString(),
    };

    await this.storageService.upsertAdminLoginCode(record);
    await this.sendAdminLoginCode(admin, code);

    return {
      sent: true,
      email,
      expiresInMinutes: this.adminCodeTtlMinutes,
    };
  }

  async verifyAdminLoginCode(params: {
    email: string;
    code: string;
  }): Promise<{ session: AdminSessionRecord; admin: AdminAllowedEmailRecord }> {
    const email = params.email.trim().toLowerCase();
    const admin = await this.storageService.getAdminAllowedEmail(email);
    if (admin == null || admin.status !== 'active') {
      throw new UnauthorizedException('Admin email is not allowed');
    }

    const latestCode = await this.storageService.getLatestAdminLoginCode(email);
    if (
      latestCode == null ||
      latestCode.usedAt != null ||
      latestCode.code !== params.code.trim() ||
      new Date(latestCode.expiresAt).getTime() < Date.now()
    ) {
      throw new UnauthorizedException('Verification code is invalid');
    }

    const now = new Date().toISOString();
    await this.storageService.markAdminLoginCodeUsed(latestCode.id, now);

    const session: AdminSessionRecord = {
      token: `admin_session_${randomUUID()}`,
      email,
      createdAt: now,
      lastSeenAt: now,
      expiresAt: new Date(
        Date.now() + this.adminSessionTtlDays * 24 * 60 * 60 * 1000,
      ).toISOString(),
    };
    await this.storageService.upsertAdminSession(session);

    return { session, admin };
  }

  async resolveAdminSession(token?: string): Promise<AdminAllowedEmailRecord> {
    if (token == null || token.trim().length === 0) {
      throw new UnauthorizedException('Missing admin session token');
    }

    const session = await this.storageService.getAdminSession(token.trim());
    if (session == null) {
      throw new UnauthorizedException('Admin session is invalid');
    }
    if (new Date(session.expiresAt).getTime() < Date.now()) {
      throw new UnauthorizedException('Admin session has expired');
    }

    const admin = await this.storageService.getAdminAllowedEmail(session.email);
    if (admin == null || admin.status !== 'active') {
      throw new UnauthorizedException('Admin session account was not found');
    }

    const now = new Date().toISOString();
    await this.storageService.upsertAdminSession({
      ...session,
      lastSeenAt: now,
      expiresAt: new Date(
        Date.now() + this.adminSessionTtlDays * 24 * 60 * 60 * 1000,
      ).toISOString(),
    });

    return admin;
  }

  private async ensureDeviceUserProfile(
    deviceUserId: string,
  ): Promise<DeviceUserProfileRecord> {
    const existing = await this.storageService.getDeviceUserProfile(deviceUserId);
    if (existing != null) {
      return existing;
    }

    const now = new Date().toISOString();
    const profile: DeviceUserProfileRecord = {
      id: deviceUserId,
      displayName: `Device User ${deviceUserId.substring(0, 8)}`,
      planCode: 'basic',
      status: 'active',
      recoveryHint: 'recover with latest stablecoin payment proof',
      createdAt: now,
      updatedAt: now,
    };
    await this.storageService.upsertDeviceUserProfile(profile);
    return profile;
  }

  private async sendAdminLoginCode(
    admin: AdminAllowedEmailRecord,
    code: string,
  ): Promise<void> {
    const host = process.env.ADMIN_SMTP_HOST?.trim();
    const user = process.env.ADMIN_SMTP_USER?.trim();
    const pass = process.env.ADMIN_SMTP_PASS?.trim();
    const from =
      process.env.ADMIN_SMTP_FROM?.trim() ||
      process.env.ADMIN_SMTP_USER?.trim();
    const port = Number.parseInt(process.env.ADMIN_SMTP_PORT ?? '587', 10);
    const secure = (process.env.ADMIN_SMTP_SECURE ?? 'false') === 'true';

    if (!host || !user || !pass || !from) {
      throw new UnauthorizedException(
        'Admin SMTP is not configured. Set ADMIN_SMTP_* env vars first.',
      );
    }

    const nodemailer = require('nodemailer') as {
      createTransport: (config: Record<string, unknown>) => {
        sendMail: (options: Record<string, unknown>) => Promise<unknown>;
      };
    };

    const transporter = nodemailer.createTransport({
      host,
      port,
      secure,
      auth: {
        user,
        pass,
      },
    });

    await transporter.sendMail({
      from,
      to: admin.email,
      subject: 'OpenClaw 管理后台验证码',
      text: `你好，${admin.displayName}。\n\n你的 OpenClaw 管理后台验证码是：${code}\n\n验证码 ${this.adminCodeTtlMinutes} 分钟内有效。`,
    });
  }
}
