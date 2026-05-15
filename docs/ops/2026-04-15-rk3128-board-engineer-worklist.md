# RK3128 Mainboard Engineer Worklist for OpenClaw Assistant

## 1. Scope

This checklist is for the board / BSP / system integration engineers.

Before system-level adaptation starts, the hardware vendor must provide the
materials listed in
[`2026-05-06-system-level-adaptation-vendor-materials.md`](2026-05-06-system-level-adaptation-vendor-materials.md).
If these materials are missing, the Android client can only target the public
Android APK baseline.

Target hardware:

- `RK3128`
- `1 GB RAM`
- `4 GB eMMC`

Target product goal:

- always-available projector assistant
- wake while media is playing
- show assistant overlay in front of the current UI
- execute app control and projector hardware control

The Android app alone cannot deliver that experience on this board. The board and BSP side must provide stable system services.

## 2. Priority Order

Implement in this order:

1. `vendor.voice`
2. `vendor.media`
3. `vendor.projector`
4. `vendor.deviceops`
5. system image and performance hardening

If only one service can be done first, do `vendor.voice`.

## 3. P0 Baseline System Bring-Up

### Must provide

- stable Android build for RK3128
- boot-complete broadcast correctness
- app auto-start or service bind path for the assistant runtime
- stable microphone input routing
- stable speaker / playback routing
- serial log and tombstone access for debugging
- package signing and privileged-permission strategy if required

### Must verify

- the assistant app can launch after boot
- the assistant app can re-bind to vendor services after process restart
- binder death is handled cleanly
- no aggressive task killer breaks the assistant runtime

### Deliverable

- one engineering note with:
  - Android version
  - SELinux mode and relevant policy additions
  - privileged app or normal app status
  - audio path summary
  - service start strategy

## 4. P1 `vendor.voice` Service

This is the most important board deliverable.

### Goal

Allow the assistant to hear the user while local playback continues.

### Board-side responsibilities

- obtain system playback render reference
- obtain microphone capture
- perform:
  - AEC
  - NS
  - AGC
  - VAD
- support wake-word or wake event delivery
- expose cleaned capture to the app
- report degradation state if the voice front-end cannot meet target quality

### Minimum API surface

- `isVoiceFrontEndAvailable()`
- `getVoiceCapabilityState()`
- `observeWakeEvents()`
- `startAssistantCaptureSession()`
- `stopAssistantCaptureSession()`

Optional but strongly recommended:

- `observePartialTranscript()`
- `observeVadState()`
- `observeBargeInState()`
- `getCurrentPlaybackReferenceState()`

### Acceptance criteria

- with local playback active, wake event still triggers reliably
- cleaned capture is usable without pausing media in the default path
- wake event to app callback is stable
- service survives repeated start/stop cycles
- degradation is reported explicitly when capability is missing

### If full target is not possible

At minimum provide:

- wake event
- raw mic session start/stop
- playback-active state

But this is only fallback grade, not target grade.

## 5. P2 `vendor.media` Service

### Goal

Provide a system-level media broker so the assistant knows what is currently playing and where to send playback commands.

### Board-side responsibilities

- detect current foreground package
- detect whether media is active
- expose current playback owner if known
- route unified commands to the correct target

### Minimum API surface

- `getForegroundPackage()`
- `isMediaActive()`
- `getPlaybackOwner()`
- `play()`
- `pause()`
- `resume()`
- `next()`
- `previous()`
- `dispatchMediaKey(keyCode)`

Recommended:

- `search(query)`
- `seekForward()`
- `seekBackward()`
- `getCurrentMediaSnapshot()`

### Acceptance criteria

- "pause" hits the actual active player
- foreground package and media-active state are correct
- unsupported actions return explicit failure, not silent no-op

## 6. P3 `vendor.projector` Service

### Goal

Expose projector-only hardware controls that normal Android TV apps do not have.

### Board-side responsibilities

- focus control
- keystone correction
- brightness / light engine control
- color mode
- source or input switch
- standby / wake
- thermal and fan state

### Minimum API surface

- `focusAuto()`
- `focusStep(delta)`
- `setKeystone(modeOrValue)`
- `setBrightness(level)`
- `switchInput(inputId)`
- `enterStandby()`
- `wakeUp()`
- `getProjectorState()`

Recommended:

- `setColorMode(mode)`
- `getThermalState()`
- `getFanState()`

### Acceptance criteria

- commands are idempotent where appropriate
- current state can be queried after each command
- failure codes distinguish unsupported, busy, and hardware fault

## 7. P4 `vendor.deviceops` Service

### Goal

Give the app and backend enough visibility to diagnose field problems on low-end devices.

### Board-side responsibilities

- OTA status and stage reporting
- network health snapshot
- storage pressure snapshot
- log export hooks
- crash and ANR visibility
- microphone and voice path health

### Minimum API surface

- `getOtaState()`
- `getHealthSnapshot()`
- `getStorageState()`
- `getNetworkState()`
- `exportDiagnosticsBundle()`

Recommended:

- `getMicHealthState()`
- `getVoiceServiceState()`
- `getLastCrashSummary()`

### Acceptance criteria

- diagnostics can be collected without adb in the field
- OTA state distinguishes idle, downloading, verifying, installing, failed
- logs are bounded and do not grow unbounded on `4 GB` storage

## 8. System Image Hardening

### Required

- make sure vendor services start reliably
- make sure assistant app can bind without race conditions
- define permission model for vendor service access
- add SELinux rules only as needed
- guarantee microphone and playback path availability

### Performance rules

- disable unnecessary resident packages if possible
- avoid memory-heavy vendor daemons
- keep assistant-side binder payloads small
- prefer compact state objects over large JSON blobs across binder

## 9. RK3128 Performance Targets

These should be treated as engineering targets, not optional nice-to-have goals.

### Memory

- keep free RAM margin healthy after boot
- assistant app idle RSS target: `<= 120 MB`
- assistant active voice target: `<= 160 MB`
- avoid any board service with sustained large in-memory buffers

### Storage

- keep writable space protected
- cap diagnostics and logs
- no large offline AI assets in baseline firmware

### Interaction

- wake event to overlay visible: `<= 300 ms`
- command dispatch after recognized intent: `<= 200 ms` local path
- no visible frame stalls during simple home navigation

## 10. Integration Test Checklist

Board team should hand off after passing at least these tests:

- media playing, user says wake word, assistant overlay appears
- media continues or only lightly ducks, capture still works
- "pause" correctly pauses the active player
- "open YouTube" launches the target app
- "focus auto" triggers projector focus
- reboot device, assistant service path still works
- repeated wake and cancel loop does not leak memory badly
- repeated app process death and restart rebinds vendor services cleanly

## 11. Definition of Done for Mainboard Team

The board-side work is done when:

- vendor services are callable from a normal app integration layer
- wake, voice capture, media state, and projector controls are stable
- diagnostics are available without adb-only workflows
- the Kotlin client can stay thin and does not need to reimplement board logic

If the app team still has to solve AEC, media ownership, or projector hardware access in the app itself, then the board-side work is not done.
