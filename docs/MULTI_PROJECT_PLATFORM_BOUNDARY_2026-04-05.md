# Multi-Project Platform Boundary

## Canonical repositories

- `home`
  - the only shared platform repository
- `ai-data-platform`
  - AI assistant application repository
- `Sonance`
  - Sonance application repository
- `openclaw-android-tv`
  - Android TV application repository

## Shared platform rule

Anything reusable across two or more applications belongs to `home`.

Examples:

- shared admin login
- shared model pool
- provider credential management
- cross-project governance
- cross-project release control
- application catalog
- project integration health checks
- platform broadcast dispatch

## Application rule

Each application repository keeps:

- client code
- project-local runtime logic
- project-local packaging and release preparation
- consuming the fixed `home` integration contract

## Fixed integration contract

Every application must be able to accept:

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`

Shared secret contract:

- request header `x-home-platform-token`
- application env `HOME_PLATFORM_TOKEN`

## Migration rule

Legacy backend or admin directories may stay temporarily as transition surfaces, but they are frozen.

Allowed:

- compatibility fixes
- local smoke tests
- maintaining the fixed `home` integration contract

Not allowed:

- building a second shared admin
- building a second shared model pool
- building a second control plane
