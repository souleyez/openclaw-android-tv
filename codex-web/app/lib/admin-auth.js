export const ADMIN_SESSION_COOKIE = 'cp_admin_session';
export const ADMIN_SESSION_HEADER = 'X-Control-Plane-Admin-Session';
export const ADMIN_TOKEN_COOKIE = 'cp_admin_token';
export const ADMIN_TOKEN_HEADER = 'X-Control-Plane-Admin-Token';
export const ACTIVE_PROJECT_COOKIE = 'cp_project_key';
export const DEFAULT_PROJECT_KEY = 'ai-data-platform';

const PROJECT_KEY_ALIASES = {
  'windows-client': 'ai-data-platform',
  'smart-assistant': 'ai-data-platform',
  'android-tv': 'openclaw-android-tv',
};

export function normalizeProjectKey(value) {
  const normalized = String(value || '').trim().toLowerCase();
  if (!normalized) {
    return DEFAULT_PROJECT_KEY;
  }

  const slug = normalized.replace(/[^a-z0-9-]/g, '-');
  return PROJECT_KEY_ALIASES[slug] || slug;
}

export function getAdminSessionTokenFromRequest(request) {
  return request.cookies.get(ADMIN_SESSION_COOKIE)?.value?.trim() || '';
}

export function getAdminSessionTokenFromCookieStore(cookieStore) {
  return cookieStore.get(ADMIN_SESSION_COOKIE)?.value?.trim() || '';
}

export function getAdminTokenFromRequest(request) {
  return request.cookies.get(ADMIN_TOKEN_COOKIE)?.value?.trim() || '';
}

export function getAdminTokenFromCookieStore(cookieStore) {
  return cookieStore.get(ADMIN_TOKEN_COOKIE)?.value?.trim() || '';
}

export function getActiveProjectKeyFromRequest(request) {
  return normalizeProjectKey(request.cookies.get(ACTIVE_PROJECT_COOKIE)?.value?.trim() || DEFAULT_PROJECT_KEY);
}

export function getActiveProjectKeyFromCookieStore(cookieStore) {
  return normalizeProjectKey(cookieStore.get(ACTIVE_PROJECT_COOKIE)?.value?.trim() || DEFAULT_PROJECT_KEY);
}
