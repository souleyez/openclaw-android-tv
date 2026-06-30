# Factory Pilot To Production Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move OpenClaw Android TV from the current `0.1.14` factory trial APK into a controlled factory pilot and then a production-ready rollout path.

**Architecture:** Treat `home` as the shared control plane and `openclaw-android-tv` as the first production client. Keep Android TV layout and device behavior in the APK, while `home` owns runtime manifest content, OTA releases, entitlement/payment summaries, model/resource leasing, and ad creative publication. Do not split new TV-specific backend silos out of `home`.

**Tech Stack:** Kotlin Android Views on RK3128, Gradle, adb, Fastify/TypeScript `home/apps/platform-api`, Next.js public admin in `home`, Postgres migrations, existing `oc.goods-editor.com/api` and `gm.goods-editor.com/ads` host boundaries.

---

## Current State On 2026-06-30

- Factory trial APK exists as `OpenClawTV-0.1.14.apk`.
- APK version is `0.1.14 / 2026062401`.
- APK is signed with the 3128 platform signature line.
- Test device verified install, default Home, reboot to OpenClaw Home, `INSTALL_PACKAGES`, and `RECEIVE_BOOT_COMPLETED`.
- Factory SOP exists at `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md`.
- The `openclaw-android-tv` worktree still has uncommitted APK changes, home sprite/ad changes, and the factory SOP.
- `home` already has runtime-manifest, OTA, model lease, payment order, resource-session, and public admin surfaces, but the latest `0.1.14` factory APK has not yet been used to prove a full production OTA loop from `home`.

## Stage 0: Source And Release Hygiene

**Goal:** Make the current factory APK traceable to source before further feature work.

### Task 0.1: Freeze the 0.1.14 release scope

**Files:**
- Inspect: `C:\Users\soulzyn\Desktop\openclaw-android-tv`
- Inspect: `C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk`

**Step 1: Record the exact worktree state**

Run:

```powershell
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv status --short
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv diff --stat
```

Expected: show only intentional Android TV APK/home/sprite/SOP changes plus no unexpected signing material.

**Step 2: Re-run focused Android validation**

Run:

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

**Step 3: Verify the released APK hash**

Run:

```powershell
Get-FileHash -Algorithm SHA256 C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk
```

Expected: `6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce`.

**Step 4: Commit the source and docs**

Run scoped staging only:

```powershell
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/build.gradle.kts
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/main/AndroidManifest.xml
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/OpenClawTvApplication.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/runtime/OwnApkUpdateRuntimeSyncAdapter.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/upgrade/OwnApkUpdateInstaller.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/upgrade/OwnApkAutoInstallCoordinator.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add apps/android-tv-client-kotlin/app/src/test/java/com/openclaw/tv/upgrade/OwnApkAutoInstallCoordinatorTest.kt
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "feat: prepare 0.1.14 factory home default rollout"
```

Expected: one commit containing the APK runtime/update/default-Home changes and SOP. If sprite/home art changes are intentional and not yet committed, commit them separately.

## Stage 1: Factory New-Machine Pilot

**Goal:** Prove that the factory can install and validate the APK on a fresh machine without Codex-side assumptions.

### Task 1.1: Send the minimal factory package

**Files:**
- Use: `C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk`

**Step 1: Provide factory instructions**

The installable artifact remains:

```text
OpenClawTV-0.1.14.apk
```

For full pilot handoff, export and transfer the generated handoff `.zip` plus `.sha256.txt` sidecar. The archive contains the install APK, feedback templates, SOP, readiness ledger, and latest gate evidence; factory still installs only `OpenClawTV-0.1.14.apk`.
The archive also contains `feedback/README-return-package.md`; factory should fill the two JSON files in `feedback/`, place screenshots/logs under `evidence/factory-return/` when available, then return the whole zip or folder for intake.
The archive also contains `feedback/return-package-checklist.json` and real `evidence/factory-return/` placeholder folders; use them as the machine-readable returned-package checklist and stable evidence drop locations for required feedback files, evidence folders, required fields, rejected zip/folder path rules, and the one-device OTA target.

Verify the received archive before using it:

```powershell
scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath <handoff.zip>
```

Required factory feedback:

```text
1. Device model and firmware version
2. Install result
3. Whether OpenClaw can be set as default Home
4. Whether power-off cold boot returns to OpenClaw Home
5. Whether restore-factory keeps or removes the APK
6. Whether iPhone/Xiaomi casting can discover the TV
7. Screenshot or video of the final Home screen
8. Logs package or no-ADB-equivalent diagnostic package for install/Home/casting/OTA evidence
```

**Step 2: Collect factory evidence**

Ask factory to run, if ADB is available:

```powershell
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell dumpsys package com.openclaw.tv
adb shell getprop ro.build.fingerprint
```

Expected: Home resolves to `com.openclaw.tv/.MainActivity`, package version is `2026062401`.

When the filled factory and vendor feedback JSON files return, ingest them from the repo root:

```powershell
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <factory-return.zip-or-folder> -AllowPending
scripts\android-tv-ingest-factory-pilot-feedback.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -EvidenceRoot <factory-return-folder> -AllowPending
scripts\android-tv-test-vendor-permission-classifier.ps1
scripts\android-tv-check-no-adb-diagnostic-package.ps1 -ManifestPath <factory-return-folder>\evidence\factory-return\logs\no-adb-diagnostic-manifest.json -EvidenceRoot <factory-return-folder> -RequireEvidenceRoot -FailOnIncomplete
```

Expected: either one `artifacts\factory-pilot-return-intake\return-*` directory for a returned zip/folder, or one `artifacts\factory-pilot-intake\intake-*` directory when the two JSON files are provided directly. Both paths must contain copied feedback, factory classification, vendor permission classification, and the factory pilot gate summary.
Returned zip/folder intake requires exactly one `feedback/return-package-checklist.json`; it validates the checklist schema, required returned files, required evidence folders, required evidence fields, release id, target device UUID, and target versionCode before running the classifier/gate intake.
For a returned zip/folder, any evidence path written in `screenshotOrVideoPath`, `logsPath`, or vendor `evidencePath` must resolve to an existing file or folder inside the returned package. For direct JSON intake, pass `-EvidenceRoot` when those fields contain package-relative paths.
Factory feedback cannot classify as complete unless both `screenshotOrVideoPath` and `logsPath` are present. Factory pilot gates that consume direct JSON feedback must also receive `-FactoryFeedbackEvidenceRoot <factory-return-folder>` and `-VendorPermissionEvidenceRoot <factory-return-folder>` so factory and vendor evidence paths are verified against returned files.
Returned folders must not contain symbolic links, junctions, or other reparse-point entries. The return-package intake rejects these before copying the folder so external files cannot be pulled into evidence by reference.
If ADB is unavailable, factory should fill `evidence/factory-return/logs/no-adb-diagnostic-manifest.json`; OpenClaw validates the manifest and every referenced install/Home/casting/OTA/crash/process/memory file with `scripts/android-tv-check-no-adb-diagnostic-package.ps1`.

### Task 1.2: Decide preinstall strategy

**Files:**
- Update: `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md`

**Step 1: Classify factory result**

Use this decision table:

```text
PASS A: Fresh install + default Home + cold boot pass
PASS B: Fresh install pass, default Home needs factory tool
BLOCKED A: Install fails due signature/permission
BLOCKED B: Default Home cannot persist
BLOCKED C: Restore factory removes APK and product requires persistence
```

**Step 2: Update SOP with the factory result**

Add a dated section:

```text
2026-07-xx factory fresh-device result:
- Firmware:
- Install:
- Default Home:
- Cold boot:
- Restore factory:
- Casting:
- Required factory action:
```

**Step 3: Commit**

Run:

```powershell
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv add docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md
git -C C:\Users\soulzyn\Desktop\openclaw-android-tv commit -m "docs: record factory fresh-device pilot result"
```

## Stage 2: Production OTA Proof

**Goal:** Prove remote upgrade from the installed factory APK through `home`, without local direct install.

### Task 2.1: Build a no-op 0.1.15 OTA candidate

**Files:**
- Modify: `apps/android-tv-client-kotlin/app/build.gradle.kts`
- Create artifact: `OpenClawTV-0.1.15.apk`

**Step 1: Bump version only**

Set:

```kotlin
versionCode = 2026070101
versionName = "0.1.15"
```

**Step 2: Run tests**

Run:

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

**Step 3: Build and sign with the same 3128 platform signature**

Use the established 3128 signing workflow. Do not print or commit key material.

**Step 4: Verify signature and hash**

Run:

```powershell
apksigner verify --verbose --print-certs OpenClawTV-0.1.15.apk
Get-FileHash -Algorithm SHA256 OpenClawTV-0.1.15.apk
```

Expected: certificate SHA-256 remains `2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522`.

Execution note on 2026-06-30:

```text
0.1.15 / 2026070101 has already been published as the current one-device home OTA canary and remains pending target-device lifecycle evidence.
0.1.16 / 2026070102 has also been built as the next signed APK candidate from source through 480af32, including APK install attempt status reporting. It is signed with the same 3128 platform certificate and is suitable as a future one-device OTA or recovery candidate only after an operator decision.
```

### Task 2.2: Publish OTA through home

**Files:**
- Inspect/modify as needed: `C:\Users\soulzyn\Desktop\codex\home`
- Use public admin or API: `home` OTA release surface

**Step 1: Upload OTA artifact to the agreed storage host**

Store APK in the production OTA storage path. Record:

```text
artifactUrl
artifactSha256
artifactSize
versionName
versionCode
releaseChannel
installPolicy
```

**Step 2: Create OTA release in home**

Create a release for:

```text
projectKey=openclaw-android-tv
versionName=0.1.15
versionCode=2026070101
updateMode=full_apk
releaseChannel=factory or stable
targetScope=one test device first
installPolicy=vendor_silent if permission is granted, otherwise deferred_prompt
```

**Step 3: Start rollout at one-device scope**

Expected: the target test device receives the manifest only when it matches the scope.

**Step 4: Verify device lifecycle**

Expected state progression:

```text
offered -> downloading -> downloaded -> verified -> installing -> installed
```

If installation falls back to UI, expected:

```text
verified -> prompt_shown -> installed
```

If silent installation cannot proceed because the factory permission is missing or PackageInstaller submission fails, the client should report `install_failed` with a recoverable note such as `permission required`, `manual confirmation`, or `system installer`, so the one-device canary closes as diagnosable `RECOVERABLE_FAILURE` instead of staying silent.

**Step 5: Commit any home-side fixes**

Run:

```powershell
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run test
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run build
git -C C:\Users\soulzyn\Desktop\codex\home status --short
```

Commit only intentional changes.

## Stage 3: Home Operator Closure

**Goal:** Make `home` usable as an operations console for Android TV without editing raw JSON or relying on Codex for routine releases.

### Task 3.1: OTA operator hardening

**Files:**
- Modify: `C:\Users\soulzyn\Desktop\codex\home\app\ControlPlaneDashboardClient.js`
- Modify/test: `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api`

**Step 1: Add release preflight checks**

Validate before creating an OTA release:

```text
artifactUrl is HTTPS
artifactSha256 is 64 hex chars
artifactSize > 0
versionCode > latest known versionCode for project/channel
installPolicy is explicit
targetScope is explicit
```

**Step 2: Add one-device canary helper**

Operator UI should make it easy to start with one device, then expand.

**Step 3: Add rollback helper text**

Make clear that rollback requires a higher `versionCode` recovery package.

**Step 4: Test and build**

Run:

```powershell
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run test
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run build
npm --prefix C:\Users\soulzyn\Desktop\codex\home run build
```

### Task 3.2: Ad and persona publishing closure

**Files:**
- Modify/test: `home` public admin and platform API
- Modify/test: Android TV home rendering if contract changes are needed

**Step 1: Keep the boundary**

Use:

```text
oc.goods-editor.com/api for runtime manifests and policy
gm.goods-editor.com/ads/... for production ad assets
```

**Step 2: Add ad creative status in operator UI**

Show:

```text
slot id
creative URL
enabled state
publish state
last published time
preview image
target project
```

**Step 3: Add Android TV visual acceptance**

Capture a device screenshot where `home.hero` creative renders in the real ad slot.

Expected: no overlap with character/persona, app row, or bottom status row.

### Task 3.3: Model lease and payment operations

**Files:**
- Modify/test: `home/apps/platform-api`
- Modify/test: `home/app/ControlPlaneDashboardClient.js`
- Verify: `codex-web` payment callback path if payment behavior changes

**Step 1: Add visible order state**

Operator should see:

```text
account/device
plan code
amount
payment state
renewal duration
provider order id
created/updated time
```

**Step 2: Add lease state summary**

Operator should see:

```text
active model leases
queued resource sessions
granted resource sessions
expired/released sessions
```

**Step 3: Run payment smoke**

Use the existing 0.01 yuan package until production prices are decided.

Expected: TV service center shows the package, QR payment completes, entitlement/lease state updates.

## Stage 4: APK Product Hardening

**Goal:** Reduce factory and user support risk on low-end RK3128 boards.

### Task 4.1: Fresh install and factory reset behavior

**Files:**
- Android app source
- Factory SOP docs

**Step 1: Test a fresh data launch**

Run:

```powershell
adb shell pm clear com.openclaw.tv
adb shell monkey -p com.openclaw.tv 1
```

Expected: Home renders without requiring cached config.

**Step 2: Test factory reset behavior with factory**

If reset removes APK, decide:

```text
factory provisioning installs after reset
or system image preinstalls APK
```

### Task 4.2: Casting acceptance

**Files:**
- `apps/android-tv-client-kotlin/feature/cast`
- `apps/android-tv-client-kotlin/feature/home`

**Step 1: Define acceptance by source**

Use:

```text
iPhone screen mirroring appears and connects through Lebo fallback
Xiaomi casting appears and connects through Lebo fallback
DLNA media renderer is discoverable for media-only casting where supported
Return to OpenClaw Home after cast ends
```

**Step 2: Capture evidence**

Record:

```text
phone model
phone OS
same Wi-Fi SSID
discovery result
connect result
audio result
return Home result
device PSS before/during/after
```

### Task 4.3: Low-memory background policy

**Files:**
- Android app background trim path
- Home lifecycle code

**Step 1: Measure before changing**

Run:

```powershell
adb shell dumpsys meminfo com.openclaw.tv
adb shell dumpsys meminfo com.hpplay.happyplay.aw
adb shell ps -A
```

**Step 2: Keep Lebo only as needed**

Maintain the current product decision:

```text
keep enough Lebo for discovery/cast fallback
avoid foregrounding Lebo unnecessarily
return to OpenClaw Home after cast flow
trim other playback/store background processes when returning Home
```

## Stage 5: Vendor/System Integration

**Goal:** Decide what must be moved from APK workaround to factory/system integration before real volume shipment.

### Task 5.1: Factory permission checklist

**Files:**
- Update: `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md`
- Update: `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md`

**Step 1: Request final factory answers**

Ask factory:

```text
1. Can OpenClaw be preinstalled as priv-app?
2. Can default Home be set in firmware?
3. Can INSTALL_PACKAGES be whitelisted?
4. Can restore-factory preserve or reinstall OpenClaw?
5. Can Lebo or equivalent casting service be retained and whitelisted?
6. Is there a no-ADB log export path?
7. Is there a system OTA path for firmware updates?
```

**Step 2: Convert answers into decisions**

Use:

```text
APK-only acceptable
factory provisioning required
system image preinstall required
vendor API required
blocked
```

Run the local decision regression before accepting a classifier or gate change:

```powershell
scripts\android-tv-test-vendor-permission-classifier.ps1
```

Expected: `PASS`, covering every fixed decision above plus unknown-template and missing-evidence incomplete outcomes.

### Task 5.2: Production readiness gate

**Files:**
- Create: `docs/testing/YYYY-MM-DD-android-tv-production-readiness.md`

**Step 1: Create readiness ledger**

Rows:

```text
factory fresh install
default Home persistence
cold boot
restore factory behavior
OTA one-device canary
OTA expanded rollout
payment renewal
ad publish and render
iPhone casting
Xiaomi casting
low-memory soak
no-ADB support evidence
server health and cert renewal
rollback drill
```

**Step 2: Require explicit pass/fail evidence**

Each row needs:

```text
status
evidence path
owner
blocker
decision
```

Audit the ledger before expansion:

```powershell
scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending
```

Expected: no missing rows or empty required fields. `PENDING` is acceptable only while named external evidence is still missing; it is not a production pass.

## Recommended Execution Order

1. Stage 0: Commit and tag current `0.1.14` source state.
2. Stage 1: Wait for factory new-machine feedback and classify result.
3. Stage 2: Build `0.1.15` no-op OTA and prove remote update through `home`.
4. Stage 3: Harden home operator workflows for OTA, ads, lease, and payment.
5. Stage 4: Run APK casting, memory, and fresh-install hardening.
6. Stage 5: Lock factory/system integration decisions for production.

## Stop Conditions

Do not expand beyond factory pilot if any of these remain unresolved:

- Fresh machine cannot persist default Home.
- Remote OTA cannot update from `0.1.14` to a higher signed package.
- Factory cannot state what happens after restore-factory.
- Casting acceptance fails with no accepted fallback.
- `home` cannot show OTA/payment/resource-session state without manual database inspection.
- Server certificate renewal and release artifact storage are not monitored.

Run the expansion guard before any rollout expansion:

```powershell
scripts\android-tv-refresh-factory-pilot-evidence.ps1
scripts\android-tv-check-factory-pilot-expansion-readiness.ps1
```

Expected: refresh completes without `FAIL`, and the expansion guard returns `PASS`. `BLOCKED` or `FAIL` means do not expand.

## Definition Of Next Milestone Done

The next milestone is complete when:

- `0.1.14` source and SOP are committed.
- Factory fresh-machine feedback is recorded.
- One signed `0.1.15` OTA is delivered via `home` to one test device.
- The device reports installed or a clear recoverable failure; recoverable failure does not permit expanded rollout.
- `home` operator UI can create/check OTA releases without raw JSON edits.
- A production readiness ledger exists with pass/fail evidence for the rows above.

Audit this definition before claiming the milestone is done:

```powershell
scripts\android-tv-audit-factory-pilot-plan.ps1
```
