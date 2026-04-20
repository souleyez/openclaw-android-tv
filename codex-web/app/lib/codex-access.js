import { buildControlPlaneApiUrl } from './config';
import {
  ADMIN_SESSION_HEADER,
  getAdminSessionTokenFromCookieStore,
  getAdminSessionTokenFromRequest,
} from './admin-auth';
import { getTrustedDeviceForToken, touchTrustedDevice } from './codex-control';

export const CODEX_DEVICE_COOKIE = 'cp_codex_mobile_device';
export const TRUSTED_DEVICE_REQUIRED_ERROR = 'TRUSTED_DEVICE_REQUIRED';
export const TRUSTED_DEVICE_REBIND_REQUIRED_ERROR = 'TRUSTED_DEVICE_REBIND_REQUIRED';

async function hasValidAdminSession(sessionToken) {
  if (!sessionToken) {
    return false;
  }

  try {
    const response = await fetch(buildControlPlaneApiUrl('/api/admin-auth/session'), {
      cache: 'no-store',
      headers: {
        [ADMIN_SESSION_HEADER]: sessionToken,
      },
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function resolveTrustedDeviceToken(token, touch = false) {
  const deviceToken = String(token || '').trim();
  if (!deviceToken) {
    return {
      allowed: false,
      kind: 'anonymous',
      device: null,
    };
  }

  const device = touch
    ? await touchTrustedDevice(deviceToken)
    : await getTrustedDeviceForToken(deviceToken);
  if (!device) {
    return {
      allowed: false,
      kind: 'anonymous',
      device: null,
    };
  }

  return {
    allowed: true,
    kind: 'bound',
    device,
  };
}

export async function resolveTrustedDeviceCookieAccess(cookieStore) {
  const deviceToken = cookieStore.get(CODEX_DEVICE_COOKIE)?.value?.trim() || '';
  return resolveTrustedDeviceToken(deviceToken, false);
}

export async function resolveTrustedDeviceRequestAccess(request, options = {}) {
  const touch = options.touch !== false;
  const deviceToken = request.cookies.get(CODEX_DEVICE_COOKIE)?.value?.trim() || '';
  return resolveTrustedDeviceToken(deviceToken, touch);
}

export async function resolveCodexCookieAccess(cookieStore) {
  const trustedAccess = await resolveTrustedDeviceCookieAccess(cookieStore);
  if (trustedAccess.allowed) {
    return trustedAccess;
  }

  const sessionToken = getAdminSessionTokenFromCookieStore(cookieStore);
  if (await hasValidAdminSession(sessionToken)) {
    return {
      allowed: true,
      kind: 'admin',
      device: null,
    };
  }

  return {
    allowed: false,
    kind: 'anonymous',
    device: null,
  };
}

export async function resolveCodexRequestAccess(request) {
  const trustedAccess = await resolveTrustedDeviceRequestAccess(request);
  if (trustedAccess.allowed) {
    return trustedAccess;
  }

  const sessionToken = getAdminSessionTokenFromRequest(request);
  if (await hasValidAdminSession(sessionToken)) {
    return {
      allowed: true,
      kind: 'admin',
      device: null,
    };
  }

  return {
    allowed: false,
    kind: 'anonymous',
    device: null,
  };
}
