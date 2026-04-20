import { NextResponse } from 'next/server';
import { buildControlPlaneApiUrl } from '../../../lib/config';
import {
  ACTIVE_PROJECT_COOKIE,
  ADMIN_SESSION_COOKIE,
  ADMIN_SESSION_HEADER,
  ADMIN_TOKEN_COOKIE,
  DEFAULT_PROJECT_KEY,
  getActiveProjectKeyFromRequest,
  getAdminSessionTokenFromRequest,
  normalizeProjectKey,
} from '../../../lib/admin-auth';

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
    maxAge: 30 * 24 * 60 * 60,
  };
}

function clearCookie(response, name, request) {
  response.cookies.set(name, '', {
    ...buildCookieOptions(request),
    maxAge: 0,
  });
}

export async function GET(request) {
  const sessionToken = getAdminSessionTokenFromRequest(request);
  if (!sessionToken) {
    return NextResponse.json({
      authenticated: false,
      projectKey: getActiveProjectKeyFromRequest(request),
    });
  }

  const response = await fetch(buildControlPlaneApiUrl('/api/admin-auth/session'), {
    cache: 'no-store',
    headers: {
      [ADMIN_SESSION_HEADER]: sessionToken,
    },
  });

  if (!response.ok) {
    const body = await response.text();
    const nextResponse = new NextResponse(body || JSON.stringify({ authenticated: false }), {
      status: response.status,
      headers: {
        'Content-Type': response.headers.get('content-type') || 'application/json',
      },
    });
    clearCookie(nextResponse, ADMIN_SESSION_COOKIE, request);
    clearCookie(nextResponse, ADMIN_TOKEN_COOKIE, request);
    return nextResponse;
  }

  const payload = await response.json();
  return NextResponse.json({
    authenticated: true,
    projectKey: getActiveProjectKeyFromRequest(request),
    ...payload,
  });
}

export async function POST(request) {
  try {
    const body = await request.json();
    const platformKey = typeof body?.platformKey === 'string' ? body.platformKey.trim() : '';
    const projectKey = normalizeProjectKey(
      typeof body?.projectKey === 'string' && body.projectKey.trim()
        ? body.projectKey.trim()
        : DEFAULT_PROJECT_KEY,
    );
    if (platformKey) {
      const backendResponse = await fetch(buildControlPlaneApiUrl('/api/admin-auth/platform-key'), {
        method: 'POST',
        cache: 'no-store',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ platformKey }),
      });

      const text = await backendResponse.text();
      if (!backendResponse.ok) {
        return new NextResponse(text, {
          status: backendResponse.status,
          headers: {
            'Content-Type': backendResponse.headers.get('content-type') || 'application/json',
          },
        });
      }

      const payload = JSON.parse(text);
      const response = NextResponse.json(payload);
      response.cookies.set(ADMIN_SESSION_COOKIE, payload.session.token, buildCookieOptions(request));
      response.cookies.set(ACTIVE_PROJECT_COOKIE, projectKey, buildCookieOptions(request));
      clearCookie(response, ADMIN_TOKEN_COOKIE, request);
      return response;
    }

    const email = typeof body?.email === 'string' ? body.email.trim() : '';
    if (!email) {
      return NextResponse.json({
        error: 'PLATFORM_KEY_REQUIRED',
      }, { status: 400 });
    }

    const backendResponse = await fetch(buildControlPlaneApiUrl('/api/admin-auth/request-code'), {
      method: 'POST',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email }),
    });

    const text = await backendResponse.text();
    return new NextResponse(text, {
      status: backendResponse.status,
      headers: {
        'Content-Type': backendResponse.headers.get('content-type') || 'application/json',
      },
    });
  } catch (error) {
    return NextResponse.json({
      error: 'ADMIN_CHALLENGE_REQUEST_FAILED',
      message: error instanceof Error ? error.message : String(error),
    }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const body = await request.json();
    const email = typeof body?.email === 'string' ? body.email.trim() : '';
    const challengeId = typeof body?.challengeId === 'string' ? body.challengeId.trim() : '';
    const code = typeof body?.code === 'string' ? body.code.trim() : '';
    const projectKey = normalizeProjectKey(
      typeof body?.projectKey === 'string' && body.projectKey.trim()
        ? body.projectKey.trim()
        : DEFAULT_PROJECT_KEY,
    );

    if (!email || !challengeId || !code) {
      return NextResponse.json({
        error: !email ? 'ADMIN_EMAIL_REQUIRED' : (!challengeId ? 'ADMIN_CHALLENGE_ID_REQUIRED' : 'ADMIN_CODE_REQUIRED'),
      }, { status: 400 });
    }

    const backendResponse = await fetch(buildControlPlaneApiUrl('/api/admin-auth/verify-code'), {
      method: 'POST',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email,
        challengeId,
        code,
      }),
    });

    const text = await backendResponse.text();
    if (!backendResponse.ok) {
      return new NextResponse(text, {
        status: backendResponse.status,
        headers: {
          'Content-Type': backendResponse.headers.get('content-type') || 'application/json',
        },
      });
    }

    const payload = JSON.parse(text);
    const response = NextResponse.json(payload);
    response.cookies.set(ADMIN_SESSION_COOKIE, payload.session.token, buildCookieOptions(request));
    response.cookies.set(ACTIVE_PROJECT_COOKIE, projectKey, buildCookieOptions(request));
    clearCookie(response, ADMIN_TOKEN_COOKIE, request);
    return response;
  } catch (error) {
    return NextResponse.json({
      error: 'ADMIN_SESSION_CREATE_FAILED',
      message: error instanceof Error ? error.message : String(error),
    }, { status: 500 });
  }
}

export async function PATCH(request) {
  try {
    const body = await request.json();
    const projectKey = normalizeProjectKey(
      typeof body?.projectKey === 'string' && body.projectKey.trim()
        ? body.projectKey.trim()
        : DEFAULT_PROJECT_KEY,
    );

    const response = NextResponse.json({
      status: 'ok',
      projectKey,
    });
    response.cookies.set(ACTIVE_PROJECT_COOKIE, projectKey, buildCookieOptions(request));
    return response;
  } catch (error) {
    return NextResponse.json({
      error: 'ADMIN_PROJECT_SELECT_FAILED',
      message: error instanceof Error ? error.message : String(error),
    }, { status: 500 });
  }
}

export async function DELETE(request) {
  const sessionToken = getAdminSessionTokenFromRequest(request);
  if (sessionToken) {
    await fetch(buildControlPlaneApiUrl('/api/admin-auth/logout'), {
      method: 'POST',
      cache: 'no-store',
      headers: {
        [ADMIN_SESSION_HEADER]: sessionToken,
      },
    }).catch(() => undefined);
  }

  const response = NextResponse.json({
    status: 'ok',
    authenticated: false,
  });
  clearCookie(response, ADMIN_SESSION_COOKIE, request);
  clearCookie(response, ADMIN_TOKEN_COOKIE, request);
  clearCookie(response, ACTIVE_PROJECT_COOKIE, request);
  return response;
}
