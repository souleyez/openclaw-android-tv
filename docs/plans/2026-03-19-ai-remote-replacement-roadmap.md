# AI Remote Replacement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build OpenClaw TV into an AI-first controller that replaces most daily remote-control operations while preserving a reliable fallback path for hard cases.

**Architecture:** The roadmap keeps the current `voice -> model intent -> local executor` core, then adds TV-specific navigation, page-state awareness, execution confirmation, and fallback orchestration in phases. The key product rule is “AI as primary control path, remote-style directional control as the fallback substrate,” not “remove every non-AI control from day one.”

**Tech Stack:** Flutter, Android native bridges (Kotlin), MiniMax/OpenAI-compatible router, SQLite-backed backend APIs, Android TV emulator/real devices.

---

## 1. Target Definition

### Replacement Target

- **Phase A target:** replace `70%` of daily high-frequency remote usage
- **Phase B target:** replace `85%` of daily high-frequency remote usage
- **Phase C target:** AI becomes the default control path; physical remote becomes fallback only

### Success Metric

- User can finish the most common TV tasks without touching directional keys in most sessions:
  - open app
  - search content
  - play/pause/resume
  - next/previous
  - go back
  - switch simple settings
  - restore account rights
  - create top-up order / check status

### Non-Goal

- Do not promise `100% remote replacement` for:
  - login flows
  - CAPTCHA / OTP
  - payment confirmation pages inside third-party apps
  - unknown ROM popups
  - unstable deep app pages with no public intent or media-session support

---

## 2. Capability Layers

### Layer 1: Direct Action Replacement

This is the current strongest path and should remain the default whenever possible.

**Examples:**
- “Open YouTube”
- “Pause”
- “Next song”
- “Search Spotify for jazz”
- “Go back”

**Implementation rule:**
- Prefer `Intent`
- Then `MediaSession`
- Then `KeyEvent`

**Current project status:**
- Already partially implemented in:
  - `apps/android-tv-client/lib/features/control/control_service.dart`
  - `apps/android-tv-client/lib/features/control/control_profile_registry.dart`
  - `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`

### Layer 2: Conversational TV Task Replacement

This layer lets users express goals instead of button presses.

**Examples:**
- “Open Netflix and find nature documentaries”
- “Switch to Spotify and play relaxing piano”
- “Continue what I was watching”
- “Lower the friction by explaining blocked actions and next steps”

**Implementation rule:**
- Model must convert open-ended language into:
  - target app
  - target action
  - optional search query
  - optional multi-step plan
- UI must confirm the interpreted task before or during execution

### Layer 3: Directional Fallback Replacement

This is what gets the product close to “replacing the remote,” even when direct app APIs are weak.

**Examples:**
- “Move right”
- “Select the second row”
- “Scroll down”
- “Confirm”
- “Go to the left menu and pick settings”

**Implementation rule:**
- Treat directional navigation as a fallback executor, not the first-choice interface
- Support:
  - `up`
  - `down`
  - `left`
  - `right`
  - `select`
  - `home`
  - `back`
  - `menu`

---

## 3. Product Strategy

### Recommended Positioning

OpenClaw should be positioned as:

- `AI-first remote replacement`
- `Voice-first TV operating assistant`
- `Remote fallback still available for edge cases`

### Why this framing is correct

- It matches the technical reality of Android TV fragmentation
- It avoids overpromising on Netflix/local-player edge cases
- It gives the team a measurable replacement goal
- It lets the product feel magical in high-frequency flows without getting trapped by rare low-level failures

---

## 4. Phase Roadmap

### Phase 1: High-Frequency Remote Replacement

**Goal:** cover the most common daily actions with low failure rate.

**Scope:**
- app launch
- play/pause/resume
- next/previous
- back
- search
- simple settings open
- explicit execution feedback

**Files to focus on:**
- Modify: `apps/android-tv-client/lib/features/control/control_profile_registry.dart`
- Modify: `apps/android-tv-client/lib/features/control/control_service.dart`
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`
- Modify: `apps/backend-api/src/modules/model-router/model-router.service.ts`
- Modify: `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`

**Exit criteria:**
- User can complete most `YouTube / VLC / Spotify / Settings` tasks without directional remote input
- UI clearly shows `received -> understanding -> executing -> done/failed`

### Phase 2: Remote-Key Parity Fallback

**Goal:** expose the minimum remote button set through AI and UI fallback.

**Scope:**
- add formal directional control actions
- add executor support for D-pad and select
- add on-screen hint when AI falls back to directional mode

**Files to focus on:**
- Modify: `apps/android-tv-client/lib/features/control/control_action.dart`
- Modify: `apps/android-tv-client/lib/features/control/control_profile.dart`
- Modify: `apps/android-tv-client/lib/features/control/control_profile_registry.dart`
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`
- Modify: `apps/backend-api/src/modules/model-router/model-router.service.ts`

**Exit criteria:**
- AI can trigger directional fallback for unknown layouts
- Client can still expose visible “currently navigating” feedback

### Phase 3: Page-State Awareness

**Goal:** stop acting blindly; improve intent accuracy by knowing what screen the TV is on.

**Scope:**
- capture foreground app/package
- capture recent executor result and current route
- optionally add accessibility/state adapter on privileged devices
- classify whether app is on home/search/playback/settings states

**Files to focus on:**
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`
- Create: `apps/android-tv-client/lib/features/control/device_state_snapshot.dart`
- Modify: `apps/backend-api/src/modules/model-router/model-router.service.ts`
- Modify: `apps/backend-api/src/modules/model-router/minimax.provider.ts`

**Exit criteria:**
- Model prompt includes current app and last known execution state
- ambiguous commands become measurably more accurate

### Phase 4: Multi-Step Task Execution

**Goal:** support tasks that require more than one action.

**Scope:**
- open target app
- wait for ready state
- search
- confirm playback or selection
- recover when one step fails

**Files to focus on:**
- Create: `apps/android-tv-client/lib/features/control/execution_plan.dart`
- Create: `apps/android-tv-client/lib/features/control/execution_orchestrator.dart`
- Modify: `apps/backend-api/src/modules/model-router/model-router.service.ts`
- Modify: `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`

**Exit criteria:**
- AI can say and do:
  - “Opening Spotify, searching for jazz, and starting playback”
- logs show step-by-step state transitions

### Phase 5: Privileged Device Mode

**Goal:** unlock near-system-grade remote replacement on customized devices.

**Scope:**
- hotword integration
- richer state observation
- privileged navigation adapters
- system overlays and faster wake UX

**Files to focus on:**
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/VoiceStandbyForegroundService.kt`
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`
- Modify: `apps/android-tv-client/lib/features/voice/voice_command_service.dart`
- Modify: `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`

**Exit criteria:**
- privileged builds feel materially closer to “no remote needed”

---

## 5. UX Requirements

### Always-Visible Execution Feedback

The user must never wonder whether the system heard them.

**Required states:**
- `Listening`
- `Received`
- `Understanding`
- `Executing`
- `Blocked`
- `Need confirmation`
- `Completed`
- `Fallback navigation mode`
- `Failed`

**Existing base:**
- `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`
- `apps/android-tv-client/lib/features/avatar/virtual_host_avatar.dart`

### AI Must Explain Fallback

If the system cannot do a direct action, it should say something like:

- “I can’t open that exact page directly, so I’m switching to navigation mode.”
- “I can pause playback, but this shared API key scope cannot launch the app.”
- “I’m moving right and selecting the highlighted result.”

### Remote Never Disappears Completely

Even when AI is primary, keep these fallback channels:
- physical remote
- D-pad events
- visible UI execution trace

---

## 6. Risk Assessment

### Risk 1: Overpromising on complete replacement

**Mitigation:**
- sell “AI-first replacement for most daily use”
- keep explicit fallback mode
- measure replacement rate instead of claiming absolute parity

### Risk 2: App fragmentation

**Mitigation:**
- continue per-app `ControlProfile`
- maintain “supported / partial / fallback-only” matrix
- ship app-specific adapters incrementally

### Risk 3: Slow response time

**Mitigation:**
- show immediate received/processing status
- prefer local executor when confidence is high
- keep a fast rule path for common commands

### Risk 4: State blindness

**Mitigation:**
- add app-state snapshotting
- record last execution result
- use privileged device mode where available

---

## 7. Recommended Next Build Order

### Task 1: Add formal remote-direction actions

**Files:**
- Modify: `apps/android-tv-client/lib/features/control/control_action.dart`
- Modify: `apps/android-tv-client/lib/features/control/control_profile_registry.dart`
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`

**Outcome:**
- AI can explicitly trigger D-pad fallback actions

### Task 2: Add fallback-navigation messaging

**Files:**
- Modify: `apps/backend-api/src/modules/model-router/model-router.service.ts`
- Modify: `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`

**Outcome:**
- UI explains when and why the system changed from direct control to navigation mode

### Task 3: Add current app / foreground snapshot

**Files:**
- Modify: `apps/android-tv-client/android/app/src/main/kotlin/com/openclaw/assistant/openclaw_android_tv_client/MainActivity.kt`
- Create: `apps/android-tv-client/lib/features/control/device_state_snapshot.dart`
- Modify: `apps/android-tv-client/lib/core/api_client.dart`

**Outcome:**
- intent resolution becomes context-aware

### Task 4: Add execution-plan orchestration

**Files:**
- Create: `apps/android-tv-client/lib/features/control/execution_plan.dart`
- Create: `apps/android-tv-client/lib/features/control/execution_orchestrator.dart`
- Modify: `apps/android-tv-client/lib/features/tv_home/tv_home_page.dart`

**Outcome:**
- multi-step tasks stop feeling like isolated button presses

### Task 5: Re-test in TV emulator and real devices

**Files:**
- Modify: `docs/testing/2026-03-19-closed-beta-device-test-runbook.md`

**Outcome:**
- replacement rate gets measured with real scenarios

---

## 8. Practical Recommendation

The product should explicitly aim for:

- **MVP:** “AI can replace most common remote actions”
- **Beta:** “AI is the default control path for daily TV use”
- **Long-term:** “Remote is fallback only, especially on privileged devices”

This is ambitious but realistic, and it aligns with the current codebase much better than a blanket promise of total physical remote elimination.
