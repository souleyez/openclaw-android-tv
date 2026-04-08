# OpenClaw Android TV Backend API

This workspace is a transition backend surface for the Android TV project.

It is no longer the place to grow shared platform capabilities. Shared backend, shared model pool, and shared admin now belong to:

- `home`

Deployed Android TV traffic should prefer the unified public host:

- `http://1.12.246.48`

In practice, Android TV reuses the shared Sonance runtime instead of carrying a separate public TV backend.

## Allowed scope

- keep Android TV client compatibility while migration is still in progress
- expose the fixed `home` integration endpoints
- preserve local smoke-test and fallback workflows when required by the app
- act as an optional local compatibility runtime only when you deliberately test outside the shared gateway

## Not allowed here

- new shared admin features
- new shared model pool features meant for reuse across applications
- new cross-project governance or release management

## Fixed `home` integration contract

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`

Shared secret:

- request header `x-home-platform-token`
- env `HOME_PLATFORM_TOKEN`

If `HOME_PLATFORM_TOKEN` is empty, the endpoints stay open for local development.
If it is set, both endpoints require the matching header.
