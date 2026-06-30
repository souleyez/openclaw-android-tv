# OpenClaw Android TV Production Readiness Ledger

Date: 2026-06-30

Scope:

- Client: `com.openclaw.tv`
- Factory APK: `0.1.14 / 2026062401`
- OTA candidate: `0.1.15 / 2026070101`
- Control plane: `home` on `https://oc.goods-editor.com/api`
- Ad asset host: `https://gm.goods-editor.com/ads/...`

## Current Release Evidence

| Item | Evidence |
| --- | --- |
| Factory APK | `C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.14.apk` |
| Factory APK SHA-256 | `6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce` |
| OTA APK | `C:\Users\soulzyn\Desktop\openclaw-tv-installers\OpenClawTV-0.1.15.apk` |
| OTA APK SHA-256 | `9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86` |
| OTA APK size | `12119959` |
| Signing certificate SHA-256 | `2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522` |
| Home OTA release | `ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33` |
| OTA target scope | `deviceUuid:6741af4b-02b9-4692-99f3-5b4380fbbc3e` |
| OTA artifact URL | `https://oc.goods-editor.com/storage/ota/openclaw-android-tv/OpenClawTV-0.1.15.apk` |
| Latest remote OTA canary snapshot | `release found; matchingReports=0; latestReport=null` |
| Home operator deployment | `f78944f feat: link device detail from dashboard` |
| Android TV source branch | `origin/codex/tv-platform-contract`; current head is verified with `git ls-remote --heads origin codex/tv-platform-contract` and recorded in factory handoff manifests at export time |
| Android TV factory source tag | `android-tv-0.1.14-factory` at `5de26b8 feat: add summer assistant sprite set` |
| Next-stage execution plan | `docs/plans/2026-06-30-next-stage-production-development-plan.md` |
| Local evidence script | `scripts/android-tv-capture-production-readiness.ps1` |
| Factory feedback classifier | `scripts/android-tv-classify-factory-feedback.ps1`; template `docs/ops/templates/android-tv-factory-feedback.template.json` |
| Vendor permission classifier | `scripts/android-tv-classify-vendor-permission.ps1`; template `docs/ops/templates/android-tv-vendor-system-permission.template.json` |
| Production service check script | `scripts/android-tv-check-production-services.ps1` |
| OTA canary report check script | `scripts/android-tv-check-ota-canary-report.ps1` |
| Factory pilot gate check script | `scripts/android-tv-check-factory-pilot-gates.ps1` |
| Factory handoff export script | `scripts/android-tv-export-factory-pilot-handoff.ps1` |

## Readiness Rows

| Gate | Status | Evidence Path | Owner | Blocker | Decision |
| --- | --- | --- | --- | --- | --- |
| Factory fresh install | Pending factory, classifier ready | `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md`; `scripts/android-tv-classify-factory-feedback.ps1` | Factory + OpenClaw | Need fresh-machine report from factory | Do not expand beyond pilot until structured feedback is classified |
| Default Home persistence | Pass on current test unit, pending factory | `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md` | Factory + OpenClaw | Need factory firmware result | Accept APK-only only if fresh unit persists Home |
| Cold boot | Pass on current test unit, pending factory | `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md` | Factory + OpenClaw | Need factory cold-boot evidence | Keep as factory checklist item |
| Restore factory behavior | Unknown | `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md` | Factory | Need answer: APK preserved, removed, or reinstalled | If removed, require factory provisioning or system image preinstall |
| OTA one-device canary | Server ready, scheduled report monitor active, device report pending | `docs/ops/2026-06-30-android-tv-0.1.15-ota-candidate.md`; `scripts/android-tv-check-ota-canary-report.ps1`; Codex automation `openclaw-tv-ota-canary-report` | OpenClaw | Target device has not reported install lifecycle yet | Keep rollout at one-device scope |
| OTA expanded rollout | Not started | `docs/ops/2026-06-30-android-tv-0.1.15-ota-candidate.md` | OpenClaw | Depends on one-device canary installed or clear recoverable failure | No broader rollout yet |
| Payment renewal | Operator-visible, production price pending | `home` commit `620808b`; `GET /api/admin/model-renewal-payment-orders` deployed and auth-protected | OpenClaw | Need production package duration and price decision; latest real payment smoke evidence should be attached before volume shipment | Keep 0.01 yuan smoke package until pricing locks |
| Ad publish and render | Operator-visible, device screenshot pending | `home` commit `620808b`; public admin shows slot, creative URL, preview, publish state, target project, and updated time | OpenClaw | Need real TV screenshot after latest ad asset | Require visual acceptance before volume shipment |
| iPhone casting | Product-accepted through Lebo fallback, final evidence pending | Casting acceptance notes/SOP | OpenClaw + Factory | Need iPhone model, OS, Wi-Fi SSID, connect/audio/return-Home evidence | Keep Lebo fallback for production pilot |
| Xiaomi casting | Product-accepted through Lebo fallback, final evidence pending | Casting acceptance notes/SOP | OpenClaw + Factory | Need Xiaomi model, OS, Wi-Fi SSID, connect/audio/return-Home evidence | Keep Lebo fallback for production pilot |
| Low-memory soak | Partial device checks done, long soak pending | Runtime memory notes/SOP | OpenClaw | Need before/during/after PSS around cast and app return | Keep background cleanup on Home return |
| No-ADB support evidence | Requirement defined, vendor classifier ready, vendor path pending | `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md`; `scripts/android-tv-classify-vendor-permission.ps1`; `docs/ops/templates/android-tv-vendor-system-permission.template.json`; `scripts/android-tv-capture-production-readiness.ps1` for ADB-equivalent local capture | Factory | Need no-ADB log export or support path from factory/vendor | Required before volume shipment |
| Server health and cert renewal | Pass with scheduled monitor | `scripts/android-tv-check-production-services.ps1`; Codex automation `openclaw-tv-production-service-gate`; `https://oc.goods-editor.com/api/health` | OpenClaw | None for current pilot; certificate still expires on 2026-08-13 and must renew before expiry | Run the scripted production service check before every factory or OTA release and keep the 12-hour monitor active |
| Rollback drill | Mechanism covered by home test, production drill pending | `home` commit `4014d1e`; `home/apps/platform-api/test/openclaw-content-control.test.ts` | OpenClaw | Need live higher versionCode recovery package exercise after one-device canary has a device report | Rollback means pause bad release and publish higher versionCode recovery APK |

## Current Production Gate

Production is not yet open for broad rollout.

The next gate is:

1. Factory installs `OpenClawTV-0.1.14.apk` on a fresh unit and returns the required checklist evidence.
2. The targeted test unit checks into `home`, receives `0.1.15`, installs through OTA, and reports `verified` or `installed`.
3. The operator confirms OTA release/report state in the `home` public admin without manual database or raw JSON edits.

## 2026-06-30 Source Publication Update

Published Android TV source traceability to GitHub:

```text
branch: origin/codex/tv-platform-contract -> 71d9867 docs: record production service monitor
tag: android-tv-0.1.14-factory -> 5de26b8 feat: add summer assistant sprite set
```

Later production-readiness documentation and check scripts have also been pushed on the same branch. Use `git ls-remote --heads origin codex/tv-platform-contract` as the authoritative current branch head.

Verification before publish:

```text
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest --console=plain -> BUILD SUCCESSFUL
scripts\android-tv-check-production-services.ps1 -> PASS
OpenClawTV-0.1.14.apk SHA-256 -> 6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce
OpenClawTV-0.1.15.apk SHA-256 -> 9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86
```

## 2026-06-30 OTA Canary Report Check

Added a read-only admin OTA report check:

```powershell
scripts\android-tv-check-ota-canary-report.ps1
```

It checks the target one-device OTA release and target device report state from `home` admin OTA snapshot. It accepts `verified`, `installed`, or `reported` as canary-closing statuses, writes a sanitized `target-ota-report.json`, and never prints admin tokens.

Auth behavior:

```text
Reads CONTROL_PLANE_ADMIN_SESSION or CONTROL_PLANE_ADMIN_TOKEN from the local environment.
Without admin auth, the script returns AUTH_REQUIRED and does not call the admin endpoint.
```

Current local limitation:

```text
No admin session/token is present in the local environment. The canary script now falls back to the configured home SSH host and reads the live admin snapshot from the server environment without printing admin secrets.
```

Scheduled monitoring:

```text
id: openclaw-tv-ota-canary-report
schedule: every 1 hour
workspace: C:\Users\soulzyn\Desktop\openclaw-android-tv
command: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-ota-canary-report.ps1 -AllowMissingAdminAuth -AllowPending
```

The monitor reports PASS/PENDING/AUTH_REQUIRED/FAIL. PENDING is expected while the server can see the target release but the target device has not reported yet, and the monitor command exits successfully for that expected state. It can close the canary only when `home` admin OTA snapshot shows the target device has reported `verified`, `installed`, or `reported` for the expected release.

Server-side admin snapshot check:

```text
8 server has CONTROL_PLANE_ADMIN_TOKEN available to the platform API environment.
Target release found: ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33
Target release versionCode: 2026070101
Target release rolloutStatus: rolling
Target release targetScope: deviceUuid:6741af4b-02b9-4692-99f3-5b4380fbbc3e
Target release installPolicy: vendor_silent
Matching target reports: 0
Latest target report: null
```

This proves the one-device OTA release is visible in the live admin snapshot. It also proves the target device has not yet reported the OTA lifecycle, so the canary remains pending.

## 2026-06-30 Home Operator Update

`home` deployment `620808b` added:

- Ad creative operator status: target project, creative URL, preview image, time window, sort order, and computed publish state.
- Payment order operator status: account/device, SKU/title, amount, payment state, renewal plan/duration, provider order id, transaction id, and updated time.
- Model lease detail: provider, model, lease mode, last renewed, last used, provider key id, and expiry.

`home` deployment `bd61b95` added:

- OTA release list controls for start/resume, pause, and mark rolled_back.
- UI reminder that recovery still requires a higher `versionCode` package with a narrow `targetScope`.
- API regression coverage that an OpenClaw TV OTA release can be paused and then marked `rolled_back` before publishing a higher `versionCode` recovery package.

`home` deployment `f78944f` added:

- The main project dashboard links Known devices to `/projects/openclaw-android-tv/devices`.
- The device page already joins registered devices, latest telemetry, active sessions, and OTA reports so operators can inspect installed TVs without manual database queries.
- This closes the operator navigation gap between the project dashboard and device/OTA observability page.

Verification:

```text
npm run platform-api:test -> 72/72 pass
npm run platform-api:build -> pass
npm run build -> pass
https://oc.goods-editor.com/api/health -> ok
GET /api/admin/model-renewal-payment-orders without admin auth -> 401 ADMIN_TOKEN_REQUIRED
OTA bootstrap target device -> ota.available=true
OTA bootstrap non-target device -> ota.available=false
/srv/home/repo -> f78944f
server backup -> /srv/backups/home/20260630T101947
systemctl is-active home-platform-api home-public-admin lease-core fleet-core -> active
npm run runtime-stack:smoke -- --mode tv --platform-api-base-url http://127.0.0.1:3210 -> ok
scripts\android-tv-check-production-services.ps1 -> PASS
GET /projects/openclaw-android-tv/devices on home-public-admin -> 200
```

2026-06-30 next-stage operator verification:

```text
next-stage plan -> docs/plans/2026-06-30-next-stage-production-development-plan.md
home npm run platform-api:test -> 72/72 pass
home npm run platform-api:build -> pass
home npm run build -> pass
```

## 2026-06-30 Rollback Drill Mechanism

Added `home` test coverage for the rollback recovery path:

```text
commit: 4014d1e test: cover openclaw ota rollback recovery
test: home/apps/platform-api/test/openclaw-content-control.test.ts
```

Verified mechanism:

```text
1. A bad OpenClaw TV OTA release can be paused through /api/admin/ota/releases/status.
2. A recovery APK release must use a higher versionCode.
3. The target device receives the recovery release through /api/ota/bootstrap.
4. A non-target device does not receive the narrow recovery release.
```

Verification:

```text
npm run platform-api:test -> 72/72 pass
npm run platform-api:build -> pass
```

This closes the code-level rollback mechanism check. It does not close the production rollback drill because no live target device has reported OTA install state yet.

## 2026-06-30 Production Service Check

Added a repeatable release-gate check:

```powershell
scripts\android-tv-check-production-services.ps1
```

Current production result:

```text
[PASS] home health: 200 ok
[PASS] home certificate: host=oc.goods-editor.com; expires=2026-08-13T19:25:54.0000000+08:00; daysLeft=44
[PASS] ad asset certificate: host=gm.goods-editor.com; expires=2026-08-11T08:57:06.0000000+08:00; daysLeft=41
[PASS] ota artifact head: status=200; contentLength=12119959; contentType=application/vnd.android.package-archive
[PASS] ota artifact sha: 9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86  OpenClawTV-0.1.15.apk
[PASS] ad asset health: gm-ad-assets-ok
[PASS] ota target scope: available=True; release=ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33
[PASS] ota non-target scope: available=False
Summary: status=PASS
checkedAt=2026-06-30T03:23:18.0638875Z
outputDir=C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\service-checks\production-services-20260630-112312
failedCount=0
```

The production service check now also writes `certificates.json` with host, expiry, days left, warning threshold, and status for both `oc.goods-editor.com` and `gm.goods-editor.com`.

This closes the manual release-gate check for current server health, `oc.goods-editor.com` and `gm.goods-editor.com` certificate validity, OTA artifact availability, OTA SHA matching, ad asset availability, and one-device OTA targeting. Continuous monitoring is covered by the scheduled production service monitor below. This does not close the device-installed OTA report.

## 2026-06-30 Production Service Monitor

Added scheduled monitoring in Codex automation:

```text
id: openclaw-tv-production-service-gate
schedule: every 12 hours
workspace: C:\Users\soulzyn\Desktop\openclaw-android-tv
command: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-tv-check-production-services.ps1
```

The monitor reports PASS/FAIL, failed check names, certificate days remaining for both the home API and ad asset hosts, OTA artifact status, ad asset health, and target/non-target OTA scope. It is read-only and does not modify files, install APKs, or contact devices.

This closes the production-service monitoring gap for the current factory pilot. It does not close the device-installed OTA report or the factory fresh-machine feedback.

## 2026-06-30 Factory/System Integration Update

Added the factory permission decision checklist to `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md`.

Decisions now use fixed categories:

```text
APK-only acceptable
factory provisioning required
system image preinstall required
vendor API required
blocked
```

Added the factory fresh-device feedback template to `docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md`.

Added structured factory feedback intake:

```text
docs/ops/templates/android-tv-factory-feedback.template.json
scripts/android-tv-classify-factory-feedback.ps1
```

The classifier converts factory feedback into `PASS A`, `PASS B`, `BLOCKED A`, `BLOCKED B`, `BLOCKED C`, or `INCOMPLETE`, plus missing fields and required factory action. It does not close the factory gate until real factory feedback is provided.

Added local evidence capture:

```powershell
scripts\android-tv-capture-production-readiness.ps1
```

Current limitation:

```text
ADB device is not online in the current local environment, so fresh-device capture and OTA installed report remain pending external device/factory evidence.
```

## 2026-06-30 Vendor/System Permission Classifier

Added structured vendor permission intake:

```text
docs/ops/templates/android-tv-vendor-system-permission.template.json
scripts/android-tv-classify-vendor-permission.ps1
```

The classifier converts vendor or factory system-permission answers into:

```text
APK-only acceptable
factory provisioning required
system image preinstall required
vendor API required
blocked
INCOMPLETE
```

It checks APK-only viability, default Home persistence, cold boot, restore-factory handling, OpenClaw priv-app or firmware default Home support, install/boot permission whitelists, Lebo or vendor casting retention, no-ADB log export, system OTA path, factory provisioning, and vendor API availability.

Current limitation:

```text
No real vendor permission feedback has been provided yet, so this closes the classification tooling gap but does not close the vendor/system integration gate.
```

## 2026-06-30 Factory Pilot Gate Check

Added a single local gate command:

```powershell
scripts\android-tv-check-factory-pilot-gates.ps1
```

It writes:

```text
factory-pilot-gates.json
summary.txt
```

The gate checks:

```text
factory APK hash
OTA APK hash and size
factory handoff export freshness and evidence completeness
production service health and OTA targeting
target device OTA installed report
ADB online device visibility
home deployment commit and service activity
factory fresh feedback classification, when a feedback JSON path is provided
vendor permission decision classification, when a feedback JSON path is provided
```

When local admin auth is missing, the OTA canary script uses the configured `HomeSshHost` to query the live platform API from the server environment. That remote fallback writes sanitized OTA evidence under the canary output directory, and does not print admin tokens. The factory gate still has a legacy secondary remote fallback that can write `remote-ota-canary-report.json` if the child canary returns `AUTH_REQUIRED`.

The OTA canary and factory pilot gate scripts now catch SSH reset, DNS, curl, and child-script failures and convert them into structured FAIL evidence with `summary.txt` output instead of leaving a partial gate directory without a summary.

Latest local result:

```text
factory APK hash -> PASS
OTA APK hash and size -> PASS
factory handoff export -> PASS; latest handoff sourceHead matches current Git head and includes production-service/factory-gate evidence
production services -> PASS
OTA installed report -> PENDING; remote=release found, but target device has not reported OTA lifecycle yet
ADB online device -> PENDING; no online adb device
home deployment -> PASS; home=f78944f services=active
factory fresh feedback -> PENDING; no factory feedback path provided
vendor permission decision -> PENDING; no vendor permission feedback path provided
overall -> PENDING
```

Current limitation:

```text
The gate is expected to return PENDING until factory fresh feedback, vendor permission feedback, target-device OTA report, and local/remote device evidence are provided.
```

## 2026-06-30 OTA Report Observability Hardening

Added source-level hardening for future APK builds:

```text
When a verified own-APK update is submitted to the system silent installer, the client now reports status=installing to home.
The report is best-effort and does not block the local install state if the network report fails.
The existing installed report still occurs after the app starts on the target version and the persisted update record is reconciled.
```

Scope boundary:

```text
This is a source change for the next APK build. It does not modify the already published OpenClawTV-0.1.15.apk artifact or its SHA-256.
The current one-device OTA canary remains pending until the target device reports a lifecycle status for release ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33.
```

Verification:

```text
.\gradlew.bat :app:testDebugUnitTest --tests "com.openclaw.tv.upgrade.*" --console=plain -> BUILD SUCCESSFUL
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest --console=plain -> BUILD SUCCESSFUL
```

## 2026-06-30 Factory Handoff Export

Added a repeatable factory handoff exporter:

```powershell
scripts\android-tv-export-factory-pilot-handoff.ps1
```

It verifies the current factory APK and OTA APK hashes, then creates a non-secret handoff folder under:

```text
artifacts\factory-pilot-handoff\handoff-<timestamp>
```

The folder contains:

```text
apk\OpenClawTV-0.1.14.apk
feedback\android-tv-factory-feedback.json
feedback\android-tv-vendor-system-permission.json
docs\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md
docs\2026-06-30-android-tv-production-readiness.md
docs\2026-06-30-next-stage-production-development-plan.md
evidence\production-services
evidence\factory-pilot-gate
handoff-manifest.json
README-factory-pilot.md
summary.txt
```

The exporter now records the current Git branch/head in `handoff-manifest.json`, copies the latest successful production service evidence, and copies the latest PASS/PENDING factory pilot gate evidence. Production service evidence includes `certificates.json` for the home API and ad asset host certificates.

Latest local export:

```text
outputDir=C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\factory-pilot-handoff\handoff-20260630-104926
factoryApkCopied=True
factoryApkSha256=6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce
otaApkSha256=9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86
```

Verification:

```text
Factory feedback template through classifier -> INCOMPLETE as expected for unfilled factory fields.
Vendor permission template through classifier -> INCOMPLETE as expected for unknown vendor answers.
```

## Required Evidence Format

When a gate changes state, append a dated note under this file with:

```text
Date:
Device model:
Firmware:
APK version:
Home result:
OTA release/report:
Casting source:
Memory snapshot:
Screenshot/video path:
Decision:
```
