# TV Client + Home App Pool / Lease Alignment Implementation Plan

> Status
> This document remains the main client-side implementation baseline.
> The current top-level boundary, sequencing, and document precedence now live in:
> - [2026-04-22-home-openclaw-latest-guidance.md](C:/Users/soulzyn/Desktop/codex/home/docs/2026-04-22-home-openclaw-latest-guidance.md)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the current Kotlin Android TV baseline so it can consume a `home`-managed app pool, support idle background app downloads, auto-create device-side user identity, and participate in a payment-aware lease queue without moving control-plane logic into the client.

**Architecture:** Keep `home` as the only control plane. The TV client remains a thin runtime that bootstraps, caches manifests, downloads packages in the background, requests a resource session, renews it, and degrades cleanly offline. On the server side, keep `app account lease` and `model lease` as separate internal resource pools, but expose a simpler client-facing `resource session` contract so the TV client does not need to coordinate two queues by itself.

**Tech Stack:** Kotlin, Android native Views, ViewModel, DataStore, WorkManager, DownloadManager, OkHttp, Kotlinx Serialization, Fastify, TypeScript, Postgres, SQL migrations, current `home/apps/platform-api`

---

## Current State Snapshot

### Client already has

- Native Kotlin Android TV workspace under `apps/android-tv-client-kotlin`
- `bootstrap` orchestration
- session / lease / identity / home-config local storage
- low-spec home shell
- remote `tv-home-config` fallback chain
- basic upgrade shell and APK download/install prompt
- local offline actions such as Wi-Fi, settings, local files, and installed app launch

### Client still missing

- app pool manifest contract
- idle background app download scheduler
- app package download state persistence
- payment / entitlement state persistence
- queue orchestration for resource access
- client-facing resource session state machine
- broadcast or manifest-delta consumption
- explicit anonymous device user lifecycle beyond current bootstrap session

### Home already has

- client bootstrap
- client policy
- client release lookup
- model lease issue / renew / release
- shared control plane repository and Postgres migration path

### Home still missing

- public gateway exposure for all required TV routes
- `tv-home-config` implementation
- app pool / manifest service
- payment / entitlement summary for TV client
- payment-aware queue scheduler
- app account pool
- client-facing resource-session endpoints

## Planning Principles

- Do not create a second control plane in the TV repo.
- Do not let the TV client hold raw payment credentials.
- Do not couple app package delivery with silent installation; silent install depends on vendor/system capability.
- Do not expose two independent queues to the TV client. The server may schedule two pools internally, but the client should see one resource-session lifecycle.
- Keep offline behavior first-class. If `home` is slow or down, the home shell still boots and local operations still work.

## Target Responsibility Split

### Home owns

- app pool metadata
- package release metadata
- package delivery policy by country / region / client version
- device user identity
- payment state and entitlement state
- queue priority and fairness
- app account pool
- model pool
- resource-session scheduling
- runtime manifest and broadcast event generation

### TV client owns

- device bootstrap
- local caching
- background download execution
- queue request / polling / renew / release
- local install prompt or vendor install handoff
- offline shell and fallback behavior
- displaying account / entitlement / payment state summaries returned by `home`

## Task 1: Freeze the contract surface for TV runtime, app pool, and resource session

**Files:**
- Create: `docs/architecture/2026-04-20-tv-runtime-manifest-and-resource-session-contract.md`
- Reference: `docs/architecture/2026-04-20-android-tv-offline-baseline-and-home-contract.md`
- Reference: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\client.ts`

**Define these client-facing contracts:**

- `GET /api/me/tv-home-config`
- `GET /api/me/runtime-manifest`
- `GET /api/me/entitlement`
- `POST /api/client/resource-session/request`
- `GET /api/client/resource-session/status`
- `POST /api/client/resource-session/renew`
- `POST /api/client/resource-session/release`

**Contract rules:**

- `runtime-manifest` returns only lightweight metadata, never raw binaries
- `resource-session` is the client-facing queue and grant surface
- response contains:
  - `queueStatus`
  - `appAccountLease`
  - `modelLease`
  - `entitlementSummary`
  - `priorityClass`
  - `expiresAt`
- `home` may schedule app-account and model resources separately internally
- legacy `/api/client/model-lease*` remains for compatibility until migration is complete

**Acceptance criteria:**

- Both repos use the same DTO names and field semantics
- Error codes are aligned
- Queue states are finite and documented

## Task 2: Update the TV client plan around the missing runtime pieces

**Files:**
- Modify: `docs/plans/2026-04-15-kotlin-android-tv-rewrite-plan.md`
- Reference: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Reference: `apps/android-tv-client-kotlin/feature/bootstrap`
- Reference: `apps/android-tv-client-kotlin/feature/home`

**Add these follow-on client modules or slices:**

- `core/network`
  - add DTOs for `RuntimeManifest`, `EntitlementSummary`, `ResourceSession`
  - add API methods for the new `me/*` and `client/resource-session/*` routes
- `core/storage`
  - add `ManifestStore`
  - add `EntitlementStore`
  - add `ResourceSessionStore`
  - add `AppDownloadStore`
- `feature/appdelivery`
  - manifest repository
  - idle download scheduler
  - download state reducer
  - package verification
- `feature/runtime`
  - resource-session request / poll / renew / release coordinator
  - queue status model
  - offline fallback rules
- `feature/home`
  - surface manifest-driven app recommendations
  - reflect entitlement / queue / install states without adding an ops/debug page

**Client gaps that must be treated as required work:**

- anonymous device user bootstrap summary
- manifest polling and cache invalidation
- idle-only package download policy
- queue join and resume after process restart
- resource-session renew and recovery after transient network failure
- entitlement-aware UI states on the home screen

## Task 3: Build the `home` app pool and runtime manifest layer

**Files:**
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\me.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\app.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-tv-runtime-service.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-app-pool-service.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-schema.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-state-repository.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\control-plane-postgres-repository.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\migrations\0006_tv_runtime_manifest.sql`

**Server objects to add:**

- `tv_runtime_manifests`
- `app_catalog_entries`
- `app_release_packages`
- `app_delivery_rules`

**`runtime-manifest` should contain:**

- `manifestVersion`
- `countryCode`
- `regionCode`
- `apps[]`
- per app:
  - `appId`
  - `title`
  - `packageName`
  - `downloadUrl`
  - `sha256`
  - `versionCode`
  - `versionName`
  - `minClientVersion`
  - `installMode`
  - `visibility`
  - `preloadPolicy`
  - `requiresEntitlement`
- `pollAfterSeconds`
- `eventCursor`

**Acceptance criteria:**

- TV client can fetch a lightweight manifest
- manifest can be versioned and cached
- country / region policy can hide or enable entries

## Task 4: Build `home` entitlement, payment summary, and payment-aware priority classes

**Files:**
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-entitlement-service.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\me.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-schema.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\control-plane-postgres-repository.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\migrations\0007_entitlement_and_payment_summary.sql`

**Server objects to add:**

- `device_users`
- `customer_payment_profiles`
- `entitlements`
- `priority_classes`

**Rules to encode:**

- paid active users rank above unpaid users
- grace-period paid users rank above free users
- same priority class uses FIFO
- add aging so low-priority users are not starved forever
- TV client receives summary only:
  - `accountId`
  - `displayId`
  - `planCode`
  - `paymentState`
  - `priorityClass`
  - `renewalState`

**Non-goals:**

- raw card data in TV client
- payment processor secrets in TV repo

## Task 5: Build `home` resource-session scheduling on top of app-account and model pools

**Files:**
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-app-account-lease-service.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-resource-session-service.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\client.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\control-plane-schema.ts`
- Modify: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\control-plane-postgres-repository.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\lib\postgres\migrations\0008_resource_session_queue.sql`

**Server objects to add:**

- `app_account_pools`
- `app_account_leases`
- `resource_session_queue`
- `resource_sessions`

**Scheduling model:**

- queue request enters one client-facing session queue
- scheduler determines:
  - whether app-account lease is needed
  - whether model lease is needed
  - whether both are available now
- if both are available, grant one `resourceSession`
- if not, return queue status with estimated wait and position

**Why this shape:**

- internal resource types stay independent
- TV client only drives one state machine
- payment priority is applied in one place

## Task 6: Add TV client background download and resource-session runtime

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/RuntimeManifestRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/AppDownloadScheduler.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/AppPackageDownloader.kt`
- Create: `apps/android-tv-client-kotlin/feature/runtime/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionCoordinator.kt`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionState.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Modify: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/OpenClawTvApplication.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`

**Client runtime rules:**

- app downloads only run when device is idle enough
- failed downloads retry with backoff
- manifest updates do not interrupt foreground use
- queue state is persisted across process death
- resource-session renew runs in the background while session is active
- if server disappears, current local shell keeps working but resource-session enters degraded state

**Install boundary:**

- if no vendor/system install capability exists:
  - download package
  - verify checksum
  - mark as ready-to-install
  - prompt user at an appropriate time
- if vendor install capability exists later:
  - plug in a board-specific installer adapter

## Task 7: Align interfaces and tests across both repos

**Files:**
- Create: `apps/android-tv-client-kotlin/core/network/src/test/java/com/openclaw/tv/core/network/RuntimeManifestContractTest.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/test/java/com/openclaw/tv/core/network/ResourceSessionContractTest.kt`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\__tests__\me-routes.test.ts`
- Create: `C:\Users\soulzyn\Desktop\codex\home-sticky-lease-deploy\apps\platform-api\src\routes\__tests__\resource-session-routes.test.ts`
- Modify: `docs/architecture/2026-04-20-tv-runtime-manifest-and-resource-session-contract.md`

**Interface rules that must match exactly:**

- enum names for queue state
- enum names for payment state
- required vs optional fields
- timestamp format
- checksum field names
- retry / poll interval semantics
- error code names

**Required queue states:**

- `not_requested`
- `queued`
- `allocating`
- `granted`
- `degraded`
- `expired`
- `released`
- `rejected`

**Required payment states:**

- `unknown`
- `free`
- `pending`
- `paid`
- `grace_period`
- `suspended`

## Task 8: Update release sequencing and execution order

**Files:**
- Modify: `docs/plans/2026-04-15-kotlin-android-tv-rewrite-plan.md`
- Reference: `docs/plans/2026-04-20-tv-client-home-app-pool-lease-alignment-plan.md`

**Recommended execution order:**

1. Finish public route exposure and `tv-home-config`
2. Freeze `runtime-manifest` and `resource-session` contract
3. Build `home` manifest + entitlement summary endpoints
4. Build `home` queue + resource-session service
5. Add TV client manifest cache + downloader
6. Add TV client resource-session runtime
7. Wire home-screen surfaces for install / payment / queue states
8. Only after that, revisit voice / summon overlay / advanced control tasks

## Updated Assessment

### What the client is still missing

- The client is currently a strong offline shell plus bootstrap runtime.
- It is not yet a real app-pool consumer.
- It does not yet know how to:
  - cache an app manifest
  - background download packages
  - persist entitlement state
  - join a payment-aware queue
  - recover a resource session after restart

### What `home` must do next

- First finish TV public route exposure and `tv-home-config`
- Then become the single source of truth for:
  - app pool
  - runtime manifest
  - payment summary
  - entitlement summary
  - queue priority
  - resource-session issuance

### How both sides should align on the interface

- The client sees a single `resource-session` contract.
- The server remains free to internally schedule `app account lease` and `model lease` independently.
- All metadata needed for background app delivery is served by `runtime-manifest`.
- Payment information is exposed as summary state, not raw payment credentials.

Plan complete and saved to `docs/plans/2026-04-20-tv-client-home-app-pool-lease-alignment-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints
