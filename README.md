# Sonance

Sonance is a minimal single-screen radio app built with Expo and backed by a lightweight NestJS service.

Control-plane status:

- `home/` is now the active control-plane workspace for Sonance.
- New work on model leasing, OTA/config release management, admin surfaces, and project governance should go to `home/`.
- This repo keeps Sonance runtime code plus legacy compatibility surfaces only.
- `apps/admin-console` and Sonance-local control-plane modules are frozen except for compatibility fixes or rollback-safe maintenance.

Current workspace focus:

- `apps/radio-app`
  Expo client for Android, iOS, and Web
- `apps/backend-api`
  Sonance runtime backend for radio catalog, uploads, AI broadcast generation, and `home` integration endpoints
- `apps/admin-console`
  Legacy Sonance admin console kept for migration compatibility only
- `docs/plans/2026-03-26-radio-app-design.md`
  Product and technical design notes for the current app direction
- `docs/plans/2026-04-06-sonance-control-plane-freeze.md`
  Freeze rules for migrated control-plane surfaces

Related but separate workspace:

- `home/`
  Shared platform/control-plane workspace. This is the active home for Sonance control-plane development.

## Product direction

- Single-screen interface
- Voice-first interaction
- Local-first station switching and habit learning
- Public internet radio catalog with gradual regional enrichment
- Short-lived model lease pool instead of per-request backend chat routing
- AI replies synthesized into broadcast audio when a model lease is available

## Quick start

### Radio app

```bash
cd apps/radio-app
npm install
npm run web
```

### Backend API

```bash
cd apps/backend-api
npm install
npm run build
node dist/main.js
```

### Admin console

```bash
cd apps/admin-console
npm install
npm run dev
```

This console is frozen for legacy compatibility. New admin/control-plane work belongs in `home/`.
