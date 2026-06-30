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
| Factory APK | `OpenClawTV-0.1.14.apk`, SHA-256 `6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce`; gate verifies signer cert SHA-256 `2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522` with v1/v2/v3 signing |
| OTA APK | `OpenClawTV-0.1.15.apk`, SHA-256 `9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86`; gate verifies the same signer cert SHA-256 with v1/v2/v3 signing |
| Next APK candidate | `OpenClawTV-0.1.16.apk`, SHA-256 `2206353e7f653a52ecaab125c91b761b144d275ee2deccda6d4b59c7133385ac`; built from source through `480af32` with versionCode `2026070102` and the same 3128 platform signing certificate; not yet published as the active `home` OTA release |
| OTA release | `ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33` |
| OTA target | `deviceUuid:6741af4b-02b9-4692-99f3-5b4380fbbc3e` |
| `home` deployment | `/srv/home/repo` at `f78944f`; `home-public-admin`, `home-platform-api`, `lease-core`, and `fleet-core` active; operator pages render through the server-local `home-public-admin` service on `127.0.0.1:3002` |
| Latest factory gate | `PENDING`, `failedCount=0`, `pendingCount=5` |
| Current blockers | Production readiness ledger evidence rows pending, target device not online over ADB, target OTA lifecycle report missing, factory fresh-device feedback missing, vendor permission answers missing |

## Next Milestone

The next milestone is factory pilot closure, not broad production rollout.

It is complete only when:

1. A factory fresh unit installs `OpenClawTV-0.1.14.apk` and returns structured feedback.
2. The targeted device receives `0.1.15` from `home` and reports `verified`, `installed`, `reported`, or `RECOVERABLE_FAILURE` backed by a clear recoverable note.
3. `home` operators can inspect OTA release/report/device state without raw database queries.
4. Vendor or factory answers classify the shipment path as APK-only acceptable, factory provisioning required, system image preinstall required, vendor API required, or blocked.
5. The readiness ledger has dated evidence for every changed gate.

## Workstream A: Factory Evidence Intake

Goal:

- Convert factory feedback into a production decision instead of informal notes.

Inputs:

- `docs/ops/templates/android-tv-factory-feedback.template.json`
- `docs/ops/templates/android-tv-vendor-system-permission.template.json`
- `scripts/android-tv-audit-factory-pilot-plan.ps1`
- `scripts/android-tv-ingest-factory-pilot-return-package.ps1`
- `scripts/android-tv-verify-factory-handoff-archive.ps1`
- `scripts/android-tv-refresh-factory-pilot-evidence.ps1`
- `scripts/android-tv-check-factory-pilot-expansion-readiness.ps1`
- `scripts/android-tv-check-production-readiness-ledger.ps1`
- `scripts/android-tv-ingest-factory-pilot-feedback.ps1`
- `scripts/android-tv-export-factory-pilot-handoff.ps1`
- `scripts/android-tv-test-factory-return-package-intake.ps1`
- `scripts/android-tv-test-vendor-permission-classifier.ps1`
- `scripts/android-tv-check-no-adb-diagnostic-package.ps1`
- `scripts/android-tv-test-no-adb-diagnostic-package.ps1`
- `scripts/android-tv-test-next-apk-candidate-gate.ps1`
- `scripts/android-tv-test-handoff-archive-next-candidate-evidence.ps1`

Commands:

```powershell
scripts\android-tv-audit-factory-pilot-plan.ps1 -AllowIncomplete
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <factory-return.zip-or-folder> -AllowPending
scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath <handoff.zip>
scripts\android-tv-refresh-factory-pilot-evidence.ps1
scripts\android-tv-check-factory-pilot-expansion-readiness.ps1 -AllowBlocked
scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending
scripts\android-tv-ingest-factory-pilot-feedback.ps1 -FactoryFeedbackPath <factory-feedback.json> -VendorPermissionPath <vendor-permission.json> -EvidenceRoot <factory-return-folder> -AllowPending
scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <factory-feedback.json> -EvidenceRoot <factory-return-folder> -RequireEvidenceRoot
scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-permission.json> -EvidenceRoot <factory-return-folder> -RequireEvidenceRoot
scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <factory-feedback.json> -FactoryFeedbackEvidenceRoot <factory-return-folder> -VendorPermissionPath <vendor-permission.json> -VendorPermissionEvidenceRoot <factory-return-folder> -AllowPending
scripts\android-tv-test-factory-return-package-intake.ps1
scripts\android-tv-test-vendor-permission-classifier.ps1
scripts\android-tv-check-no-adb-diagnostic-package.ps1 -ManifestPath <factory-return-folder>\evidence\factory-return\logs\no-adb-diagnostic-manifest.json -EvidenceRoot <factory-return-folder> -RequireEvidenceRoot -FailOnIncomplete
scripts\android-tv-test-no-adb-diagnostic-package.ps1
scripts\android-tv-test-next-apk-candidate-gate.ps1
scripts\android-tv-test-handoff-archive-next-candidate-evidence.ps1
```

Acceptance:

- Factory pilot plan audit maps current evidence to every `Definition Of Next Milestone Done` item and remains `INCOMPLETE` until external evidence closes.
- Returned factory package intake can accept a returned zip or folder, locate both feedback JSON files, and run the classifier/gate intake.
- Returned factory package intake records the returned package kind, byte size, entry count, and zip SHA-256 when the return is a `.zip`, so external feedback evidence can be tied back to the original returned package.
- Returned factory package intake rejects unsafe zip entries before extraction, including absolute paths, Windows drive paths, empty entry names, and `..` traversal segments.
- Returned factory package intake rejects returned folders that contain reparse-point entries such as symbolic links or junctions before copying, so folder returns cannot pull evidence from outside the package.
- Returned factory package intake rejects ambiguous packages with more than one `android-tv-factory-feedback.json` or more than one `android-tv-vendor-system-permission.json`, so stale feedback copies cannot be selected silently.
- Returned factory package intake requires exactly one `feedback/return-package-checklist.json` and validates its schema, required returned files, required evidence folders, required evidence fields, release id, target device UUID, and target versionCode before running classifier/gate intake.
- Returned factory package intake validates package-relative evidence paths from `screenshotOrVideoPath`, `logsPath`, and `evidencePath`; referenced files or folders must exist inside the returned package. Direct JSON intake can run the same validation when `-EvidenceRoot` is provided.
- Factory feedback classification requires both `screenshotOrVideoPath` and `logsPath`; a returned package with PASS-style fields but no logs package remains `INCOMPLETE`, so no-ADB diagnostics cannot be skipped.
- No-ADB diagnostic manifests can be machine-validated when ADB is unavailable; the manifest must list package-relative install, Home, casting, OTA, crash/ANR, process, and memory evidence files that exist inside the returned package.
- Factory pilot gates require `-FactoryFeedbackEvidenceRoot` whenever `-FactoryFeedbackPath` is supplied; otherwise factory feedback remains incomplete because screenshot/video and logs package paths cannot be verified.
- Vendor permission classification requires `-VendorPermissionEvidenceRoot` whenever `-VendorPermissionPath` is supplied to factory pilot gates; otherwise vendor permission remains incomplete because `evidencePath` cannot be verified.
- Factory pilot evidence refresh writes one `artifacts/factory-pilot-refresh/refresh-*` summary linking the latest handoff, gate, and expansion evidence.
- Factory pilot evidence refresh reuses the same factory gate evidence for expansion-readiness evaluation, so one refresh run does not duplicate remote SSH/service checks.
- Factory pilot plan audit requires the operator OTA snapshot to match the expected one-device release before treating either OTA delivery or operator OTA UI visibility as `PASS`.
- Factory handoff archive verification can validate a transferred `.zip` and `.sha256.txt` sidecar without extracting it.
- Factory handoff archive verification and factory gate reject unsafe zip entries such as absolute paths, Windows drive paths, empty entry names, or `..` traversal segments.
- Factory return package intake has a repeatable regression script that builds throwaway fixtures for a complete zip return, missing checklist, missing evidence directories, and missing referenced log file, then verifies the expected PASS/PENDING/FAIL boundaries.
- Factory pilot expansion readiness returns `PASS` before any rollout expansion only when the factory gate summary and `factory-pilot-gates.json` agree, every required gate is present, and every required gate is `PASS`; `BLOCKED` means stay at one-device/factory-pilot scope.
- Production readiness ledger audit finds all required rows and fields, checks that code-spanned local evidence references resolve to existing files, verifies referenced `home` commits, and verifies referenced Codex automations exist and are `ACTIVE`; it remains `PENDING` until the real external evidence rows close.
- Production readiness ledger summary prints `pendingGates` and `pendingBlockers`, and the factory pilot gate includes `pendingGates` in the production-readiness detail so the next missing evidence can be identified from top-level gate output.
- Returned factory/vendor JSON files are copied into a single `artifacts/factory-pilot-intake/intake-*` evidence directory with classifier and gate summaries.
- Factory feedback is classified as `PASS A` or `PASS B`, or the blocker is named.
- Vendor permission feedback is classified into one of the fixed production decisions.
- Vendor permission templates treat `unknown` as incomplete input, not as a failed vendor path; explicit `no` answers are required before the classifier marks a path failed or blocked.
- Vendor permission classification has a repeatable local regression script covering APK-only acceptable, factory provisioning required, system image preinstall required, vendor API required, blocked, unknown-template incomplete, and missing evidence path incomplete outcomes.
- Factory handoff export contains the install APK, feedback templates, SOP, readiness ledger, next-stage plan, latest production-service evidence, and latest factory-gate evidence.
- Factory handoff export records the local source HEAD and `origin/<branch>` HEAD in `handoff-manifest.json`; archive verification and factory gate require the remote branch HEAD to match the packaged source HEAD.
- Factory feedback templates do not prefill real-device result fields with `PASS`; unfilled install, Home, cold boot, casting, OTA, screenshot/video, and logs package fields must classify as incomplete or pending.
- Factory handoff export contains `feedback/README-return-package.md` so the factory can return one zip/folder with the two filled JSON files and attached screenshots/logs in stable paths.
- Factory handoff export contains `evidence/factory-return/logs/no-adb-diagnostic-manifest.json` as the no-ADB diagnostic template.
- Factory handoff export contains `feedback/return-package-checklist.json` and real `evidence/factory-return/` placeholder folders so required returned files, evidence folders, required fields, rejected path rules, and the one-device OTA target are machine-readable before the factory sends the package back.
- Factory handoff export also creates a `.zip` archive plus `.sha256.txt` sidecar for transfer; the APK inside remains the only APK to install on the factory unit.
- Factory handoff export includes `handoff-files.sha256.txt`, and the gate verifies hash-manifest contents plus required archive entries, including factory/OTA APK signature evidence files, `feedback/return-package-checklist.json`, `evidence/factory-return/logs/no-adb-diagnostic-manifest.json`, and `evidence/production-services/operator-ota-snapshot/target-ota-report.json`, before accepting the package.
- Factory handoff archive verification computes the SHA-256 of `apk/OpenClawTV-0.1.14.apk` inside the zip and requires it to equal the expected factory APK hash.
- Factory handoff archive verification and factory gate both validate the operator OTA snapshot contents: status must be `PASS`, `PENDING`, or `RECOVERABLE_FAILURE`, and release id, target device UUID, versionCode, targetScope, and artifact SHA-256 must match the expected one-device OTA.
- Factory handoff archive verification requires `evidence/factory-pilot-gate/next-apk-candidate-signature.txt`, so the transfer package proves the next APK candidate was covered by the latest factory gate.
- Factory pilot gate passes the handoff export check before relying on the package for factory communication.
- Factory pilot gate verifies both the factory APK and OTA APK signing certificate SHA-256 before treating the artifacts as release-ready.
- Factory pilot gate also verifies the next APK candidate hash and signing certificate, so a future OTA or recovery candidate cannot sit outside the machine-checked release chain.
- `docs/testing/2026-06-30-android-tv-production-readiness.md` gets a dated evidence note.
- `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md` records the same decision.

## Workstream B: One-Device OTA Closure

Goal:

- Prove remote upgrade from factory APK `0.1.14` to OTA APK `0.1.15` through `home`, without local direct install.

Commands:

```powershell
scripts\android-tv-check-ota-canary-report.ps1 -AllowMissingAdminAuth -AllowPending
scripts\android-tv-test-ota-canary-report.ps1
scripts\android-tv-check-factory-pilot-gates.ps1 -AllowPending
```

Acceptance:

- `target-ota-report.json` shows the target release and a matching target report.
- Accepted successful closing statuses are `verified`, `installed`, or `reported`.
- If the device reports a failure status, the canary helper may emit `RECOVERABLE_FAILURE` only when the failure `note` includes a clear recoverable reason; that closes single-device diagnosis but keeps rollout blocked.
- APK-side install attempts report `installing`, `prompt_shown`, or recoverable `install_failed` notes for silent install permission, manual confirmation, or system installer fallback issues, so target-device diagnosis does not stop at `verified`.
- OTA canary report checking has a local snapshot regression script that covers no-report PENDING, accepted successful statuses, recoverable failure, hard failure, and wrong release/version evidence.
- The rollout remains one-device scoped until the canary closes with a successful status.

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
- `/projects/openclaw-android-tv` and `/projects/openclaw-android-tv/devices` render on the deployed server-local `home-public-admin` at `127.0.0.1:3002`.
- `scripts/android-tv-check-production-services.ps1` verifies that `https://oc.goods-editor.com/login`, `/projects/openclaw-android-tv`, and `/projects/openclaw-android-tv/devices` return `404`, keeping the API/storage host separate from the operator UI.
- `scripts/android-tv-check-production-services.ps1` also writes `operator-ota-snapshot/target-ota-report.json` from the admin OTA snapshot, proving the operator path can see the current one-device release and whether a target report exists without raw database inspection.
- The operator can see the current one-device OTA release and no matching target report yet.

## Workstream D: APK Runtime Evidence

Goal:

- Keep the RK3128 board stable during normal home, casting, and OTA flows.

Evidence commands when a device is connected:

```powershell
adb devices -l
scripts\android-tv-capture-production-readiness.ps1 -LaunchHome
adb shell pm clear com.openclaw.tv
adb shell monkey -p com.openclaw.tv 1
adb shell dumpsys meminfo com.openclaw.tv
adb shell dumpsys meminfo com.hpplay.happyplay.aw
adb shell ps -A
```

Acceptance:

- Fresh data launch renders Home without cached config.
- `scripts/android-tv-capture-production-readiness.ps1` writes both `summary.txt` and machine-readable `production-readiness-capture.json`; `NO_ADB_DEVICE` is valid boundary evidence only and does not close runtime gates.
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
- Expanded rollout starts only after one-device OTA closes with successful evidence; `RECOVERABLE_FAILURE` requires recovery-package evidence before any expansion decision.

Acceptance:

- A live target device receives the higher-version recovery release through `/api/ota/bootstrap`.
- A non-target device does not receive the recovery release.
- The readiness ledger records the rollback drill result.

## Next Batch Order

1. Verify `home` operator build and server-local deployed pages after the latest operator UI changes.
2. Keep the hourly OTA canary monitor active until the target device reports.
3. Keep `0.1.16 / 2026070102` ready as the next one-device OTA or recovery candidate, but do not publish it over the current `0.1.15` canary without an operator decision.
4. When ADB or factory feedback is available, run the full factory pilot gate with feedback JSON files.
5. Capture APK runtime evidence on a connected device.
6. Only after one-device OTA closes successfully, prepare a recovery-package drill and then decide whether to expand rollout; if it closes as `RECOVERABLE_FAILURE`, run recovery first and keep `do_not_expand`.

## Stop Conditions

Do not expand rollout while any of these remain true:

- Fresh factory unit feedback is missing.
- Target OTA lifecycle report is missing.
- Restore-factory behavior is unknown.
- Vendor/system permission answers are missing.
- No-ADB support path is unknown.
- iPhone or Xiaomi casting acceptance has no final evidence.
