# OpenClaw Android TV

`openclaw-android-tv` is an application repository for the Android TV client.

It is not the shared platform repository.

## Active scope

- `apps/android-tv-client`
  - Android TV client development
- device-side UX, runtime behavior, packaging, and compatibility work

## Runtime/backend direction

Android TV is now expected to:

- stay a distinct project in `home`
- reuse the shared Sonance runtime behind the unified gateway
- default to the public backend host `http://1.12.246.48`

That means this repository should keep focusing on the TV client, not on rebuilding a second shared backend.

## Frozen transition surfaces

- `apps/backend-api`
  - transition surface only
  - keep the fixed `home` integration contract
  - do not add new shared backend or governance features here
- `apps/admin-console`
  - frozen local admin surface
  - new management work belongs in `home`

## Platform boundary

Shared backend and shared admin now belong to:

- `home`

The Android TV project should consume platform capabilities from `home` and only keep the minimum local contract needed during migration:

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`

Detailed repository boundary rules live in:

- `docs/APP_BOUNDARY_2026-04-05.md`
