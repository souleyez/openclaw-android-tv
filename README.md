# Sonance

Sonance is a minimal single-screen radio app built with Expo and backed by a lightweight NestJS service.

Current workspace focus:

- `apps/radio-app`
  Expo client for Android, iOS, and Web
- `apps/backend-api`
  Backend for model lease pooling, billing skeleton, radio catalog, AI broadcast generation, and admin monitoring
- `docs/plans/2026-03-26-radio-app-design.md`
  Product and technical design notes for the current app direction

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
