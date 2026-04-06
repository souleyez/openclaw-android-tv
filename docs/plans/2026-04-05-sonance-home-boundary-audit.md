# Sonance / home Boundary Audit

Date: 2026-04-05

## Verdict

The relationship is only partially clarified in implementation.

- Architecture and repo-boundary documents already define `home` as the only shared platform repository.
- `home` already contains a real shared platform API and public admin surface.
- `Sonance` still carries a transitional local backend and local admin console that include both:
  - true Sonance application-domain logic
  - duplicated platform-style control-plane capabilities

So the current state is:

- boundary is clear in docs
- boundary is not fully enforced in code
- Sonance is still in migration mode, not yet a pure application-side consumer of `home`

## Canonical target split

### Must belong to `home`

These are shared platform capabilities and should not keep growing inside Sonance:

- shared admin login and admin sessions
- project catalog and project switching
- shared model provider pool and lease policy
- shared release control
- shared config-release control
- shared audit log and governance views
- project integration health checks
- platform broadcast dispatch

### Must remain inside `Sonance`

These are Sonance application-domain capabilities and should stay owned by Sonance:

- radio client runtime
- local-first station switching behavior
- Sonance-specific radio catalog and enrichment rules
- Sonance-specific AI broadcast generation
- Sonance-specific station health and fallback logic
- Sonance-specific upload flow and broadcast timeline behavior
- Sonance client-side update consumption logic

### Important nuance

Some capabilities are project-scoped but still belong in `home` as control-plane surfaces.

Examples:

- Sonance release records
- Sonance config releases
- Sonance project devices
- Sonance project sessions
- Sonance project governance status

These should appear in a Sonance workspace inside `home`, not in a second standalone admin console inside the Sonance repo.

## Current code reality

### Evidence that `home` is already the shared platform

- `home/apps/platform-api`
- `home/app`
- `home/docs/project-integration-contract.md`
- `home/docs/project-workspace-guidelines.md`

`home` already implements:

- shared project integrations
- shared project admin workspaces
- shared release and broadcast envelope logic
- shared health/broadcast integration contract

### Evidence that Sonance still duplicates platform surfaces

Sonance still contains:

- `apps/backend-api/src/modules/admin`
- `apps/backend-api/src/modules/model-router`
- `apps/backend-api/src/modules/ota`
- `apps/admin-console/app/api-pool`
- `apps/admin-console/app/device-users`
- `apps/admin-console/app/orders`
- `apps/admin-console/app/finance`
- `apps/admin-console/app/risk`
- `apps/admin-console/app/logs`
- `apps/admin-console/app/ota`

This means Sonance still hosts a second control-plane surface instead of only exposing application runtime plus a fixed platform contract.

## What should happen to Sonance modules

### Keep and continue

- `apps/radio-app/**`
- Sonance-specific radio runtime in `apps/backend-api/src/modules/radio/**`
- Sonance-specific AI broadcast flow
- Sonance-specific upload handling
- Sonance-specific project data storage that backs the runtime

### Freeze, then migrate to `home`

- `apps/backend-api/src/modules/admin/**`
- `apps/backend-api/src/modules/model-router/**`
- `apps/backend-api/src/modules/ota/**`
- most of `apps/admin-console/**`

These should not keep evolving as a second general-purpose platform.

### Reframe rather than delete

Some Sonance backend data can remain in Sonance even after admin migration.

Example:

- Sonance-specific radio stations
- Sonance-specific broadcasts
- Sonance-specific subscription/order records if they are only meaningful to Sonance

But the operator-facing surface for those records should eventually be presented from the Sonance project workspace in `home`, not from a separate Sonance admin app.

## Concrete missing integration pieces

The biggest technical gap is that Sonance has not fully adopted the fixed `home` integration contract yet.

Expected contract from `home` docs:

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`
- shared secret header `x-home-platform-token`
- app env `HOME_PLATFORM_TOKEN`

Current Sonance repo status:

- no confirmed `internal/platform` endpoints in `apps/backend-api/src`
- no confirmed `HOME_PLATFORM_TOKEN` integration path in the Sonance backend
- no clear Sonance-side project integration bootstrap against `home`

This is the line that turns the architecture from “documented” into “real”.

## Operational risks right now

### 1. Double control plane risk

If the Sonance admin console keeps growing while `home` also grows, the same governance concepts will diverge:

- releases
- leases
- admin users
- audit logs
- device/project status

### 2. Ownership ambiguity

Operators will not know whether a release or model-pool action should happen in:

- Sonance admin
- `home` public admin

That creates process drift even if both systems technically work.

### 3. Migration drag

The longer Sonance keeps local admin pages alive, the harder it becomes to shut them down because users will start depending on them as permanent workflow surfaces.

## Recommended migration order

### Phase 1: boundary hardening

- stop adding new platform-style capabilities to Sonance
- mark Sonance admin/backend control-plane parts as transitional
- fix Sonance README and admin copy so the repo no longer reads like a shared platform

### Phase 2: contract completion

- implement `GET /internal/platform/health` in Sonance backend
- implement `POST /internal/platform/broadcasts` in Sonance backend
- add `HOME_PLATFORM_TOKEN` handling
- register Sonance in `home` using the canonical project integration flow

### Phase 3: operator surface migration

- move release/config publishing workflow into the Sonance workspace in `home`
- move model-pool operator workflow into `home`
- move project health/governance checks into `home`
- reduce Sonance local admin to either:
  - a temporary compatibility shell, or
  - remove it entirely

### Phase 4: final Sonance shape

Sonance should end up as:

- app client
- app runtime backend
- fixed `home` integration contract
- no second shared control plane

## Practical bottom line

The relationship is clarified enough to choose direction, but not clarified enough to call the migration finished.

Current score:

- document-level clarity: high
- implementation-level clarity: medium
- enforced boundary in code: low to medium

In plain terms:

- yes, we now know that `home` is supposed to be the public/shared backend
- no, Sonance has not yet been reduced to a clean consumer of that backend
