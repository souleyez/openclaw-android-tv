import { NextResponse } from 'next/server';
import {
  bindTrustedDevice,
  clearTrustedDevice,
  getCodexBridgeStatus,
  getCodexDefaults,
  getCodexModels,
  getTrustedDeviceSummary,
} from '../../../lib/codex-control';
import {
  CODEX_DEVICE_COOKIE,
  resolveCodexRequestAccess,
  resolveTrustedDeviceRequestAccess,
  TRUSTED_DEVICE_REQUIRED_ERROR,
  TRUSTED_DEVICE_REBIND_REQUIRED_ERROR,
} from '../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function shouldUseSecureCookies(request) {
  const configured = process.env.PUBLIC_ADMIN_SECURE_COOKIES?.trim().toLowerCase();
  if (configured === 'true') {
    return true;
  }
  if (configured === 'false') {
    return false;
  }

  const forwardedProto = request?.headers.get('x-forwarded-proto')?.split(',')[0]?.trim().toLowerCase();
  return forwardedProto === 'https';
}

function buildCookieOptions(request) {
  return {
    httpOnly: true,
    sameSite: 'lax',
    secure: shouldUseSecureCookies(request),
    path: '/',
    maxAge: 180 * 24 * 60 * 60,
  };
}

export async function GET(request) {
  try {
    const access = await resolveCodexRequestAccess(request);
    if (!access.allowed) {
      return NextResponse.json({
        error: 'CODEX_ACCESS_REQUIRED',
      }, { status: 401 });
    }

    const [defaultsResult, modelsResult, boundDeviceResult, bridgeStatusResult] = await Promise.allSettled([
      getCodexDefaults(),
      getCodexModels(),
      getTrustedDeviceSummary(),
      getCodexBridgeStatus(),
    ]);

    const defaults = defaultsResult.status === 'fulfilled'
      ? defaultsResult.value
      : {
        model: 'gpt-5.4',
        reasoningEffort: 'medium',
      };
    const models = modelsResult.status === 'fulfilled'
      ? modelsResult.value
      : [];
    const boundDevice = boundDeviceResult.status === 'fulfilled'
      ? boundDeviceResult.value
      : null;
    const bridgeStatus = bridgeStatusResult.status === 'fulfilled'
      ? bridgeStatusResult.value
      : {
        mode: 'unknown',
        reachable: false,
        error: bridgeStatusResult.reason?.code
          || (bridgeStatusResult.reason instanceof Error
            ? bridgeStatusResult.reason.message
            : 'CODEX_RUNTIME_STATUS_UNKNOWN'),
        lastSeenAt: '',
      };

    return NextResponse.json({
      access,
      boundDevice,
      defaults,
      models,
      bridgeStatus,
    });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_ACCESS_LOAD_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: 500 });
  }
}

export async function POST(request) {
  const access = await resolveCodexRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: 'CODEX_ACCESS_REQUIRED',
    }, { status: 401 });
  }

  if (access.kind === 'bound') {
    return NextResponse.json({
      status: 'ok',
      access,
    });
  }

  const existingBoundDevice = await getTrustedDeviceSummary();
  if (existingBoundDevice) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REBIND_REQUIRED_ERROR,
      boundDevice: existingBoundDevice,
    }, { status: 409 });
  }

  const userAgent = request.headers.get('user-agent') || '';
  const binding = await bindTrustedDevice(userAgent);
  const response = NextResponse.json({
    status: 'ok',
    access: {
      allowed: true,
      kind: 'bound',
      device: binding.device,
    },
  });
  response.cookies.set(CODEX_DEVICE_COOKIE, binding.token, buildCookieOptions(request));
  return response;
}

export async function DELETE(request) {
  const trustedAccess = await resolveTrustedDeviceRequestAccess(request);
  if (!trustedAccess.allowed) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REQUIRED_ERROR,
    }, { status: 401 });
  }

  const clearedDevice = await clearTrustedDevice();
  const response = NextResponse.json({
    status: 'ok',
    clearedDevice,
  });
  response.cookies.set(CODEX_DEVICE_COOKIE, '', {
    ...buildCookieOptions(request),
    maxAge: 0,
  });
  return response;
}
