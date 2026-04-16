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

## Native rewrite baseline

The Android TV client now has two parallel implementation tracks:

- `apps/android-tv-client`
  - existing Flutter transition client
- `apps/android-tv-client-kotlin`
  - planned native Kotlin client

The first Kotlin baseline is allowed to ship without board-vendor system services.

That baseline should:

- depend only on public Android APIs and `home`
- support bootstrap, lease, native home shell, in-app push-to-talk, and app launch
- clearly surface degraded capability state

That baseline must not assume:

- `vendor.voice`
- `vendor.media`
- `vendor.projector`
- `vendor.deviceops`

Detailed baseline constraints live in:

- `docs/architecture/2026-04-16-no-system-capability-baseline.md`

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
