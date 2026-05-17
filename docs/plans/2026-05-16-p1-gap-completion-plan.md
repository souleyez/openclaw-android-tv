# Android TV P1 Gap Completion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Finish the P1 gaps needed for a field-usable Android TV APK baseline while keeping system-level projection, OTA, diagnostics, and hardware controls explicitly gated on vendor materials.

**Architecture:** Keep the current APK baseline: home provides runtime data through `oc.goods-editor.com`, the TV client renders a local-first shell, and Android public APIs cover app launch, install prompts, DLNA media receiving, boot launch, and best-effort background trimming. Anything requiring Miracast/AirPlay sink, privileged process control, OTA, projection hardware, or diagnostics stays behind a vendor-interface adapter and is not promised until the vendor drop is available.

**Tech Stack:** Kotlin Android TV client, Gradle, adb/uiautomator, Android public APIs, `feature:cast` lightweight DLNA renderer, `runtime-manifest`, `oc.goods-editor.com`.

---

## Current Baseline

- Branch: `codex/tv-platform-contract`
- Current test device: `64WGLJQS5R`
- Home API domain: `https://oc.goods-editor.com/api`
- Current APK scope: homepage shell, app rail, app management overlay, local APK install prompt, boot receiver, runtime manifest, lightweight DLNA, Lebo fallback, best-effort process trimming.
- Current visual gap: the homepage character and left-side ad zone have code shells and binding points, but are not yet accepted as a landed product surface because final assets, slot data evidence, empty-state behavior, and real-device screenshots still need to be closed.
- Current local dirty files before this plan: the cast Wi-Fi hint text/style change in `HomeViewModel.kt`, `item_quick_action.xml`, and `HomeViewModelTest.kt`.

## P1 Definition For This Round

P1 here means "must be solid enough for the next acceptance APK":

- Users can understand and use the homepage without operator explanation.
- The homepage character is a real lightweight sprite system, can switch actions from dialogue/status, and the ad slot to the character's left is backed by either final creatives or a documented fallback state.
- App slots can be filled from installed apps and managed from the `+` entry.
- Cast entry is stable, resource-light, and honest about self-built DLNA vs Lebo fallback.
- Returning home trims obvious background playback/app-store processes without hurting system stability.
- Boot launch works where the system allows public APK boot receivers.
- `oc.goods-editor.com` is the only production home API base.
- Every remaining system-level limitation is written down as a vendor/material blocker, not hidden as an APK bug.

---

### Task 0: Freeze The Current Local State

**Files:**
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/item_quick_action.xml`
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt`
- Inspect: `artifacts/android-tv/openclaw-tv-cast-wifi-hint-debug-*.apk`

**Step 1: Check local diff**

Run:

```powershell
git status --short --branch
git diff -- apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt apps/android-tv-client-kotlin/feature/home/src/main/res/layout/item_quick_action.xml apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt
```

Expected:
- Only the cast hint text/style change plus this plan document are dirty.

**Step 2: Re-run focused verification**

Run:

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:home:testDebugUnitTest :app:assembleDebug
```

Expected:
- `BUILD SUCCESSFUL`.

**Step 3: Commit only after visual acceptance**

Run after acceptance:

```powershell
git add apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt apps/android-tv-client-kotlin/feature/home/src/main/res/layout/item_quick_action.xml apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt docs/plans/2026-05-16-p1-gap-completion-plan.md
git commit -m "Polish cast wifi hint and plan P1 gap closure"
```

---

### Task 1: Build The P1 Acceptance Ledger

**Files:**
- Create: `docs/testing/2026-05-16-p1-acceptance-ledger.md`
- Reference: `docs/ops/2026-05-12-android-tv-thread-handoff.md`
- Reference: `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md`
- Reference: `docs/ops/2026-05-06-x10-emcp-v4-material-gap-analysis.md`

**Step 1: Write the ledger**

Create a table with these columns:

- `Area`
- `P1 target`
- `Current status`
- `Test evidence`
- `Owner`
- `Blocker`
- `Result`

Rows:

- Home visual shell
- Home character sprite assets
- Dialogue-driven character actions
- Character-left ad slot
- Ad slot empty/fallback state
- App rail defaults
- App management `+` entry
- Local APK install and upgrade
- App uninstall prompt
- Cast self-built DLNA
- Cast Lebo fallback
- Cast return-to-home behavior
- Background trimming
- Boot launch
- Home API production domain
- Runtime manifest content
- Low-end memory budget
- Vendor system projection
- Vendor OTA and diagnostics
- Vendor hardware controls

**Step 2: Fill current evidence**

Use known evidence:

- APK installed on `64WGLJQS5R`.
- UI tree confirms `连「Soulzy」`.
- Foreground activity is `com.openclaw.tv/.MainActivity`.
- Crash buffer has no OpenClaw crash.
- Runtime log uses `https://oc.goods-editor.com/api`.

**Step 3: Keep unresolved system items explicit**

Mark these as vendor blockers, not APK blockers:

- Miracast / Wi-Fi Display / AirPlay sink implemented by our APK.
- Platform-signed process kill/freeze.
- Device OTA install without system installer.
- Projection focus, keystone, brightness, fan, temperature APIs.
- No-adb log export.

---

### Task 2: Close The Home API P1 Gap

**Files:**
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/TvHomeRepository.kt`
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeRuntimeManifestRepository.kt`
- Inspect: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Cross-repo inspect: `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api\src\lib\control-plane-tv-contract.ts`

**Step 1: Verify production routes**

Run:

```powershell
curl.exe -i https://oc.goods-editor.com/api/health
curl.exe -i https://oc.goods-editor.com/api/me/runtime-manifest
curl.exe -i https://oc.goods-editor.com/api/me/entitlement
curl.exe -i https://oc.goods-editor.com/api/me/tv-home-config
```

Expected:
- `/api/health` returns `200`.
- Auth-required TV routes return `401` without TV auth.
- No route should return public `404`.

**Step 2: Investigate device-side 503**

Run after launching the APK:

```powershell
adb -s 64WGLJQS5R logcat -c
adb -s 64WGLJQS5R shell am force-stop com.openclaw.tv
adb -s 64WGLJQS5R shell am start -n com.openclaw.tv/.MainActivity
Start-Sleep -Seconds 8
adb -s 64WGLJQS5R logcat -d | Select-String -Pattern "Platform API|HTTP 503|runtime-manifest|entitlement|tv-home-config|OpenClawRuntime"
```

Expected:
- Base URL remains `https://oc.goods-editor.com/api`.
- If `503` persists, identify which route returns it and whether it is auth, upstream, or data.

**Step 3: Patch only the responsible side**

Rules:

- If Android retries too aggressively, adjust the TV repository cadence.
- If home returns `503` for empty runtime data, fix home to return a valid empty or auth response.
- Do not add a new TV endpoint for ads or apps; app delivery and ads remain in `runtime-manifest`.

---

### Task 3: Land Homepage Character And Character-Left Ad Slot

**Files:**
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeAdSlotRegistry.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/AssistantSpriteView.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/AssistantSpriteState.kt`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_idle.png`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_talk.png`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_think.png`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_point_left.png`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_guide.png`
- Create: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_sprite_worried.png`
- Modify or replace: `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable-nodpi/assistant_girl_hero_bust.*`
- Test: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt`

**Step 1: Define the landed visual contract**

Acceptance:

- Character is visible in the first viewport on the right side of the hero area.
- Character is rendered from a small sprite set, not a single static bitmap.
- Character looks intentional at 1280x720 on the test projector, not like a temporary placeholder.
- Character action changes when the homepage dialogue/status changes.
- Ad zone sits to the character's left, does not cover the dialogue, App rail, or quick-action row.
- If there is no valid ad creative, the ad zone has a clean empty state or is hidden without leaving an awkward hole.
- Online and offline states keep the same visual hierarchy.

**Step 2: Finalize the sprite asset contract**

Sprite actions for P1:

- `idle`: default online waiting state, subtle blink/breathing loop.
- `talk`: when the dialogue text changes or a spoken response is being shown.
- `think`: runtime sync, loading, checking, or "online syncing" states.
- `point_left`: when a valid character-left ad creative is visible or focused.
- `guide`: offline Wi-Fi guide or setup guidance.
- `worried`: warning, critical, entitlement/resource-limited, or request failure states.

Asset rules:

- Use WebP or optimized PNG sprite atlases under `drawable-nodpi`.
- Keep each action atlas small and fixed-size; target 6-8 fps, not full video.
- Keep total decoded sprite memory budget low enough for the 1 GB board. Initial budget target: under 8 MB decoded for the currently active action.
- Do not use Lottie, GIF, animated WebP runtime decoding, or video for P1.
- Keep the old static bust as a fallback if a sprite atlas fails to decode.

Implementation shape:

- Add `AssistantSpriteState` enum and a resolver in `HomeViewModel`.
- Add `assistantSpriteState: AssistantSpriteState` to `HomeUiState`.
- Replace the `assistant_character` `ImageView` with `AssistantSpriteView`, or wrap the existing view id with a custom class in XML.
- `AssistantSpriteView` draws one atlas frame at a time with `Canvas.drawBitmap(srcRect, dstRect)`.
- The view owns only the active atlas bitmap and releases/switches when state changes.
- Pause frame ticking when the Fragment is stopped.

Validation:

- Screenshot on the real device must show the full character, not cropped upside-down or hidden behind the hero card.
- UI still respects the projector's current physical orientation decision.
- `adb shell dumpsys meminfo com.openclaw.tv` before/after sprite animation must not show a large persistent jump.

**Step 3: Map dialogue/status to sprite actions**

Initial resolver:

- Offline surface or Wi-Fi prompt -> `guide`.
- `HomeStatusTone.CRITICAL` -> `worried`.
- `HomeStatusTone.WARNING` -> `think` unless the notice is an error-like message, then `worried`.
- Valid and visible hero ad, especially when hero ad has focus -> `point_left`.
- Dialogue text changed within the last short window -> `talk`.
- Otherwise -> `idle`.

Rules:

- State resolution must be deterministic and testable in `HomeViewModel`.
- UI focus can temporarily override base state, for example ad focus -> `point_left`.
- Avoid matching only raw Chinese copy where possible; prefer structured state such as `surfaceMode`, `statusTone`, `heroAds`, and focus events.
- If the later real AI dialogue pipeline exposes listening/speaking/thinking events, wire those events into the same `AssistantSpriteState` instead of adding a second animation system.

**Step 4: Finalize ad slot semantics**

Data contract:

- Slot id remains `home.hero`.
- TV client only reads `runtime-manifest.adSlots[]`.
- TV client does not request layout, coordinates, or sizing from home.
- Supported media for P1 is still image creative only.

Empty-state rule:

- Missing slot: hide ad card.
- Disabled slot: hide ad card.
- Slot with no valid creative: hide ad card.
- Image load failure: keep the hero stable and log the failure; do not crash or show broken UI.

**Step 5: Create fixture coverage**

Add or update tests for:

- `HomeAdSlotRegistry` resolves only enabled `home.hero` image creatives.
- expired or future creatives are ignored.
- duplicate creatives are de-duped.
- `HomeViewModel` exposes `heroAds` from the locked runtime manifest.
- empty `heroAds` does not produce a notice that blocks normal homepage use.
- `HomeViewModel` resolves `AssistantSpriteState.GUIDE` for offline Wi-Fi mode.
- `HomeViewModel` resolves `AssistantSpriteState.THINK` for sync/loading warning states.
- `HomeViewModel` resolves `AssistantSpriteState.WORRIED` for critical states.
- `HomeViewModel` resolves `AssistantSpriteState.IDLE` for stable online state.

Run:

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:home:testDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.

**Step 6: Real-device visual acceptance**

Run:

```powershell
adb -s 64WGLJQS5R install -r <new-apk>
adb -s 64WGLJQS5R shell am force-stop com.openclaw.tv
adb -s 64WGLJQS5R shell am start -n com.openclaw.tv/.MainActivity
Start-Sleep -Seconds 5
adb -s 64WGLJQS5R exec-out screencap -p > C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\android-tv\p1-home-character-ad.png
adb -s 64WGLJQS5R exec-out uiautomator dump /dev/tty
adb -s 64WGLJQS5R shell dumpsys meminfo com.openclaw.tv
```

Acceptance:

- Screenshot proves character and ad slot are present in the intended positions.
- Short observation proves idle/talk/think/guide or worried state can switch without flicker.
- No text or app card overlap.
- Focus can move through hero ad only when it has a valid click action.
- OpenClaw PSS remains within the low-end memory budget recorded in the acceptance ledger.
- Crash buffer has no OpenClaw crash.

---

### Task 4: Harden Cast Entry And Fallback

**Files:**
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt`
- Modify: `apps/android-tv-client-kotlin/feature/cast/src/main/java/com/openclaw/tv/feature/cast/DlnaRendererController.kt`
- Modify: `apps/android-tv-client-kotlin/feature/cast/src/main/java/com/openclaw/tv/feature/cast/LightweightDlnaRenderer.kt`
- Test: `apps/android-tv-client-kotlin/feature/cast/src/test/java/...`
- Test: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt`

**Step 1: Add narrow diagnostics**

Add log events for:

- DLNA start success or failure.
- SSDP IPv4 endpoint and description URL.
- Multicast lock acquire/release.
- Lebo service-only start result.
- Fallback transition reason.
- Return-to-home guard result.

Expected log tag:

```kotlin
private const val CAST_TAG = "OpenClawCast"
```

**Step 2: Verify self-built DLNA behavior**

Run:

```powershell
adb -s 64WGLJQS5R logcat -c
adb -s 64WGLJQS5R shell am start -n com.openclaw.tv/.MainActivity
adb -s 64WGLJQS5R exec-out uiautomator dump /dev/tty
```

Manual acceptance:

- Android DLNA-capable media apps can discover the OpenClaw renderer.
- Pushing an image or video opens `DlnaPlaybackActivity`.
- Existing playback is paused/stopped before cast playback.
- After cast playback ends or Back is pressed, user can return to home.

**Step 3: Verify Lebo fallback behavior**

Manual acceptance:

- iPhone AirPlay and Xiaomi mirror options continue to appear when Lebo is needed.
- Home does not unnecessarily bring Lebo UI to foreground.
- If Lebo UI opens, home reclaim happens only when no active private TCP session is detected.
- Idle fallback is trimmed after the configured guard window.

**Step 4: Keep product copy honest**

Text rule:

- Self-built receiver: "媒体投屏".
- Lebo fallback: "苹果/小米镜像".
- Do not say our APK alone perfectly supports system mirror casting.

**Step 5: Run tests**

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:cast:testDebugUnitTest :feature:home:testDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.

---

### Task 5: Finish App Management Acceptance

**Files:**
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/InstalledAppCatalogProvider.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/InstalledAppsAdapter.kt`
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Test: `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt`

**Step 1: Confirm the four required actions**

Acceptance:

- `+` opens the app management overlay.
- Installed apps can be opened from the list.
- Non-system installed apps can open Android uninstall confirmation.
- USB or local APK files show as install or upgrade candidates.

**Step 2: Tighten USB scan safety**

Implementation rule:

- Scan only readable storage roots.
- Keep depth and result caps.
- Run scan on `Dispatchers.IO`.
- Never block homepage first paint.
- Empty state should be useful: "未发现 APK，可把安装包放入 U 盘根目录或 Download 目录".

**Step 3: Improve remote-control discoverability**

UI rule:

- Primary action text says open/install/upgrade.
- Secondary text says long-press OK to uninstall for non-system apps.
- System apps clearly say they cannot be uninstalled here.

**Step 4: Real-device test**

Run:

```powershell
adb -s 64WGLJQS5R push <test-apk-path> /sdcard/Download/
adb -s 64WGLJQS5R shell am start -n com.openclaw.tv/.MainActivity
adb -s 64WGLJQS5R exec-out uiautomator dump /dev/tty
```

Expected:
- APK candidate appears in the management overlay.
- Existing installed package with newer APK is labeled as upgrade.
- Install prompt opens through Android package installer.

---

### Task 6: Close Low-End Resource Trim P1

**Files:**
- Modify: `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt`
- Test: add or extend focused tests only if trim package selection is extracted.

**Step 1: Measure before changing behavior**

Run:

```powershell
adb -s 64WGLJQS5R shell ps | Select-String -Pattern "openclaw|hpplay|dangbei|guanke|qiyi|mgtv|youku|ktcp|bili"
adb -s 64WGLJQS5R shell dumpsys meminfo com.openclaw.tv
adb -s 64WGLJQS5R shell dumpsys meminfo com.hpplay.happyplay.aw
```

Expected:
- Record OpenClaw PSS, Lebo PSS, and obvious playback/store background processes.

**Step 2: Keep trimming best-effort**

Rules:

- Use `killBackgroundProcesses` only.
- Do not attempt hidden APIs.
- Do not freeze system packages from inside APK.
- Keep allowlist for Android, SystemUI, Settings, providers, package installer, input method.
- Preserve Lebo while discovery or active cast may be needed.

**Step 3: Add evidence logging**

Log count and package names attempted for trimming under an `OpenClawTrim` tag.

**Step 4: Acceptance**

After pressing Home/OpenClaw or returning to home:

- Foreground stays OpenClaw.
- Playback apps no longer keep obvious audio/video background state.
- OpenClaw memory remains stable.
- Lebo stays only when discovery or active cast requires it.

---

### Task 7: Verify Boot Launch On The Test Device

**Files:**
- Inspect: `apps/android-tv-client-kotlin/app/src/main/AndroidManifest.xml`
- Inspect: `apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/BootCompletedReceiver.kt`

**Step 1: Broadcast smoke test**

Run:

```powershell
adb -s 64WGLJQS5R shell am force-stop com.openclaw.tv
adb -s 64WGLJQS5R shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.openclaw.tv
Start-Sleep -Seconds 5
adb -s 64WGLJQS5R shell dumpsys window windows | Select-String -Pattern "mCurrentFocus|mFocusedApp"
```

Expected:
- OpenClaw comes to foreground if the system delivers the broadcast to this APK.

**Step 2: Real reboot test**

Run only when safe for the bench:

```powershell
adb -s 64WGLJQS5R reboot
```

Acceptance:

- Device boots normally.
- OpenClaw launches or the launcher fallback is documented as a system restriction.
- No boot-loop behavior.

---

### Task 8: Produce The Next Acceptance APK

**Files:**
- Output: `artifacts/android-tv/openclaw-tv-p1-acceptance-debug-YYYYMMDD-HHMMSS.apk`
- Output: screenshot under `artifacts/android-tv/`
- Update: `docs/testing/2026-05-16-p1-acceptance-ledger.md`

**Step 1: Full local verification**

Run:

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:home:testDebugUnitTest :feature:cast:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Expected:
- `BUILD SUCCESSFUL`.

**Step 2: Package artifact**

Run:

```powershell
$src = "C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\app\build\outputs\apk\debug\app-debug.apk"
$dest = "C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\android-tv\openclaw-tv-p1-acceptance-debug-$(Get-Date -Format yyyyMMdd-HHmmss).apk"
Copy-Item -Path $src -Destination $dest
Get-FileHash -Algorithm SHA256 -Path $dest
```

**Step 3: Install and smoke test**

Run:

```powershell
adb -s 64WGLJQS5R install -r $dest
adb -s 64WGLJQS5R shell am force-stop com.openclaw.tv
adb -s 64WGLJQS5R shell am start -n com.openclaw.tv/.MainActivity
adb -s 64WGLJQS5R exec-out uiautomator dump /dev/tty
adb -s 64WGLJQS5R logcat -b crash -d | Select-String -Pattern "com.openclaw.tv|FATAL EXCEPTION|AndroidRuntime"
```

Expected:
- Homepage displays.
- App rail and quick actions are present.
- Cast hint shows the current Wi-Fi.
- No OpenClaw crash in crash buffer.

---

### Task 9: Vendor P1 Blocker Follow-Up

**Files:**
- Update: `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md`
- Update: `docs/ops/2026-05-06-x10-emcp-v4-material-gap-analysis.md`
- Optional create: `docs/ops/2026-05-16-vendor-p1-follow-up.md`

**Step 1: Send or prepare the vendor list**

Ask for:

- Actual Wi-Fi/BT module confirmation: `W800CS/W8800DCS` vs `8723DS`.
- Current engineering firmware and flash SOP.
- Platform signature / priv-app / permission allowlist policy.
- Miracast / Wi-Fi Display / AirPlay sink capability and vendor entry point.
- DLNA multicast policy and device-name policy.
- OTA mechanism and status API.
- Log export SOP without adb.
- Factory test APK/tools and ageing reports.
- Projection hardware APIs: focus, keystone, brightness, input source, power, temperature, fan.

**Step 2: Acceptance gate**

Only promote a system-level item from "blocked" to "development-ready" when the vendor provides:

- Interface file or SDK.
- Method documentation.
- Error model.
- Demo APK or sample client.
- Test firmware.
- Logs that prove the interface runs on the target board.

---

## Recommended Execution Order

1. Task 0: freeze current local change.
2. Task 1: create P1 acceptance ledger.
3. Task 2: clear the `oc.goods-editor.com` runtime 503 question.
4. Task 3: land the homepage character and character-left ad slot.
5. Task 4: harden cast and fallback evidence.
6. Task 5: finish app management acceptance.
7. Task 6: measure and document low-end resource trimming.
8. Task 7: verify boot launch.
9. Task 8: produce the next acceptance APK.
10. Task 9: prepare vendor P1 blocker follow-up.

## Non-Goals

- Do not promise perfect iPhone or Xiaomi mirror casting from the self-built APK.
- Do not implement process freeze from inside the APK.
- Do not add a new API for ads or app delivery.
- Do not make runtime-manifest refresh frequently while the home UI is running.
- Do not treat X10 production files as a BSP or firmware drop.
