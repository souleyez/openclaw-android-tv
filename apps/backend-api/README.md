# OpenClaw Android TV Backend API

This workspace is a transition backend surface for the Android TV project.

It is no longer the place to grow shared platform capabilities. Shared backend, shared model pool, and shared admin now belong to:

- `home`

## Allowed scope

- keep Android TV client compatibility while migration is still in progress
- expose the fixed `home` integration endpoints
- preserve local smoke-test and fallback workflows when required by the app

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
