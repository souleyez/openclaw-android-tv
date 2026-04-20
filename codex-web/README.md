# Codex Web

Standalone Next.js app for the authenticated Codex web surface previously embedded in `home`.

## Scope

- `/codex` mobile web shell
- `/login` platform-key login
- `/api/codex/*` runtime control endpoints
- `/api/admin/session` session bootstrap for platform auth
- local, bridge, and relay Codex runtime helpers

## Local development

1. Copy `.env.example` to `.env.local`.
2. Point `CONTROL_PLANE_API_BASE_URL` at the running platform API.
3. If you need to preserve the old `home` runtime state, set `CODEX_CONTROL_STATE_DIR`, `CODEX_RELAY_STATE_DIR`, `CODEX_UPLOADS_ROOT`, `CODEX_DOWNLOADS_ROOT`, and `CODEX_BRIDGE_STATE_DIR` to the existing storage paths.
4. Install dependencies with `npm install`.
5. Run `npm run dev`.

The app listens on port `3003` by default.
