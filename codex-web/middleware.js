import { NextResponse } from 'next/server';

const ALLOWED_PAGES = new Set(['/', '/login', '/codex']);

function applySecurityHeaders(response) {
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'no-referrer');
  response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
  response.headers.set('Cross-Origin-Resource-Policy', 'same-origin');
  response.headers.set('X-Robots-Tag', 'noindex, nofollow, noarchive');
  return response;
}

function isAllowedPage(pathname) {
  if (ALLOWED_PAGES.has(pathname)) {
    return true;
  }

  return pathname.startsWith('/login/') || pathname.startsWith('/codex/');
}

export function middleware(request) {
  const { pathname } = request.nextUrl;
  if (isAllowedPage(pathname)) {
    return applySecurityHeaders(NextResponse.next());
  }

  return applySecurityHeaders(NextResponse.rewrite(new URL('/', request.url)));
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|.*\\..*).*)'],
};
