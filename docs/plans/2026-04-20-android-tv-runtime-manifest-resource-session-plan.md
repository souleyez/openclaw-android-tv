# Android TV Runtime Manifest + Resource Session Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate `openclaw-android-tv` from the current `tv-home-config + model-lease` baseline to the frozen `home` runtime contract so the TV client consumes `tv-home-config`, `runtime-manifest`, `entitlement`, and a single `resource-session` lifecycle without taking control-plane logic into the client.

**Architecture:** Keep `home` as the only control plane and keep the Android TV app as a thin runtime. `tv-home-config` becomes path and cadence metadata only, `runtime-manifest` becomes the only source for app delivery and ad creatives, `entitlement` becomes summary-only user state, and `resource-session` becomes the only queue/grant contract visible to the client. The TV app owns fixed `slotId` rendering, manifest caching, offline fallback, background downloads, and request or renew or release behavior, while `home` keeps the internal split between app-account and model scheduling.

**Tech Stack:** Kotlin, Android native Views, ViewModel, OkHttp, Kotlinx Serialization, DataStore, WorkManager, DownloadManager, JUnit, MockWebServer, current `apps/android-tv-client-kotlin` modules, and the frozen `home` DTO contract under `C:\Users\soulzyn\Desktop\codex\home`

---

## Coordination Gates

- Treat `C:\Users\soulzyn\Desktop\codex\home\docs\2026-04-20-android-tv-runtime-handoff.md` as the product boundary.
- Treat `C:\Users\soulzyn\Desktop\codex\home\docs\architecture\2026-04-20-openclaw-tv-runtime-contract.md` as the API source of truth.
- Do not add TV operator pages, do not re-expand `home` UI, and do not push app metadata or ad layout back into `tv-home-config`.
- Client-side contract, storage, and UI preparation can start immediately with MockWebServer and fake repositories.
- End-to-end device verification for manifest, entitlement, and resource-session must wait until the matching `home` routes exist on the target environment.

## Current Android TV Baseline

- `apps/android-tv-client-kotlin/core/network` already talks to:
  - `POST /client/bootstrap/auth`
  - `GET /me/tv-home-config`
  - `GET /client/policy`
  - `GET /client/releases/latest`
  - `POST /client/model-lease`
  - `GET /client/model-lease/status`
  - `POST /client/model-lease/renew`
  - `POST /client/model-lease/release`
- `apps/android-tv-client-kotlin/core/storage` already persists:
  - session
  - device identity
  - lease
  - tv-home-config
  - upgrade state
- `apps/android-tv-client-kotlin/feature/bootstrap` already owns bootstrap, policy, upgrade, and legacy lease restore.
- `apps/android-tv-client-kotlin/feature/home` already renders an offline-first shell with featured apps, quick actions, Wi-Fi state, and config fallback.

## Task 1: Freeze the Android TV client-side route and DTO contract

**Files:**
- Modify: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/PlatformApi.kt`
- Modify: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/TvHomeDtos.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/TvRuntimeDtos.kt`
- Modify: `apps/android-tv-client-kotlin/core/network/src/test/java/com/openclaw/tv/core/network/PlatformApiContractTest.kt`
- Reference: `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api\src\lib\control-plane-tv-contract.ts`
- Reference: `C:\Users\soulzyn\Desktop\codex\home\docs\architecture\2026-04-20-openclaw-tv-runtime-contract.md`

**Step 1: Write the failing contract test**

Extend `PlatformApiContractTest.kt` so it locks these public client calls:

- `GET /me/tv-home-config`
- `GET /me/runtime-manifest`
- `GET /me/entitlement`
- `POST /client/resource-session/request`
- `GET /client/resource-session/status`
- `POST /client/resource-session/renew`
- `POST /client/resource-session/release`

Also lock the enum payload values for:

- queue states:
  - `not_requested`
  - `queued`
  - `allocating`
  - `granted`
  - `degraded`
  - `expired`
  - `released`
  - `rejected`
- payment states:
  - `unknown`
  - `free`
  - `pending`
  - `paid`
  - `grace_period`
  - `suspended`

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :core:network:testDebugUnitTest
```

Expected: FAIL because the new DTOs and methods do not exist yet.

**Step 3: Write the minimal implementation**

Update the client network layer so it contains:

- a new `TvHomeConfigDto` that matches the frozen `home` shape:
  - `projectKey`
  - `projectLabel`
  - `runtimeManifestPath`
  - `entitlementPath`
  - `resourceSessionBasePath`
  - `manifestPollAfterSeconds`
  - `resourceSessionPollAfterSeconds`
  - `backgroundDownloadEnabled`
  - `idleDownloadOnly`
- a new `TvRuntimeDtos.kt` with:
  - `TvRuntimeManifestDto`
  - `TvRuntimeManifestAppDto`
  - `TvRuntimeAdSlotDto`
  - `TvRuntimeAdCreativeDto`
  - `TvEntitlementSummaryDto`
  - `TvResourceSessionDto`
  - nested app-account and model-lease DTOs
- new `PlatformApi` methods for manifest, entitlement, and resource-session
- legacy `model-lease` methods left in place until migration completes

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/core/network
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: freeze android tv runtime dto contract"
```

## Task 2: Add persistent stores for manifest, entitlement, resource-session, and app downloads

**Files:**
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/RuntimeManifestStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/EntitlementStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/ResourceSessionStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/AppDownloadStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/test/java/com/openclaw/tv/core/storage/RuntimeManifestStoreTest.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/test/java/com/openclaw/tv/core/storage/ResourceSessionStoreTest.kt`
- Modify: `apps/android-tv-client-kotlin/core/storage/build.gradle.kts`

**Step 1: Write the failing persistence tests**

Add tests that save and reload:

- manifest version, app list, and `adSlots`
- entitlement summary and payment state
- resource-session status, queue position, and lease expiry
- app download state per `appId`, including status, checksum target, and local file path

**Step 2: Run tests to verify they fail**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :core:storage:testDebugUnitTest
```

Expected: FAIL because the new stores do not exist yet.

**Step 3: Write the minimal implementation**

Create DataStore-backed stores with small JSON payloads and explicit defaults:

- `RuntimeManifestStore` caches the last successful manifest plus fetch timestamp
- `EntitlementStore` caches only the summary DTO returned by `home`
- `ResourceSessionStore` persists the current session view and local poll metadata
- `AppDownloadStore` persists a per-app reducer state for:
  - `not_started`
  - `queued`
  - `downloading`
  - `downloaded`
  - `verifying`
  - `ready_to_install`
  - `failed`

Do not store layout coordinates or ad slot measurements in any store.

**Step 4: Run tests to verify they pass**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/core/storage
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add runtime manifest and session stores"
```

## Task 3: Introduce a dedicated runtime-session module without removing bootstrap compatibility

**Files:**
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/runtime/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionCoordinator.kt`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionPollPolicy.kt`
- Create: `apps/android-tv-client-kotlin/feature/runtime/src/test/java/com/openclaw/tv/feature/runtime/ResourceSessionCoordinatorTest.kt`
- Modify: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/BootstrapRuntime.kt`
- Modify: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/BootstrapRepository.kt`

**Step 1: Write the failing coordinator test**

Add a `ResourceSessionCoordinatorTest.kt` that verifies:

- request starts from `not_requested`
- polling advances through `queued` and `allocating`
- granted responses persist both `appAccountLease` and `modelLease`
- renew reuses `resourceSessionId`
- release clears the local session store
- transient HTTP failures degrade locally instead of crashing the app shell

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:runtime:testDebugUnitTest
```

Expected: FAIL because the module and coordinator do not exist yet.

**Step 3: Write the minimal implementation**

Create a new runtime module that:

- reads `StoredSession` from `SessionStore`
- talks only to `resource-session` endpoints for queue or grant behavior
- persists the latest `TvResourceSessionDto`
- exposes a small state machine for:
  - request
  - poll
  - renew
  - release
  - resume-after-restart
- leaves `feature/bootstrap` responsible for auth, policy, and release checks only

Do not delete legacy lease code yet. Gate old code behind compatibility paths until the home route is live.

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/settings.gradle.kts apps/android-tv-client-kotlin/feature/runtime apps/android-tv-client-kotlin/feature/bootstrap
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add resource session runtime coordinator"
```

## Task 4: Add app-delivery orchestration for manifest refresh and idle background downloads

**Files:**
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/RuntimeManifestRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/ManifestRefreshScheduler.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/AppDownloadCoordinator.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/IdleDownloadPolicy.kt`
- Create: `apps/android-tv-client-kotlin/feature/appdelivery/src/test/java/com/openclaw/tv/feature/appdelivery/RuntimeManifestRepositoryTest.kt`
- Modify: `apps/android-tv-client-kotlin/app/build.gradle.kts`
- Modify: `apps/android-tv-client-kotlin/gradle/libs.versions.toml`

**Step 1: Write the failing delivery tests**

Add tests that verify:

- manifest refresh honors `manifestPollAfterSeconds`
- `adSlots` are accepted as an array, including empty arrays
- unknown `slotId` values remain in manifest cache but do not break downstream consumers
- idle-only downloads do not enqueue while the device is considered active
- checksum mismatch marks a download as failed and does not expose it as installable

**Step 2: Run tests to verify they fail**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:appdelivery:testDebugUnitTest
```

Expected: FAIL because the module and repository do not exist yet.

**Step 3: Write the minimal implementation**

Create an app-delivery feature that:

- fetches `runtime-manifest` through `PlatformApi`
- caches the manifest in `RuntimeManifestStore`
- computes a delta by `appId`, `versionCode`, and `sha256`
- schedules background downloads only when:
  - `backgroundDownloadEnabled == true`
  - `idleDownloadOnly == false`, or the idle policy says the device is idle
- keeps install execution separate from download completion

Do not add silent install assumptions. The output of this slice is `downloaded and verified`, not `installed`.

**Step 4: Run tests to verify they pass**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/settings.gradle.kts apps/android-tv-client-kotlin/feature/appdelivery apps/android-tv-client-kotlin/app/build.gradle.kts apps/android-tv-client-kotlin/gradle/libs.versions.toml
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add runtime manifest app delivery flow"
```

## Task 5: Rewire the home screen to manifest-driven apps, entitlement state, and fixed ad slots

**Files:**
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/TvHomeRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeAdSlotRegistry.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeRuntimePresenter.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/AppRailAdapter.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/item_app_rail.xml`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/TvHomeRepositoryTest.kt`

**Step 1: Write the failing UI and repository tests**

Add tests that verify:

- featured apps come from `runtime-manifest.apps`, not from `tv-home-config.featuredAppIds`
- `tv-home-config` only provides path and cadence inputs used by the runtime layer
- entitlement summary changes the home status copy without exposing raw payment details
- `adSlots` empty array renders no ad and no error
- unknown `slotId` is ignored
- slot present but no active creative renders nothing

**Step 2: Run tests to verify they fail**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:home:testDebugUnitTest
```

Expected: FAIL because `feature/home` still assumes the old `tv-home-config` content model.

**Step 3: Write the minimal implementation**

Refactor the home feature so it:

- treats `tv-home-config` as boot metadata only
- reads manifest-driven app cards from `RuntimeManifestStore`
- reads payment and priority summaries from `EntitlementStore`
- reads queue and grant summaries from `ResourceSessionStore`
- resolves ads through a fixed `HomeAdSlotRegistry`
- ignores unknown slot ids and inactive creatives

The slot registry should be the only place that knows TV-local placement constants.

**Step 4: Run tests to verify they pass**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/home
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: render home from runtime manifest and entitlement"
```

## Task 6: Wire the new runtime modules into the app shell and verify on device

**Files:**
- Modify: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/OpenClawTvApplication.kt`
- Modify: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/MainActivity.kt`
- Modify: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/MainRouteResolver.kt`
- Modify: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/BootstrapRuntimeOwner.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/androidTest/java/com/openclaw/tv/feature/home/HomeScreenTest.kt`
- Modify: `apps/android-tv-client-kotlin/scripts/capture-tv-home.ps1`

**Step 1: Write the failing integration assertions**

Extend `HomeScreenTest.kt` and the device capture script so they check:

- bootstrap still reaches the home shell
- manifest-backed app entries render
- ad slot absence does not crash rendering
- queue or entitlement badges are visible when test data is injected
- offline fallback still works when manifest fetch fails after a prior successful cache

**Step 2: Run local tests to verify current gaps**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :app:testDebugUnitTest :feature:home:connectedDebugAndroidTest
```

Expected: FAIL or partial coverage because the app shell is not wired to the new runtime modules yet.

**Step 3: Write the minimal integration**

Update app wiring so:

- bootstrap auth remains first
- runtime manifest refresh starts after bootstrap succeeds
- resource-session polling is tied to authenticated app lifetime
- background download scheduling respects the `tv-home-config` toggles
- home UI subscribes to the new stores and runtime coordinators

Keep failure behavior conservative:

- if manifest is unavailable and there is cache, use cache
- if entitlement is unavailable, keep the last summary
- if resource-session is unavailable, show degraded state and keep the shell usable

**Step 4: Run verification**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :core:network:testDebugUnitTest :core:storage:testDebugUnitTest :feature:runtime:testDebugUnitTest :feature:appdelivery:testDebugUnitTest :feature:home:testDebugUnitTest :app:testDebugUnitTest
gradlew.bat :feature:home:connectedDebugAndroidTest
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\capture-tv-home.ps1
```

Expected:

- unit tests PASS
- connected home test PASS
- capture script produces updated screenshots or XML artifacts for handoff review

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app apps/android-tv-client-kotlin/feature/bootstrap apps/android-tv-client-kotlin/scripts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: wire runtime manifest and resource session into tv app"
```

## Delivery Order

1. Do Task 1 and Task 2 immediately because they only depend on the frozen contract and local tests.
2. Start Task 3 in parallel with `home` route work, but keep it behind fake repositories until `/api/client/resource-session/**` exists.
3. Start Task 4 once `GET /api/me/runtime-manifest` is available on a dev environment or adequately mocked.
4. Start Task 5 after the manifest and entitlement shapes are frozen in the target environment.
5. Finish with Task 6 only after the target backend has all three runtime routes live:
   - `GET /api/me/runtime-manifest`
   - `GET /api/me/entitlement`
   - `/api/client/resource-session/**`

## Non-Goals

- No new TV-side operator console.
- No TV-side storage of raw payment credentials.
- No ad layout coordinates from `home`.
- No silent install requirement in this slice.
- No direct exposure of internal app-account-pool or model-lease scheduling details in the home screen.
