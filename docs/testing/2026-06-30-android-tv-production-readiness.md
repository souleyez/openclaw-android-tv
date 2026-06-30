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
| Home operator deployment | `f78944f feat: link device detail from dashboard`; `home-public-admin` is verified through server-local `127.0.0.1:3002`, while `oc.goods-editor.com` remains the public API/storage boundary |
| Android TV source branch | `origin/codex/tv-platform-contract`; current head is verified with `git ls-remote --heads origin codex/tv-platform-contract` and recorded in factory handoff manifests at export time |
| Android TV factory source tag | `android-tv-0.1.14-factory` at `5de26b8 feat: add summer assistant sprite set` |
| Next-stage execution plan | `docs/plans/2026-06-30-next-stage-production-development-plan.md` |
| Local evidence script | `scripts/android-tv-capture-production-readiness.ps1` |
| Factory pilot plan audit | `scripts/android-tv-audit-factory-pilot-plan.ps1` |
| Factory feedback classifier | `scripts/android-tv-classify-factory-feedback.ps1`; template `docs/ops/templates/android-tv-factory-feedback.template.json` |
| Vendor permission classifier | `scripts/android-tv-classify-vendor-permission.ps1`; template `docs/ops/templates/android-tv-vendor-system-permission.template.json` |
| Factory feedback intake script | `scripts/android-tv-ingest-factory-pilot-feedback.ps1` |
| Factory return package intake | `scripts/android-tv-ingest-factory-pilot-return-package.ps1` |
| Factory return package intake regression | `scripts/android-tv-test-factory-return-package-intake.ps1` |
| Factory handoff archive verifier | `scripts/android-tv-verify-factory-handoff-archive.ps1` |
| Factory pilot evidence refresh | `scripts/android-tv-refresh-factory-pilot-evidence.ps1` |
| Factory pilot expansion guard | `scripts/android-tv-check-factory-pilot-expansion-readiness.ps1` |
| Production readiness ledger check | `scripts/android-tv-check-production-readiness-ledger.ps1` |
| Production service check script | `scripts/android-tv-check-production-services.ps1` |
| OTA canary report check script | `scripts/android-tv-check-ota-canary-report.ps1` |
| OTA canary report regression | `scripts/android-tv-test-ota-canary-report.ps1` |
| Vendor permission classifier regression | `scripts/android-tv-test-vendor-permission-classifier.ps1` |
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
| OTA expanded rollout | Not started | `docs/ops/2026-06-30-android-tv-0.1.15-ota-candidate.md` | OpenClaw | Depends on one-device canary closing with `verified`, `installed`, or `reported`; `RECOVERABLE_FAILURE` requires recovery evidence first | No broader rollout yet |
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

This proves the one-device OTA release is visible in the live admin snapshot. It also proves the target device has not yet reported the OTA lifecycle, so the canary remains pending. A later `RECOVERABLE_FAILURE` can close single-device diagnosis only when the failure note is clearly recoverable; it must still block expanded rollout.

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
GET /projects/openclaw-android-tv on server-local home-public-admin -> 200
GET /projects/openclaw-android-tv/devices on server-local home-public-admin -> 200
GET https://oc.goods-editor.com/projects/openclaw-android-tv -> 404 expected; public admin is not exposed on the API domain
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
[PASS] api host admin boundary: https://oc.goods-editor.com/login=404; https://oc.goods-editor.com/projects/openclaw-android-tv=404; https://oc.goods-editor.com/projects/openclaw-android-tv/devices=404
[PASS] home certificate: host=oc.goods-editor.com; expires=2026-08-13T19:25:54.0000000+08:00; daysLeft=44
[PASS] ad asset certificate: host=gm.goods-editor.com; expires=2026-08-11T08:57:06.0000000+08:00; daysLeft=41
[PASS] ota artifact head: status=200; contentLength=12119959; contentType=application/vnd.android.package-archive
[PASS] ota artifact sha: 9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86  OpenClawTV-0.1.15.apk
[PASS] ad asset health: gm-ad-assets-ok
[PASS] ota target scope: available=True; release=ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33
[PASS] ota non-target scope: available=False
[PASS] operator ota admin snapshot: status=PENDING; release=ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33; versionCode=2026070101; matchingReports=0; latestReportStatus=; evidence=C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\service-checks\production-services-20260630-145544\operator-ota-snapshot
Summary: status=PASS
checkedAt=2026-06-30T06:56:18.4915800Z
outputDir=C:\Users\soulzyn\Desktop\openclaw-android-tv\artifacts\service-checks\production-services-20260630-145544
failedCount=0
```

The production service check now also writes `certificates.json` with host, expiry, days left, warning threshold, and status for both `oc.goods-editor.com` and `gm.goods-editor.com`. It also writes `api-host-admin-boundary.json` to prove that the API/storage host does not expose the operator admin routes. It writes `operator-ota-snapshot/target-ota-report.json` from the admin OTA snapshot to prove the operator path can see the current one-device OTA release/report state without raw database inspection.

This closes the manual release-gate check for current server health, `oc.goods-editor.com` and `gm.goods-editor.com` certificate validity, API/admin boundary, OTA artifact availability, OTA SHA matching, ad asset availability, one-device OTA targeting, and operator-visible OTA release/report state. Continuous monitoring is covered by the scheduled production service monitor below. This does not close the device-installed OTA report.

The factory pilot gate also verifies the deployed operator pages through the restricted server-local `home-public-admin` service. The `oc.goods-editor.com` public host is intentionally scoped to API/storage routes and should not be treated as the admin UI origin.

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
The factory feedback template intentionally does not prefill real-device result fields with `PASS`; blank install, Home, cold boot, casting, OTA, and evidence fields must stay incomplete or pending until factory evidence is returned.

Added structured factory feedback intake:

```text
docs/ops/templates/android-tv-factory-feedback.template.json
scripts/android-tv-classify-factory-feedback.ps1
scripts/android-tv-ingest-factory-pilot-feedback.ps1
```

The classifier converts factory feedback into `PASS A`, `PASS B`, `BLOCKED A`, `BLOCKED B`, `BLOCKED C`, or `INCOMPLETE`, plus missing fields and required factory action. The intake script copies returned factory/vendor JSON into `artifacts/factory-pilot-intake/intake-*`, runs both classifiers, runs the factory pilot gate, and writes one top-level summary. It does not close the factory gate until real factory feedback is provided.

The returned zip/folder intake also validates package-relative evidence paths in `screenshotOrVideoPath`, `logsPath`, and vendor `evidencePath`. Direct JSON intake performs the same validation when `-EvidenceRoot` is provided. Any referenced file or folder must exist inside the returned package; absolute paths, URLs, and path traversal are rejected. The return-package intake records returned package kind, size, entry count, and zip SHA-256 when available; it rejects unsafe zip entries before extraction, rejects returned folders containing reparse-point entries such as symbolic links or junctions before copying, and rejects packages containing duplicate factory or vendor feedback JSON files, so stale nested copies cannot be selected silently.

Added local evidence capture:

```powershell
scripts\android-tv-capture-production-readiness.ps1
scripts\android-tv-capture-production-readiness.ps1 -AllowNoDevice
```

Current limitation:

```text
ADB device is not online in the current local environment, so fresh-device capture and OTA installed report remain pending external device/factory evidence. The script writes `NO_ADB_DEVICE` evidence and exits successfully only when `-AllowNoDevice` is used.
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
The vendor permission classifier treats `unknown` answers as incomplete input, not as failed vendor capability. A path is marked failed or blocked only after explicit `no` answers close the relevant alternatives.

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
factory APK signing certificate and v1/v2/v3 signature schemes
OTA APK signing certificate and v1/v2/v3 signature schemes
factory handoff export freshness and evidence completeness
production service health and OTA targeting
target device OTA installed report
ADB online device visibility
home deployment commit, service activity, and server-local operator page rendering
factory fresh feedback classification, when a feedback JSON path is provided
vendor permission decision classification, when a feedback JSON path is provided
factory returned zip/folder intake into classifier and gate evidence
production readiness ledger row, field, local evidence-reference, `home` commit, and Codex automation completeness
factory pilot expansion readiness before wider rollout
factory pilot evidence refresh summary linking handoff, gate, and expansion evidence
factory handoff archive verification from the transferred zip and sidecar
factory pilot plan audit against the `Definition Of Next Milestone Done`
```

When local admin auth is missing, the OTA canary script uses the configured `HomeSshHost` to query the live platform API from the server environment. That remote fallback writes sanitized OTA evidence under the canary output directory, and does not print admin tokens. The factory gate still has a legacy secondary remote fallback that can write `remote-ota-canary-report.json` if the child canary returns `AUTH_REQUIRED`.

The OTA canary and factory pilot gate scripts now catch SSH reset, DNS, curl, and child-script failures and convert them into structured FAIL evidence with `summary.txt` output instead of leaving a partial gate directory without a summary.

The factory pilot plan audit now requires the operator OTA snapshot before counting either `One signed 0.1.15 OTA is delivered via home to one test device` or `home operator UI can create/check OTA releases without raw JSON edits` as `PASS`. The snapshot must show the expected release id, one-device target UUID, target versionCode, artifact SHA-256, target scope, and an accepted snapshot status.

Verification:

```text
scripts\android-tv-audit-factory-pilot-plan.ps1 -RefreshRoot artifacts\factory-pilot-refresh\refresh-20260630-161102-723 -AllowIncomplete -> status=INCOMPLETE, failedCount=0, pendingCount=3; OTA delivery and home operator requirements point at operator-ota-snapshot\target-ota-report.json.
scripts\android-tv-audit-factory-pilot-plan.ps1 -RefreshRoot artifacts\factory-pilot-refresh\refresh-20260630-161102-723 -ExpectedOtaReleaseId wrong-release-id -AllowIncomplete -> status=FAIL, failedRequirements include OTA delivery and home operator requirements.
```

Latest local result:

```text
factory APK hash -> PASS
OTA APK hash and size -> PASS
factory APK signature -> PASS; certSha256=2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522; v1=True; v2=True; v3=True; signers=1
OTA APK signature -> PASS; certSha256=2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522; v1=True; v2=True; v3=True; signers=1
factory handoff export -> PASS; latest handoff sourceHead matches current Git head and includes production-service/factory-gate evidence
production services -> PASS
OTA installed report -> PENDING; remote=release found, but target device has not reported OTA lifecycle yet
ADB online device -> PENDING; no online adb device
home deployment -> PASS; home=f78944f services=active operatorPages=2/2 via 127.0.0.1:3002
production readiness ledger -> PENDING; all required rows and fields present; local evidenceReferenceCheckCount=14, homeCommitCheckCount=3, automationCheckCount=2 with no missing references; real evidence rows still pending
factory fresh feedback -> PENDING; no factory feedback path provided
vendor permission decision -> PENDING; no vendor permission feedback path provided
overall -> PENDING
```

Expansion readiness must remain `BLOCKED` while the gate status is `PENDING`; it can return `PASS` only after the factory pilot gate has no failed or pending rows.

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
feedback\return-package-checklist.json
feedback\README-return-package.md
docs\2026-06-24-android-tv-0.1.14-factory-shipment-sop.md
docs\2026-06-30-android-tv-production-readiness.md
docs\2026-06-30-next-stage-production-development-plan.md
evidence\production-services
evidence\factory-pilot-gate
evidence\factory-return\README-evidence.txt
evidence\factory-return\screenshots\README-screenshots.txt
evidence\factory-return\logs\README-logs.txt
handoff-files.sha256.txt
handoff-manifest.json
README-factory-pilot.md
summary.txt
```

The exporter now records the current Git branch/head in `handoff-manifest.json`, writes `handoff-files.sha256.txt` for package-file integrity, generates `feedback/README-return-package.md` for factory return packaging, copies the latest successful production service evidence, and copies the latest PASS/PENDING factory pilot gate evidence. Production service evidence includes `certificates.json` for the home API and ad asset host certificates, plus `operator-ota-snapshot/target-ota-report.json` for the one-device OTA operator view. Factory pilot gate evidence must include `factory-apk-signature.txt` and `ota-apk-signature.txt` before the archive verifier accepts the package.

The exporter also creates a sibling `.zip` archive and `.sha256.txt` sidecar by default. The archive is the transfer package for factory or partner handoff; the APK inside remains the only APK to install. The factory pilot gate checks the latest handoff archive, sidecar, file hash manifest, and required archive entries before accepting the handoff export as PASS.

The archive verifier, factory pilot gate, and return-package intake reject unsafe zip entry names, including absolute paths, Windows drive paths, empty entry names, and `..` traversal segments. Return-package intake also rejects directory packages containing reparse-point entries before copying.

The exporter records both the local source HEAD and the `origin/<branch>` HEAD in `handoff-manifest.json`. Archive verification and the factory pilot gate require the remote branch HEAD to match the packaged source HEAD, so a factory handoff cannot silently reference unpublished local source.

The archive verifier also computes the SHA-256 of `apk/OpenClawTV-0.1.14.apk` directly from the zip entry and requires it to equal `6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce`.

The archive verifier and factory pilot gate also validate the operator OTA snapshot contents. The snapshot must be for `ota_openclaw-android-tv_2026070101_1782780116232_67ce5c33`, target `deviceUuid:6741af4b-02b9-4692-99f3-5b4380fbbc3e`, versionCode `2026070101`, and OTA artifact SHA-256 `9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86`; accepted snapshot statuses are `PASS`, `PENDING`, and `RECOVERABLE_FAILURE`.

Export summaries record these release-critical fields:

```text
outputDir=<generated handoff directory>
factoryApkCopied=True
factoryApkSha256=6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce
otaApkSha256=9b007e2c90dde18d8f63e4a5f7415aef97a3cd377c00f2f355854ef833feab86
archivePlanned=True
archivePath=<generated handoff zip>
archiveSha256SidecarPath=<generated handoff zip>.sha256.txt
```

Archive verification now requires the fixed handoff entries, including `feedback/return-package-checklist.json` and both APK signature evidence files under `evidence/factory-pilot-gate/`.

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

## 2026-06-30 Local ADB Evidence Check

Current local check:

```text
adb devices -l -> no online devices
scripts\android-tv-capture-production-readiness.ps1 -AllowNoDevice -> status=NO_ADB_DEVICE
evidence: artifacts\device-tests\production-readiness-20260630-185618
summary: artifacts\device-tests\production-readiness-20260630-185618\summary.txt
capture JSON: artifacts\device-tests\production-readiness-20260630-185618\production-readiness-capture.json
```

Decision:

```text
The capture script now writes machine-readable production-readiness-capture.json for both NO_ADB_DEVICE and CAPTURED paths. The current local output records the no-device boundary only. It does not close fresh-device install, casting, memory, or OTA installed-report gates. Those still require a connected test unit or factory-returned evidence.
```

## 2026-06-30 Return Package Zip Preflight Hardening

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-ingest-factory-pilot-return-package.ps1
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-handoff\handoff-20260630-154958.zip -AllowPending -> status=PENDING, returnPackageEntryCount=45, unsafeReturnPackageEntries=, failedCount=0, pendingCount=5
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\unsafe-entry-smoke-source\factory-return-unsafe-entry.zip -AllowPending -> status=FAIL, unsafeReturnPackageEntries=../return-escape.txt, intakeStatus=NOT_RUN
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-handoff\handoff-20260630-160101.zip -AllowPending -> status=PENDING, returnPackageKind=zip, returnPackageSha256=7815d6827fcaad3387a773dbcd2afbb15d81ac4a9bead7adabc462c2e8093bc4, returnPackageSizeBytes=11117976, returnPackageEntryCount=45
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\unsafe-entry-smoke-source\factory-return-unsafe-entry.zip -AllowPending -> status=FAIL, returnPackageKind=zip, returnPackageSha256=f79c8fd01c855a9e65a3adaaac61e799e6224c540bf579c1f4fd3cf5a4651dba, returnPackageSizeBytes=11117859, unsafeReturnPackageEntries=../return-escape.txt, intakeStatus=NOT_RUN
```

Decision:

```text
Return-package intake records returned zip identity and rejects unsafe zip entries before extraction. This hardens factory-returned package intake but does not close the factory fresh install, vendor permission, target OTA report, or runtime evidence gates.
```

## 2026-06-30 Readiness Ledger Evidence Reference Hardening

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-check-production-readiness-ledger.ps1
scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending -> status=PENDING, rowIssueCount=0, pendingRowCount=13, evidenceReferenceCheckCount=14, homeCommitCheckCount=3, automationCheckCount=2
scripts\android-tv-check-production-readiness-ledger.ps1 -HomeRepoRoot artifacts\missing-home-root -AllowPending -> status=FAIL, rowIssueCount=4
scripts\android-tv-check-production-readiness-ledger.ps1 -CodexAutomationRoot artifacts\readiness-ledger-checks\empty-automation-root -AllowPending -> status=FAIL, rowIssueCount=2
```

Decision:

```text
The readiness ledger now verifies local evidence paths, referenced home commits, and referenced Codex automations. This strengthens the production gate evidence check but does not close rows that still require factory feedback, target OTA lifecycle, device runtime screenshots, or vendor answers.
```

## 2026-06-30 Expansion Guard Gate Completeness Hardening

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-check-factory-pilot-expansion-readiness.ps1
scripts\android-tv-check-factory-pilot-expansion-readiness.ps1 -ExistingGateRoot artifacts\factory-pilot-refresh\refresh-20260630-163005-203\factory-pilot-gate -AllowBlocked -> status=BLOCKED, requiredGateCount=12, requiredGateCheckCount=12, failedGates=, blockingGates includes the five current pending gates
fake gate root with only summary.txt status=PASS and no factory-pilot-gates.json -> status=FAIL, failedGates=factory pilot gate evidence=FAIL
fake gate root with factory-pilot-gates.json missing `vendor permission decision` -> status=FAIL, missingRequiredGates=vendor permission decision
```

Decision:

```text
Expansion guard no longer trusts a PASS summary alone. It requires factory-pilot-gates.json, summary/json status consistency, every required gate to be present, and every required gate to be PASS before expansion can be allowed.
```

## 2026-06-30 Post-Push Factory Evidence Refresh

Current local check:

```text
git push origin codex/tv-platform-contract -> c946af7..8d18e16
git ls-remote --heads origin codex/tv-platform-contract -> 8d18e16857bf0c1f6e0a0fa2cfc89666f244b132
scripts\android-tv-check-production-services.ps1 -> PASS; evidence=artifacts\service-checks\production-services-20260630-164509; matchingReports=0
adb devices -l -> no online devices
scripts\android-tv-check-ota-canary-report.ps1 -AllowMissingAdminAuth -AllowPending -> PENDING; target release found but no target OTA lifecycle report yet
scripts\android-tv-refresh-factory-pilot-evidence.ps1 -> status=BLOCKED; childFailures=; output=artifacts\factory-pilot-refresh\refresh-20260630-164545-078
handoff archive -> artifacts\factory-pilot-handoff\handoff-20260630-164545.zip
handoff archive SHA-256 -> 821c5497a561520e10a68afceba8d4b6b2b6116ad6cf12629ab57da73f06c642
archive verifier -> PASS; unsafeEntries=; sourceRemoteMatchesHead=True; sourceRemoteHeadFull=8d18e16857bf0c1f6e0a0fa2cfc89666f244b132; factoryApkEntrySha256=6e3666128e8b4ac139b387242e22e85786d48b965fe050d53cdf7d51f16e26ce
expansion guard -> BLOCKED; requiredGateCount=12; requiredGateCheckCount=12; missingRequiredGates=; failedGates=; blockingGates=production readiness ledger, ota installed report, adb online device, factory fresh feedback, vendor permission decision
plan audit -> INCOMPLETE; pendingRequirements=Factory fresh-machine feedback is recorded; The device reports installed or a clear recoverable failure; A production readiness ledger exists with pass/fail evidence for required rows
```

Decision:

```text
The latest factory handoff archive is source-traceable to the pushed GitHub branch and contains the latest production-service evidence. It remains a factory-pilot package only; expansion stays blocked until factory fresh feedback, target OTA lifecycle reporting, online device runtime evidence, and vendor permission feedback close.
```

## 2026-06-30 Factory Feedback Logs Evidence Hardening

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-classify-factory-feedback.ps1 and scripts/android-tv-export-factory-pilot-handoff.ps1
scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath docs\ops\templates\android-tv-factory-feedback.template.json -> INCOMPLETE; missingFields includes logsPath and screenshotOrVideoPath
factory feedback fixture with PASS-style fields but blank logsPath -> INCOMPLETE; missingFields=logsPath
factory feedback fixture with logsPath populated -> PASS A classification path
scripts\android-tv-ingest-factory-pilot-return-package.ps1 on a returned folder whose factory logsPath points to a missing file -> FAIL; evidencePathIssueCount=1; issue=factory.logsPath referenced evidence path not found
scripts\android-tv-ingest-factory-pilot-return-package.ps1 on the same returned folder with the logs file present -> PENDING; evidencePathIssueCount=0; factoryConclusion=PASS A; vendorDecision=APK-only acceptable; gateStatus=PENDING
```

Decision:

```text
Factory feedback can no longer classify as complete without a logs package path. Returned packages must include the referenced logs file or folder inside the package, so no-ADB/support diagnostics cannot be skipped while marking factory feedback as PASS-style evidence.
```

## 2026-06-30 Direct Factory Feedback Evidence Root Hardening

Current local check:

```text
PowerShell parser -> parse ok for factory feedback classifier, factory pilot gate, direct intake, expansion guard, refresh, and handoff exporter scripts.
scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <complete-feedback-json> -RequireEvidenceRoot -> INCOMPLETE; evidencePathIssues=EvidenceRoot is required to validate screenshotOrVideoPath and logsPath
scripts\android-tv-classify-factory-feedback.ps1 -FeedbackPath <returned-feedback-json> -EvidenceRoot <returned-package-folder> -RequireEvidenceRoot -> PASS A; evidencePathIssueCount=0
scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <complete-feedback-json> -VendorPermissionPath <vendor-json> -AllowPending -> factory fresh feedback=PENDING; conclusion=INCOMPLETE because no FactoryFeedbackEvidenceRoot was supplied
scripts\android-tv-check-factory-pilot-gates.ps1 -FactoryFeedbackPath <returned-feedback-json> -FactoryFeedbackEvidenceRoot <returned-package-folder> -VendorPermissionPath <vendor-json> -AllowPending -> factory fresh feedback=PASS, while overall gate remains PENDING on OTA/ADB/ledger rows
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <returned-package-folder> -AllowPending -> PENDING; factoryConclusion=PASS A; vendorDecision=APK-only acceptable; evidencePathIssueCount=0; gateStatus=PENDING
```

Decision:

```text
Direct JSON feedback can no longer make the factory fresh feedback gate PASS unless the caller also provides FactoryFeedbackEvidenceRoot. The return-package path continues to provide the evidence root automatically, so real factory return packages are validated by file existence while loose JSON-only summaries remain incomplete.
```

## 2026-06-30 Vendor Permission Evidence Root Hardening

Current local check:

```text
PowerShell parser -> parse ok for vendor permission classifier, factory pilot gate, direct intake, expansion guard, refresh, and handoff exporter scripts.
Before hardening, scripts\android-tv-check-factory-pilot-gates.ps1 accepted a vendor JSON whose evidencePath pointed to a missing file and marked vendor permission decision=PASS.
scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-json> -RequireEvidenceRoot -> INCOMPLETE; evidencePathIssues=EvidenceRoot is required to validate evidencePath
scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <vendor-json> -EvidenceRoot <return-folder> -RequireEvidenceRoot, where evidencePath is missing -> INCOMPLETE; evidencePathIssues=referenced evidence path not found
scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath <returned-vendor-json> -EvidenceRoot <return-folder> -RequireEvidenceRoot -> APK-only acceptable; evidencePathIssueCount=0
scripts\android-tv-check-factory-pilot-gates.ps1 with VendorPermissionPath but no VendorPermissionEvidenceRoot -> vendor permission decision=PENDING; decision=INCOMPLETE
scripts\android-tv-check-factory-pilot-gates.ps1 with VendorPermissionPath and VendorPermissionEvidenceRoot -> vendor permission decision=PASS; decision=APK-only acceptable, while overall gate remains PENDING on OTA/ADB/ledger rows
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath <returned-package-folder> -AllowPending -> PENDING; factoryConclusion=PASS A; vendorDecision=APK-only acceptable; evidencePathIssueCount=0; gateStatus=PENDING
```

Decision:

```text
Vendor permission feedback can no longer make the vendor permission decision gate PASS unless the caller also provides VendorPermissionEvidenceRoot and the vendor evidencePath resolves to a real file or folder inside the returned package.
```

## 2026-06-30 Directory Return Package Reparse-Point Hardening

Current local check:

```text
Before hardening, a directory return package containing `external-junction` with attributes Directory, ReparsePoint was accepted by scripts\android-tv-ingest-factory-pilot-return-package.ps1 and continued to status=PENDING.
Added directory return preflight that records reparse-point entries under unsafeReturnPackageEntries and rejects the package before copying.
PowerShell parser -> parse ok for scripts/android-tv-ingest-factory-pilot-return-package.ps1.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\directory-reparse-smoke-source -OutputRoot artifacts\factory-pilot-return-intake\directory-reparse-smoke-final -AllowPending -> FAIL; unsafeReturnPackageEntries=external-junction; intakeStatus=NOT_RUN.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\logs-required-complete-package -OutputRoot artifacts\factory-pilot-return-intake\directory-safe-return-smoke-final -AllowPending -> PENDING; unsafeReturnPackageEntries=; factoryConclusion=PASS A; vendorDecision=APK-only acceptable; evidencePathIssueCount=0.
```

Decision:

```text
Factory return folders can no longer use symbolic links, junctions, or other reparse-point entries to reference files outside the returned package. This closes the folder-return equivalent of unsafe zip entry rejection.
```

## 2026-06-30 Factory Return Checklist Manifest

Current local change:

```text
Added feedback/return-package-checklist.json to factory handoff export.
The checklist records required feedback JSON files, required evidence folders, factory feedback required fields, vendor permission required fields, rejected zip/folder path rules, and the one-device OTA target.
Updated archive verification to require feedback/return-package-checklist.json, parse it, and validate schema, required feedback files, screenshotOrVideoPath/logsPath/evidencePath fields, release id, target device UUID, and target versionCode.
PowerShell parser -> parse ok for scripts/android-tv-export-factory-pilot-handoff.ps1 and scripts/android-tv-verify-factory-handoff-archive.ps1.
scripts\android-tv-export-factory-pilot-handoff.ps1 -OutputRoot artifacts\factory-pilot-handoff\handoff-return-checklist-smoke -> archiveCreated=True; archiveEntryCount later verified as 49.
scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath artifacts\factory-pilot-handoff\handoff-return-checklist-smoke.zip -> PASS; requiredEntryCount=19; returnChecklistParseOk=True; returnChecklistRequiredFiles=feedback/android-tv-factory-feedback.json,feedback/android-tv-vendor-system-permission.json; hashManifestChecked=48.
```

Decision:

```text
Factory return package requirements are now both human-readable in feedback/README-return-package.md and machine-readable in feedback/return-package-checklist.json before the package is accepted for factory handoff.
```

## 2026-06-30 Return Package Checklist Intake Enforcement

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-ingest-factory-pilot-return-package.ps1.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\logs-required-complete-package -OutputRoot artifacts\factory-pilot-return-intake\missing-checklist-smoke -AllowPending -> FAIL; missing return-package-checklist.json; returnChecklistCandidateCount=0; intakeStatus=NOT_RUN.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\bad-checklist-target-smoke-source-2 -OutputRoot artifacts\factory-pilot-return-intake\bad-checklist-target-smoke-2 -AllowPending -> FAIL; returnChecklistParseOk=True; returnChecklistIssueCount=1; issue=return package checklist OTA release id mismatch; intakeStatus=NOT_RUN.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-handoff\handoff-20260630-175058.zip -OutputRoot artifacts\factory-pilot-return-intake\handoff-checklist-intake-smoke -AllowPending -> PENDING; returnChecklistParseOk=True; returnChecklistIssueCount=0; factoryConclusion=INCOMPLETE; vendorDecision=INCOMPLETE; gateStatus=PENDING.
```

Decision:

```text
Factory returned zip/folder packages must now include exactly one feedback/return-package-checklist.json that matches the current one-device OTA target and required evidence fields before classifier/gate intake runs. This prevents stale or wrong-target returned packages from being treated as current pilot evidence.
```

## 2026-06-30 Return Package Evidence Directory Enforcement

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-export-factory-pilot-handoff.ps1, scripts/android-tv-verify-factory-handoff-archive.ps1, and scripts/android-tv-ingest-factory-pilot-return-package.ps1.
scripts\android-tv-export-factory-pilot-handoff.ps1 -OutputRoot artifacts\factory-pilot-handoff\handoff-return-evidence-dir-smoke -> archiveCreated=True; archiveEntryCount later verified as 52.
scripts\android-tv-verify-factory-handoff-archive.ps1 -ZipPath artifacts\factory-pilot-handoff\handoff-return-evidence-dir-smoke.zip -> PASS; returnChecklistRequiredEvidenceDirectories=evidence/factory-return/,evidence/factory-return/screenshots/,evidence/factory-return/logs/; hashManifestChecked=51.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-handoff\handoff-return-evidence-dir-smoke.zip -OutputRoot artifacts\factory-pilot-return-intake\handoff-evidence-dir-intake-smoke -AllowPending -> PENDING; returnChecklistIssueCount=0; factoryConclusion=INCOMPLETE; vendorDecision=INCOMPLETE.
scripts\android-tv-ingest-factory-pilot-return-package.ps1 -ReturnPath artifacts\factory-pilot-return-intake\missing-evidence-dir-smoke-source -OutputRoot artifacts\factory-pilot-return-intake\missing-evidence-dir-smoke -AllowPending -> FAIL; returnChecklistIssueCount=3; missing evidence/factory-return/, evidence/factory-return/screenshots/, and evidence/factory-return/logs/; intakeStatus=NOT_RUN.
```

Decision:

```text
Factory handoff packages now include real evidence drop folders, and returned packages must preserve those folders before intake runs. This gives factory a stable place to put screenshots, videos, logs, and no-ADB diagnostic files while preventing checklist-only packages with no evidence area from entering classifier/gate intake.
```

## 2026-06-30 Local Build Proof Refresh

Current local check:

```text
Android TV:
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest --console=plain -> BUILD SUCCESSFUL; 164 actionable tasks up-to-date.

home:
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run test -> 72/72 pass.
npm --prefix C:\Users\soulzyn\Desktop\codex\home\apps\platform-api run build -> pass.
npm --prefix C:\Users\soulzyn\Desktop\codex\home run build -> pass; Next.js production build generated 22 static pages.
```

Decision:

```text
The current Android TV branch and home operator/control-plane code still pass their local build and unit-test checks. This refreshes local implementation proof only; it does not close factory fresh feedback, target OTA lifecycle reporting, ADB runtime evidence, or vendor permission feedback.
```

## 2026-06-30 Factory SOP Return Package Sync

Current local change:

```text
docs/ops/2026-06-24-android-tv-0.1.14-factory-shipment-sop.md now mirrors the handoff/export/intake contract for factory return packages.
It names the required feedback JSON files, feedback/return-package-checklist.json, evidence/factory-return/ screenshot and log directories, package-relative evidence paths, rejected unsafe path rules, rejected reparse-point folder rules, and current one-device OTA target matching.
```

Decision:

```text
The factory-facing SOP now matches the machine-enforced handoff verifier and return-package intake rules. This reduces factory return ambiguity, but it still does not close factory fresh feedback, target OTA lifecycle reporting, ADB runtime evidence, or vendor permission feedback.
```

## 2026-06-30 Factory Return Package Intake Regression

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-test-factory-return-package-intake.ps1.
scripts\android-tv-test-factory-return-package-intake.ps1 -> status=PASS; caseCount=4; failureCount=0; output=artifacts\factory-pilot-return-intake-tests\run-20260630-183008-310.
scripts\android-tv-test-factory-return-package-intake.ps1 -SkipLivePositive -> status=PASS; caseCount=3; failureCount=0; output=artifacts\factory-pilot-return-intake-tests\run-20260630-183149-901.
```

Covered cases:

```text
complete-zip -> PENDING with returnPackageKind=zip, non-empty returnPackageSha256, returnChecklistIssueCount=0, evidencePathIssueCount=0, factoryConclusion=PASS A, vendorDecision=APK-only acceptable.
missing-checklist -> FAIL, returnChecklistCandidateCount=0, intakeStatus=NOT_RUN.
missing-evidence-dirs -> FAIL, returnChecklistIssueCount>=3, intakeStatus=NOT_RUN.
missing-log-file -> FAIL, evidencePathIssueCount>=1, intakeStatus=NOT_RUN.
```

Decision:

```text
Factory return package intake now has a repeatable regression check for both accepted package shape and hard-fail boundaries. This strengthens the factory feedback intake gate but does not close real factory fresh feedback, target OTA lifecycle reporting, ADB runtime evidence, or vendor permission feedback.
```

## 2026-06-30 OTA Canary Report Regression

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-check-ota-canary-report.ps1, scripts/android-tv-test-ota-canary-report.ps1, and scripts/android-tv-export-factory-pilot-handoff.ps1.
scripts\android-tv-test-ota-canary-report.ps1 -> status=PASS; caseCount=7; failureCount=0; output=artifacts\ota-canary-report-tests\run-20260630-184102-221.
scripts\android-tv-test-factory-return-package-intake.ps1 -SkipLivePositive -> status=PASS; caseCount=3; failureCount=0; output=artifacts\factory-pilot-return-intake-tests\run-20260630-184102-219.
scripts\android-tv-check-ota-canary-report.ps1 -AllowMissingAdminAuth -AllowPending -> PENDING; remote=release found, but target device has not reported OTA lifecycle yet.
```

Covered cases:

```text
no-report-pending -> PENDING.
accepted-verified -> PASS.
accepted-installed -> PASS.
accepted-reported -> PASS.
recoverable-failure -> RECOVERABLE_FAILURE.
hard-failure -> FAIL.
wrong-release-version -> FAIL.
```

Decision:

```text
OTA canary report checking now has a local admin-snapshot regression path that does not require admin secrets or production writes. This protects the one-device OTA gate status mapping, but it still does not close the real target-device OTA lifecycle report.
The factory handoff exporter now also lists the OTA and factory-return regression commands in handoff-manifest.json and README-factory-pilot.md local verification instructions.
```

## 2026-06-30 Readiness Pending Detail Output

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-check-production-readiness-ledger.ps1 and scripts/android-tv-check-factory-pilot-gates.ps1.
scripts\android-tv-check-production-readiness-ledger.ps1 -AllowPending -> status=PENDING; pendingRowCount=13; pendingGates=Factory fresh install; Default Home persistence; Cold boot; Restore factory behavior; OTA one-device canary; OTA expanded rollout; Payment renewal; Ad publish and render; iPhone casting; Xiaomi casting; Low-memory soak; No-ADB support evidence; Rollback drill; rowIssueCount=0.
scripts\android-tv-check-factory-pilot-gates.ps1 -AllowPending -> status=PENDING; failedCount=0; pendingCount=5; production readiness ledger detail includes pendingGates with the same 13 pending readiness rows.
```

Decision:

```text
Readiness gate output now exposes the pending row names and blockers directly in summary.txt, and the factory pilot gate surfaces pendingGates in its top-level production-readiness row. This improves handoff diagnostics but does not close any real external evidence row.
```

## 2026-06-30 Vendor Permission Classifier Regression

Current local check:

```text
PowerShell parser -> parse ok for scripts/android-tv-test-vendor-permission-classifier.ps1, scripts/android-tv-export-factory-pilot-handoff.ps1, scripts/android-tv-test-ota-canary-report.ps1, and scripts/android-tv-test-factory-return-package-intake.ps1.
scripts\android-tv-test-vendor-permission-classifier.ps1 -> status=PASS; caseCount=7; failureCount=0; output=artifacts\vendor-permission-classifier-tests\run-20260630-190951-531.
scripts\android-tv-test-ota-canary-report.ps1 -> status=PASS; caseCount=7; failureCount=0; output=artifacts\ota-canary-report-tests\run-20260630-190951-556.
scripts\android-tv-test-factory-return-package-intake.ps1 -SkipLivePositive -> status=PASS; caseCount=3; failureCount=0; output=artifacts\factory-pilot-return-intake-tests\run-20260630-190951-573.
```

Covered cases:

```text
apk-only-acceptable -> recommendedDecision=APK-only acceptable.
factory-provisioning-required -> recommendedDecision=factory provisioning required.
system-image-preinstall-required -> recommendedDecision=system image preinstall required.
vendor-api-required -> recommendedDecision=vendor API required.
blocked-no-default-home -> recommendedDecision=blocked.
incomplete-unknown-template -> recommendedDecision=INCOMPLETE and exits 2 with -FailOnIncomplete.
missing-evidence-path -> recommendedDecision=INCOMPLETE with evidencePathIssueCount>=1.
```

Decision:

```text
Vendor permission classification now has a repeatable local regression check for every fixed production decision plus incomplete evidence boundaries. This strengthens the vendor/system integration gate but does not close the real vendor permission feedback gate until factory or vendor returns filled evidence.
```
