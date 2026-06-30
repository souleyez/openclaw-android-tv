# OpenClaw Android TV Next-Stage Production Development Plan

Date: 2026-06-30

Parent plan:

- `docs/plans/2026-06-30-factory-pilot-to-production-plan.md`

Purpose:

- Keep the factory pilot moving toward production without depending on chat history.
- Separate code work that can continue now from evidence that must come from a real device, factory, or vendor.
- Preserve the `home` boundary: `home` remains the shared control plane, while the Android TV APK owns local device behavior.

## Current Verified State

| Area | Current evidence |
| --- | --- |
| Android TV source branch | `origin/codex/tv-platform-contract`; current head is verified with `git ls-remote --heads origin codex/tv-platform-contract` and recorded in factory handoff manifests at export time |
| Factory APK | `OpenClawTV-0.1.14.apk`, SHA-256 `6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce` |
| OTA APK | `OpenClawTV-0.1.15.apk`, SHA-256 `9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86` |
| OTA release | `ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33` |
| OTA target | `deviceUuid:6741af4b-02b9-4692-99f3-5b4380fbbc3e` |
| `home` deployment | `/srv/home/repo` at `f78944f`; `home-public-admin`, `home-platform-api`, `lease-core`, and `fleet-core` active |
| Latest factory gate | `PENDING`, `failedCount=0`, `pendingCount=4` |
| Current blockers | Target device not online over ADB, target OTA lifecycle report missing, factory fresh-device feedback missing, vendor permission answers missing |

## Next Milestone

The next milestone is factory pilot closure, not broad production rollout.

It is complete only when:

1. A factory fresh unit installs `OpenClawTV-0.1.14.apk` and returns structured feedback.
2. The targeted device receives `0.1.15` from `home` and reports `verified`, `installed`, or a clear recoverable failure.
3. `home` operators can inspect OTA release/report/device state without raw database queries.
4. Vendor or factory answers classify the shipment path as APK-only acceptable, factory provisioning required, system image preinstall required, vendor API required, or blocked.
5. The readiness ledger has dated evidence for every changed gate.

## Workstream A: Factory Evidence Intake

Goal:

- Convert factory feedback into a production decision instead of informal notes.

Inputs:

- `docs/ops/templates/android-tv-factory-feedback.template.json`
- `docs/ops/templates/android-tv-vendor-system-permission.template.json`
- `scripts/android-tv-export-factory-pilot-handoff.ps1`

Commands:

```powershell
scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <factory-feedback.json>
scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-permission.json>
scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -AllowPending
```

Acceptance:

- Factory feedback is classified as `PASS A` or `PASS B`, or the blocker is named.
- Vendor permission feedback is classified into one of the fixed production decisions.
- Factory handoff export contains the install APK, feedback templates, SOP, readiness ledger, next-stage plan, latest production-service evidence, and latest factory-gate evidence.
- Factory pilot gate passes the handoff export check before relying on the package for factory communication.
- `docs/testing/2026-06-30-android-tv-production-readiness.md` gets a dated evidence note.
- `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md` records the same decision.

## Workstream B: One-Device OTA Closure

Goal:

- Prove remote upgrade from factory APK `0.1.14` to OTA APK `0.1.15` through `home`, without local direct install.

Commands:

```powershell
scripts\android-tv-check-ota-canary-report.ps1 -AllowMissingAdminAuth -AllowPending
scripts\android-tv-check-factory-pilot-gates.ps1 -AllowPending
```

Acceptance:

- `target-ota-report.json` shows the target release and a matching target report.
- Accepted closing statuses are `verified`, `installed`, or `reported`.
- If the device reports a failure status, the failure includes a recoverable reason and does not expand rollout.
- The rollout remains one-device scoped until this closes.

## Workstream C: Home Operator Proof

Goal:

- Make `home` usable for routine Android TV operations without raw JSON or direct database inspection.

Already implemented:

- OTA preflight checks for HTTPS artifact URL, SHA-256, artifact size, newer versionCode, explicit installPolicy, and explicit targetScope.
- One-device canary helper from the Known devices list.
- OTA start/resume, pause, and mark-rolled-back controls.
- Rollback helper text requiring a higher `versionCode` recovery package with narrow `targetScope`.
- Ad creative status with slot id, creative URL, preview, enabled state, computed publish state, updated time, and target project.
- Payment orders with account/device, SKU/title, amount, payment state, renewal duration, provider order id, transaction id, and updated time.
- Model leases and resource sessions with active/queued/granted/released operator state.

Next proof:

```powershell
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run test
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run build
npm --prefix C:\Users\soulzyn\Desktop\codex\home run build
```

Latest proof on 2026-06-30:

```text
npm run platform-api:test -> 72/72 pass
npm run platform-api:build -> pass
npm run build -> pass
```

Acceptance:

- Builds pass.
- `/projects/openclaw-android-tv` and `/projects/openclaw-android-tv/devices` render on the deployed `home-public-admin`.
- The operator can see the current one-device OTA release and no matching target report yet.

## Workstream D: APK Runtime Evidence

Goal:

- Keep the RK3128 board stable during normal home, casting, and OTA flows.

Evidence commands when a device is connected:

```powershell
adb devices -l
adb shell pm clear com.openclaw.tv
adb shell monkey -p com.openclaw.tv 1
adb shell dumpsys meminfo com.openclaw.tv
adb shell dumpsys meminfo com.hpplay.happyplay.aw
adb shell ps -A
```

Acceptance:

- Fresh data launch renders Home without cached config.
- iPhone and Xiaomi casting use the accepted Lebo fallback and return to OpenClaw Home after cast ends.
- Memory snapshots are recorded before, during, and after casting.
- Returning Home trims nonessential background activity without breaking casting discovery.

## Workstream E: Rollback And Expansion

Goal:

- Prepare production recovery before expanding beyond one device.

Rules:

- Rollback is not downgrading `versionCode`.
- Recovery requires a new APK with a higher `versionCode` and narrow `targetScope`.
- A bad release must be paused or marked `rolled_back` before publishing recovery.
- Expanded rollout starts only after one-device OTA closes with evidence.

Acceptance:

- A live target device receives the higher-version recovery release through `/api/ota/bootstrap`.
- A non-target device does not receive the recovery release.
- The readiness ledger records the rollback drill result.

## Next Batch Order

1. Verify `home` operator build and deployed pages after the latest operator UI changes.
2. Keep the hourly OTA canary monitor active until the target device reports.
3. When ADB or factory feedback is available, run the full factory pilot gate with feedback JSON files.
4. Capture APK runtime evidence on a connected device.
5. Only after one-device OTA closes, prepare a recovery-package drill and then decide whether to expand rollout.

## Stop Conditions

Do not expand rollout while any of these remain true:

- Fresh factory unit feedback is missing.
- Target OTA lifecycle report is missing.
- Restore-factory behavior is unknown.
- Vendor/system permission answers are missing.
- No-ADB support path is unknown.
- iPhone or Xiaomi casting acceptance has no final evidence.
