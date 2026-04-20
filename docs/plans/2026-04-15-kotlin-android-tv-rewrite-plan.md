# Kotlin Android TV Rewrite Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current Flutter-based TV client with a native Kotlin Android TV client that consumes the existing `home` platform APIs, while keeping the current Flutter client alive until the native client reaches feature parity.

**Architecture:** Build a new parallel Android project at `apps/android-tv-client-kotlin` instead of rewriting in place. For higher-end boards, Compose for TV remains acceptable, but for the current `RK3128 + 1GB/4GB` target the baseline shell should use low-overhead native Views, `RecyclerView`, and explicit focus management. Keep Media3 for media/session integration, Kotlin coroutines + ViewModel for orchestration, a transparent `AssistantActivity` for summon-time foreground animation, a vendor voice-front-end integration layer for board-level wake and clean-audio capture, and a thin platform contract layer targeting the current `home/apps/platform-api` client routes. Do not move policy, lease strategy, or shared governance into the client.

**Tech Stack:** Kotlin, Android Gradle Plugin, native Views, `RecyclerView`, optional Leanback-style focus patterns, AndroidX Lifecycle/ViewModel, Retrofit or Ktor client, OkHttp, Kotlinx Serialization, Media3, DataStore, Room, Hilt, AIDL or bound system service integration for vendor voice front-end, foreground service, JUnit, Robolectric, Espresso UI Test

**Supplement:** For the newer app-pool, payment-priority queue, and `home` resource-session alignment scope, also follow `docs/plans/2026-04-20-tv-client-home-app-pool-lease-alignment-plan.md`.

For the low-spec board profile, also follow:

- `docs/architecture/2026-04-15-rk3128-low-spec-architecture.md`
- `docs/ops/2026-04-15-rk3128-board-engineer-worklist.md`

## Additional Product Requirements Locked Into This Rewrite

- **Playback-aware voice capture**
  - The client must remain usable while local media is playing.
  - The primary implementation is board-level full-duplex voice processing supplied by the projector mainboard vendor.
  - The target behavior is:
    - keep third-party and local playback running
    - use system playback audio as render reference
    - output cleaned mic audio that suppresses or cancels local playback bleed
    - support barge-in while media is active
  - App-layer duck or pause remains fallback only, not default behavior.

- **Summon animation in front of the current UI**
  - The chosen implementation is:
    - launch a transparent or semi-transparent `AssistantActivity`
    - render the animated assistant shell above the current foreground experience
    - route voice state, partial transcript, and execution state into this shell
  - This is not a `SYSTEM_ALERT_WINDOW` style global overlay requirement.

---

### Task 1: Lock the rewrite boundary and create the native app workspace

**Files:**
- Create: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Create: `apps/android-tv-client-kotlin/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/gradle.properties`
- Create: `apps/android-tv-client-kotlin/gradle/libs.versions.toml`
- Create: `apps/android-tv-client-kotlin/README.md`
- Modify: `README.md`
- Reference: `docs/APP_BOUNDARY_2026-04-05.md`

**Step 1: Write the boundary note**

In `apps/android-tv-client-kotlin/README.md`, state:

- this is the new native client
- `apps/android-tv-client` stays as the frozen Flutter transition client
- all shared backend capability comes from `home`

**Step 2: Run a repo sanity check**

Run:

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv status --short
```

Expected: clean worktree before scaffolding the new project.

**Step 3: Create the empty Gradle workspace**

Add the root Gradle files for a new Android project under `apps/android-tv-client-kotlin`.

**Step 4: Verify Gradle can enumerate the project**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat projects
```

Expected: PASS and list at least the root project.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add README.md apps/android-tv-client-kotlin
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "chore: scaffold native android tv workspace"
```

### Task 2: Create the Android TV app shell

**Files:**
- Create: `apps/android-tv-client-kotlin/app/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/app/proguard-rules.pro`
- Create: `apps/android-tv-client-kotlin/app/src/main/AndroidManifest.xml`
- Create: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/MainActivity.kt`
- Create: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/OpenClawTvApplication.kt`
- Create: `apps/android-tv-client-kotlin/app/src/main/res/values/strings.xml`
- Create: `apps/android-tv-client-kotlin/app/src/main/res/values/themes.xml`
- Create: `apps/android-tv-client-kotlin/app/src/main/res/xml/network_security_config.xml`
- Create: `apps/android-tv-client-kotlin/app/src/androidTest/java/com/openclaw/tv/AppLaunchTest.kt`

**Step 1: Write the failing launch test**

Create `AppLaunchTest.kt` that launches the app and asserts the root screen contains the app title.

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :app:connectedDebugAndroidTest
```

Expected: FAIL because the app module and activity do not exist yet.

**Step 3: Write minimal implementation**

Create an Android TV launcher app with:

- TV launcher intent filter
- `OpenClawTvApplication`
- `MainActivity`
- a placeholder native launcher layout

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add native android tv app shell"
```

### Task 3: Add the platform contract and network layer for `home`

**Files:**
- Create: `apps/android-tv-client-kotlin/core/network/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/PlatformApi.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/PlatformApiFactory.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/AuthInterceptor.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/BootstrapDtos.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/LeaseDtos.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/PolicyDtos.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/main/java/com/openclaw/tv/core/network/dto/ReleaseDtos.kt`
- Create: `apps/android-tv-client-kotlin/core/network/src/test/java/com/openclaw/tv/core/network/PlatformApiContractTest.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Modify: `apps/android-tv-client-kotlin/app/build.gradle.kts`

**Step 1: Write the failing contract test**

Create `PlatformApiContractTest.kt` that verifies the client targets the current `home` routes:

- `POST /client/bootstrap/auth`
- `GET /client/policy`
- `POST /client/model-lease`
- `GET /client/model-lease/status`
- `POST /client/model-lease/renew`
- `POST /client/model-lease/release`
- `GET /client/releases/latest`

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :core:network:testDebugUnitTest
```

Expected: FAIL because the network module does not exist.

**Step 3: Write minimal implementation**

Create a dedicated network module with:

- DTOs matching `home/apps/platform-api/src/routes/client.ts`
- base URL config
- auth token interceptor
- typed interface for all client routes

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/core/network apps/android-tv-client-kotlin/settings.gradle.kts apps/android-tv-client-kotlin/app/build.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add home platform contract client"
```

### Task 4: Add persistent app state and session storage

**Files:**
- Create: `apps/android-tv-client-kotlin/core/storage/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/SessionStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/DeviceStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/LeaseStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/main/java/com/openclaw/tv/core/storage/SettingsStore.kt`
- Create: `apps/android-tv-client-kotlin/core/storage/src/test/java/com/openclaw/tv/core/storage/SessionStoreTest.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`

**Step 1: Write the failing persistence test**

Create `SessionStoreTest.kt` that saves and reloads:

- bootstrap token
- device id
- active lease token
- selected locale

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :core:storage:testDebugUnitTest
```

Expected: FAIL because the storage module does not exist.

**Step 3: Write minimal implementation**

Use DataStore for:

- session tokens
- selected language
- selected avatar
- active lease metadata

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/core/storage apps/android-tv-client-kotlin/settings.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add native session and lease storage"
```

### Task 5: Build bootstrap, policy, and lease orchestration

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/bootstrap/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/BootstrapRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/BootstrapViewModel.kt`
- Create: `apps/android-tv-client-kotlin/feature/bootstrap/src/main/java/com/openclaw/tv/feature/bootstrap/LeaseCoordinator.kt`
- Create: `apps/android-tv-client-kotlin/feature/bootstrap/src/test/java/com/openclaw/tv/feature/bootstrap/LeaseCoordinatorTest.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Modify: `apps/android-tv-client-kotlin/app/build.gradle.kts`

**Step 1: Write the failing orchestration test**

Create `LeaseCoordinatorTest.kt` that verifies:

- bootstrap auth stores a session
- policy is fetched after auth
- lease is issued after policy
- lease renewal uses stored lease token
- release clears local lease state

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:bootstrap:testDebugUnitTest
```

Expected: FAIL because the feature module does not exist.

**Step 3: Write minimal implementation**

Implement a bootstrap flow that:

- authenticates the client against `home`
- fetches `policy`
- fetches `release`
- issues and stores a model lease
- exposes state to the UI as a single session model

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/bootstrap apps/android-tv-client-kotlin/settings.gradle.kts apps/android-tv-client-kotlin/app/build.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add bootstrap and lease orchestration"
```

### Task 6: Build the low-spec TV home shell with native Views

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/home/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/model/HomeUiState.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/AppRailAdapter.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/item_app_rail.xml`
- Create: `apps/android-tv-client-kotlin/feature/home/src/androidTest/java/com/openclaw/tv/feature/home/HomeScreenTest.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`
- Modify: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/MainActivity.kt`

**Step 1: Write the failing UI test**

Create `HomeScreenTest.kt` that asserts the native home shows:

- hero assistant section
- app rail
- large subtitle area
- focusable primary action

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:home:connectedDebugAndroidTest
```

Expected: FAIL because the feature and native home shell do not exist.

**Step 3: Write minimal implementation**

Use low-overhead native Views to build:

- top-level screen layout
- focus-safe app rail
- current subtitle / execution panel
- account and connection summary

Do not implement voice or control execution yet.

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/home apps/android-tv-client-kotlin/settings.gradle.kts apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/MainActivity.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add low-spec native tv home shell"
```

### Task 7: Rebuild playback-aware voice capture, summon overlay, and command execution in native Kotlin

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/voice/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/voice/src/main/java/com/openclaw/tv/feature/voice/VendorVoiceServiceClient.kt`
- Create: `apps/android-tv-client-kotlin/feature/voice/src/main/java/com/openclaw/tv/feature/voice/WakeEventBridge.kt`
- Create: `apps/android-tv-client-kotlin/feature/voice/src/main/java/com/openclaw/tv/feature/voice/SpeechPipelineCoordinator.kt`
- Create: `apps/android-tv-client-kotlin/feature/voice/src/main/java/com/openclaw/tv/feature/voice/VoiceCaptureSession.kt`
- Create: `apps/android-tv-client-kotlin/feature/voice/src/main/java/com/openclaw/tv/feature/voice/VoiceViewModel.kt`
- Create: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/assistant/AssistantActivity.kt`
- Create: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/assistant/AssistantOverlayViewModel.kt`
- Create: `apps/android-tv-client-kotlin/feature/control/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/control/src/main/java/com/openclaw/tv/feature/control/ControlExecutor.kt`
- Create: `apps/android-tv-client-kotlin/feature/control/src/main/java/com/openclaw/tv/feature/control/AppLauncher.kt`
- Create: `apps/android-tv-client-kotlin/feature/control/src/main/java/com/openclaw/tv/feature/control/KeyEventDispatcher.kt`
- Create: `apps/android-tv-client-kotlin/feature/control/src/main/java/com/openclaw/tv/feature/control/SearchIntentDispatcher.kt`
- Create: `apps/android-tv-client-kotlin/feature/control/src/test/java/com/openclaw/tv/feature/control/ControlExecutorTest.kt`
- Modify: `apps/android-tv-client-kotlin/app/src/main/AndroidManifest.xml`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`

**Step 1: Write the failing control test**

Create `ControlExecutorTest.kt` that verifies:

- `open_app` maps to package launch
- navigation actions map to key events
- search actions map to search intent when supported
- unsupported actions return structured failure
- summon state requests the assistant activity surface
- vendor voice service wake event opens the summon surface
- cleaned capture can start while local playback remains active
- fallback duck or pause is only used when vendor voice service is unavailable or degraded

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:control:testDebugUnitTest
```

Expected: FAIL because the voice/control modules do not exist.

**Step 3: Write minimal implementation**

Implement:

- playback-aware voice capture using:
  - a vendor-provided voice-front-end service that receives render reference plus mic input
  - `VendorVoiceServiceClient` as the Kotlin integration boundary
  - `WakeEventBridge` for board-level summon and wake events
  - `SpeechPipelineCoordinator` to consume cleaned audio or transcripts from the system service
- transparent `AssistantActivity` that can appear above the current UI on summon / wake / press-to-talk
- fallback local degrade mode that can duck or pause playback only if vendor voice capability is missing
- local execution mapping for:
  - YouTube
  - Netflix
  - Prime Video
  - Disney+
  - Plex
  - Spotify
  - VLC
  - Settings
- command result model for UI and logging

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/voice apps/android-tv-client-kotlin/feature/control apps/android-tv-client-kotlin/settings.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add native voice and control execution"
```

### Task 8: Add remote home config, app pool, and program shelf support

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeCatalogRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/model/HomeCatalogModels.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeCatalogRepositoryTest.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeScreen.kt`

**Step 1: Write the failing repository test**

Create `HomeCatalogRepositoryTest.kt` that verifies:

- background image URL loads from server config
- featured app ids map to local app capability entries
- absent server data falls back to packaged defaults

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:home:testDebugUnitTest
```

Expected: FAIL because catalog loading is not implemented.

**Step 3: Write minimal implementation**

Implement:

- home config fetch
- app pool mapping
- program shelf placeholder fed by current service responses or static fallback
- cached background and app list

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/home
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add remote home config and app pool rendering"
```

### Task 9: Add OTA, broadcast handling, and runtime status surfaces

**Files:**
- Create: `apps/android-tv-client-kotlin/feature/ota/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/ota/src/main/java/com/openclaw/tv/feature/ota/OtaRepository.kt`
- Create: `apps/android-tv-client-kotlin/feature/broadcast/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/broadcast/src/main/java/com/openclaw/tv/feature/broadcast/BroadcastHandler.kt`
- Create: `apps/android-tv-client-kotlin/feature/settings/build.gradle.kts`
- Create: `apps/android-tv-client-kotlin/feature/settings/src/main/java/com/openclaw/tv/feature/settings/SettingsScreen.kt`
- Create: `apps/android-tv-client-kotlin/feature/ota/src/test/java/com/openclaw/tv/feature/ota/OtaRepositoryTest.kt`
- Modify: `apps/android-tv-client-kotlin/settings.gradle.kts`

**Step 1: Write the failing OTA test**

Create `OtaRepositoryTest.kt` that verifies:

- latest release response is parsed
- target version and force-upgrade flags are surfaced
- no release returns safe null state

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :feature:ota:testDebugUnitTest
```

Expected: FAIL because the OTA feature does not exist.

**Step 3: Write minimal implementation**

Implement:

- release lookup from `GET /client/releases/latest`
- settings/status surface showing policy, lease, and release info
- placeholder runtime for consuming platform broadcasts later without changing client shell structure

**Step 4: Run test to verify it passes**

Run the same command again.

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/feature/ota apps/android-tv-client-kotlin/feature/broadcast apps/android-tv-client-kotlin/feature/settings apps/android-tv-client-kotlin/settings.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: add ota and runtime status features"
```

### Task 10: Add instrumentation, compatibility harness, and release packaging

**Files:**
- Create: `apps/android-tv-client-kotlin/app/src/androidTest/java/com/openclaw/tv/EndToEndSmokeTest.kt`
- Create: `apps/android-tv-client-kotlin/scripts/build-debug.ps1`
- Create: `apps/android-tv-client-kotlin/scripts/build-release.ps1`
- Create: `docs/testing/2026-04-15-kotlin-tv-client-test-matrix.md`
- Modify: `README.md`
- Modify: `docs/testing/2026-03-19-android-tv-compatibility-matrix.md`

**Step 1: Write the failing smoke test**

Create `EndToEndSmokeTest.kt` that walks:

- launch
- bootstrap state visible
- home renders
- press-to-talk entry point visible
- app rail focus moves

**Step 2: Run test to verify it fails**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat :app:connectedDebugAndroidTest
```

Expected: FAIL because the full flow is not wired yet.

**Step 3: Write minimal implementation**

Add:

- smoke test harness
- debug and release build scripts
- updated compatibility matrix for the native client
- updated root README describing native rewrite path

**Step 4: Run full verification**

Run:

```bash
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
gradlew.bat testDebugUnitTest
gradlew.bat connectedDebugAndroidTest
gradlew.bat assembleDebug
```

Expected: PASS.

**Step 5: Commit**

```bash
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/androidTest apps/android-tv-client-kotlin/scripts docs/testing/2026-04-15-kotlin-tv-client-test-matrix.md README.md docs/testing/2026-03-19-android-tv-compatibility-matrix.md
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "chore: finalize native kotlin tv client baseline"
```

## Recommended Rewrite Approach

Use this approach:

- **Approach A: Parallel greenfield native client** at `apps/android-tv-client-kotlin`

Do not use these as the primary path:

- **Approach B: In-place replacement of `apps/android-tv-client`**
  - too destructive
  - harder diff review
  - harder rollback

- **Approach C: Keep Flutter shell and only grow native bridges**
  - preserves old structural problems
  - still leaves TV focus, lifecycle, and native orchestration split across two runtimes

## API Assumptions

This plan assumes the native client consumes the existing `home` platform client routes in:

- `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api\src\routes\client.ts`

The native client should not reimplement or second-guess server logic for:

- policy resolution
- model lease decision
- provider selection
- release targeting

It only consumes and executes.

This plan also assumes a device-side vendor voice contract exists or will be defined with at least:

- `isVoiceFrontEndAvailable()`
- `observeWakeEvents()`
- `startAssistantCaptureSession()`
- `stopAssistantCaptureSession()`
- `getVoiceCapabilityState()`

The Kotlin rewrite should keep that integration behind one adapter boundary so BSP changes do not leak across the app.

## Voice and summon constraints

- The Kotlin client should treat board-level voice processing as the primary path:
  - render reference comes from the device audio stack
  - microphone cleanup happens before the app-level speech pipeline
  - summon and wake events may originate outside the app process
- The app rewrite must define a strict integration boundary for the vendor service:
  - capability discovery
  - wake event subscription
  - cleaned audio or transcript session start and stop
  - degradation reporting
- Local duck or pause remains a compatibility fallback, not the default interaction model.
- The summon avatar requirement is satisfied by a foreground transparent `AssistantActivity`, not by a universal system overlay permission model.

## Official UI and media guidance used for this plan

- Android Developers Compose for TV codelab states Compose for TV is the UI framework for Android TV apps: [Compose for TV](https://developer.android.com/codelabs/compose-for-tv-introduction)
- Android Developers Media3 docs are the official media stack reference: [Media3](https://developer.android.com/media/media3)

## Notes For Execution

- Keep `apps/android-tv-client` frozen until the Kotlin client can boot, authenticate, lease, render home, and execute the first supported app controls.
- Do not move any admin, shared model routing, or project governance logic into this repository.
- Prefer feature parity on:
  - bootstrap/session
  - lease usage
  - voice entry
  - summon overlay
  - TV home
  - app control
before trying to exceed the Flutter client.

Plan complete and saved to `docs/plans/2026-04-15-kotlin-android-tv-rewrite-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
