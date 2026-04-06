# Sonance Control-Plane Freeze

Date: 2026-04-06

## Decision

Sonance no longer owns the active control plane.

The active control-plane workspace is:

- `C:\Users\soulzyn\Desktop\codex\home`

Sonance keeps:

- runtime radio APIs
- runtime uploads
- runtime AI broadcast generation
- `home` integration endpoints

Sonance no longer grows:

- local admin surfaces
- local model lease policy/features
- local OTA/config release features
- shared project governance features

## What Is Frozen In Sonance

Frozen compatibility surfaces:

- `apps/admin-console`
- `apps/backend-api/src/modules/model-router`
- `apps/backend-api/src/modules/ota`
- `apps/backend-api/src/modules/admin`

Allowed changes in frozen areas:

- compatibility fixes
- rollback-safe maintenance
- bug fixes required to keep old paths usable during migration
- emergency operational patches

Disallowed changes in frozen areas:

- new features
- new product workflows
- new admin pages
- new release policy logic
- new multi-project governance logic

## Active Development Ownership

Develop in `home` when changing:

- `/api/router/**`
- `/api/ota/**`
- `/api/admin/**`
- unified admin UI
- project integrations
- model lease strategy
- release/config rollout control

Develop in Sonance when changing:

- `/api/radio/**`
- `/uploads/radio/**`
- `/internal/platform/**`
- radio catalog enrichment
- broadcast upload/runtime playback flows
- Sonance client local intelligence

## Production Routing

Unified gateway host:

- `http://1.12.246.48`

Route ownership:

- `/api/router/**` -> `home`
- `/api/ota/**` -> `home`
- `/api/admin/**` -> `home`
- `/api/admin-auth/**` -> `home`
- `/api/radio/**` -> Sonance runtime
- `/uploads/radio/**` -> Sonance runtime
- `/internal/platform/**` -> Sonance runtime

## Enforcement Rule

If a new task is described as "后台", "租约", "更新", "控制面", or "管理台", default to `home` first.

Only continue work in Sonance-local control-plane code when the task is explicitly a compatibility fix or a migration unblocker.
