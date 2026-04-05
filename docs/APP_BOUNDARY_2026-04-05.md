# OpenClaw Android TV Application Boundary

## Position

`openclaw-android-tv` is an application repository.

Its long-term development focus is the Android TV client.

## What stays in this repository

- `apps/android-tv-client`
- TV-specific UX and system adaptation
- device-side runtime behavior
- consuming platform broadcasts from `home`

## What moves to `home`

- shared admin login and sessions
- shared model pool and provider credential management
- cross-project governance
- cross-project release control
- application catalog
- project integration health checks
- platform broadcast dispatch

## Temporary migration rule

`apps/backend-api` and `apps/admin-console` stay only as transition surfaces.

Allowed work there:

- keep the fixed `home` integration endpoints working
- keep local compatibility tests runnable while the client finishes migration
- minimal bug fixes needed by the Android TV client

Not allowed there:

- new shared backend modules
- new shared admin features
- new shared model routing logic intended for reuse across applications
- a second control plane

## Fixed integration contract

The project must keep accepting:

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`

Shared secret:

- request header `x-home-platform-token`
- application env `HOME_PLATFORM_TOKEN`
