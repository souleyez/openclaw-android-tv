# OpenClaw Android TV Kotlin Rewrite on RK3128 Low-Spec Boards

## 1. Purpose

This document narrows the Kotlin rewrite plan to the actual lowest target hardware:

- Rockchip `RK3128`
- `4 x Cortex-A7`, around `1.3 GHz`
- `1 GB RAM`
- `4 GB eMMC`

Reference hardware profile used for this assessment:

- Mango RK3128 board summary: CPU `RK3128 ARM Cortex-A7`, `1GB DDR3/2GB DDR3 optional`, `4GB/8GB/16GB/32GB eMMC`, `1080P` decode support  
  Source: [Mango RK3128 board page](https://www.mangoarm.com/Rockchip-RK3128-Single-Board-Computer/)

This is a constraint document. It overrides any Kotlin client assumption that is too expensive for this board.

## 2. Hard Constraints

- `1 GB RAM` means the app must behave like an embedded terminal, not like a rich consumer media app.
- `4 GB eMMC` means no large offline models, no large caches, no large background asset packs, and no log growth without strict caps.
- `Cortex-A7` class CPU means UI, animation, and binder/service churn must stay simple.
- If the BSP is still on Android `5.1` or `6.0`, the implementation must stay conservative. If the BSP can move to `7.1.2` or `8.1` stably, the client margin improves.

## 3. Main Architecture Decision

The client should still move to native Kotlin, but the low-spec profile changes the UI and runtime choices:

- keep Kotlin native
- keep `home` as the only backend and control plane
- keep vendor board services as the primary source of system capability
- do **not** optimize for flashy UI
- do **not** optimize for framework purity

For this board, the client should be treated as a thin assistant terminal.

## 4. UI Stack Decision

Google documents that Compose is the recommended way to build Android TV apps:

- [Create and run a TV app](https://developer.android.com/training/tv/get-started/create)
- [Compose for TV codelab](https://developer.android.com/codelabs/compose-for-tv-introduction)

However, that recommendation is not the same thing as the best choice for `RK3128 + 1GB/4GB`.

For this hardware profile, the recommended UI stack is:

- Kotlin
- single-activity shell
- XML layouts or very light custom views
- `RecyclerView`-based rails
- explicit focus management
- minimal overdraw
- no heavy blur, no large parallax, no video wallpaper as default

Do not make Compose for TV the default implementation on this board. It may still be acceptable for isolated surfaces later, but not as the baseline shell.

## 5. What We Can Realistically Ship

The following is realistic on this board if the vendor side exposes usable board-level services:

- always-available summon entry
- wake-word to assistant overlay flow
- transparent assistant foreground activity
- simple assistant avatar animation
- subtitle/transcript area
- app rail with `5-8` featured apps
- open app, directional control, confirm, back, media keys
- remote config from `home`
- OTA status display
- projector hardware controls
- basic health and diagnostics surface

The following should not be in the baseline:

- full-screen video background
- large image carousel with many decoded bitmaps resident at once
- WebView-driven shell
- on-device ASR, TTS, or LLM models
- rich particle effects
- multiple long-lived app processes
- large analytics or log SDKs

## 6. Board-Level Services Required

The Kotlin client should rely on four device-side service domains:

- `vendor.voice`
  - wake event
  - cleaned mic capture
  - capability and degradation state
- `vendor.media`
  - current foreground media app
  - media active state
  - unified playback commands
- `vendor.projector`
  - brightness, keystone, focus, input switch, standby
- `vendor.deviceops`
  - OTA state, logs, diagnostics, health snapshot

If these services do not exist, the app can still run, but the target experience drops sharply.

## 7. Performance and Resource Budgets

These are practical target budgets for the RK3128 lowest profile.

### RAM

- launcher to home idle app RSS target: `<= 120 MB`
- summon overlay + active voice capture target: `<= 160 MB`
- sustained peak budget: `<= 200 MB`

If the app regularly exceeds that range, LMK pressure and jank risk become unacceptable on `1 GB` devices.

### Storage

- release APK target: `<= 30 MB`
- writable app cache target: `<= 80 MB`
- local logs ring buffer: `<= 16 MB`
- no offline speech model downloads in the baseline SKU

### Startup and interaction

- cold start to first usable home frame: `<= 1.5 s`
- warm resume to usable frame: `<= 500 ms`
- wake event to visible assistant overlay: `<= 300 ms`
- push-to-talk to capture-start UI response: `<= 150 ms`

## 8. Voice Strategy on This Board

This board profile only makes sense if the projector mainboard team can provide system-level voice help.

Primary path:

- the board captures render reference from local playback
- the board captures mic input
- the board performs echo cancellation, suppression, gain control, and VAD
- the board exposes wake events and cleaned capture to the app

App fallback path:

- if the board voice service is missing or degraded, the app may duck or pause playback during capture

The app should not try to become the DSP stack on this hardware.

## 9. Minimum BSP Assumptions

Preferred:

- stable Android `7.1.2` or `8.1`
- system service or bound service support for vendor capabilities
- stable binder reconnect behavior
- clean boot auto-start path for the assistant app or service

Possible but weaker:

- Android `5.1` or `6.0`

If the shipped BSP is `5.x/6.x`, the client should further reduce:

- animation complexity
- resident surfaces
- library count
- image decode footprint

## 10. Practical Product Grade for RK3128

If the board vendor delivers `vendor.voice` well, this board can still ship a credible assistant experience:

- "TV/Projector assistant terminal"
- voice summon
- animated assistant foreground shell
- global playback coexistence
- app launch and media control
- projector device control

It is not a good target for a rich, content-heavy, heavily animated Android TV super-app.

## 11. Implementation Impact on the Main Rewrite Plan

The Kotlin rewrite plan remains valid in structure, but on RK3128:

- treat `vendor.voice` as mandatory priority work
- downgrade UI complexity
- replace Compose-heavy assumptions with low-overhead native Views as the baseline
- keep the home screen flat and memory-stable
- ship the first release as a control terminal, not as a visually rich content browser

## 12. Recommended Next Documents

To make the rewrite executable, the next concrete contracts should be written and frozen:

- vendor voice service contract
- vendor media broker contract
- vendor projector hardware contract
- vendor device-ops contract
