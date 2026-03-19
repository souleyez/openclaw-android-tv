# OpenClaw Android TV Closed Beta Release Checklist

Date: 2026-03-19
Stage: Internal alpha -> closed beta
Goal: Ship a limited, invite-only Android TV test build to selected devices and users

## Release Recommendation

Current recommendation:
- Suitable for internal technical alpha
- Suitable for invite-only closed beta after P0 is complete
- Not suitable yet for public beta or production payments

## Scope Recommendation

Closed beta should explicitly limit the supported feature set to:
- Voice interaction on selected Android TV / Android box devices
- Local control for `YouTube`, `VLC`, `Spotify`, `Settings`
- Local conversation with model fallback
- Stablecoin top-up in test mode only
- Shared device scope testing for `household` and `api_key`

Do not position closed beta as supporting:
- Public payments
- Netflix-grade reliable control
- Full local player compatibility
- OTA operations console
- Unrestricted background hotword support on all devices

## P0 Must Complete Before Closed Beta

### Product and release gating

- Define the exact closed beta feature promise and unsupported items
- Freeze the supported device list for the first beta batch
- Freeze the supported app list for the first beta batch
- Add an in-app `beta / test only` disclosure for billing and model usage
- Add a privacy notice for microphone, logs, and payment testing

### Account and identity

- Replace the fixed demo user returned by `/me` with a real minimal account model
- Add invite code or tester whitelist login
- Separate user data, devices, orders, and logs by real account ID
- Ensure one tester cannot see another tester's data

### Billing safety

- Keep stablecoin flows in explicit test mode
- Prevent real settlement from being treated as production credit without review
- Add order state guardrails for `pending`, `confirming`, `reviewing`, `failed`, `expired`, `confirmed`
- Add admin-side visibility for latest orders and settlement state
- Add manual override and rollback path for mistaken credits

### Backend reliability

- Move core environment configuration out of demo defaults
- Verify MiniMax configuration per environment
- Add structured error responses for model router, device control authorization, and billing
- Add request logging and failure tracing
- Add at least one backup/export strategy for SQLite data

### Client reliability

- Replace remaining hardcoded demo assumptions in the home flow
- Ensure the app behaves correctly when backend is offline or slow
- Add visible retry / fallback messaging for voice, router, and control failures
- Add startup handling for missing mic permission, missing speech service, and missing target apps
- Test the standby service toggle and notification behavior on target devices

### Device and app validation

- Run regression on the first supported device matrix
- Validate real installs of `YouTube`, `VLC`, `Spotify`, `Settings`
- Record what actions actually work on each target device and ROM
- Mark unsupported actions clearly in the tester guide

### Release operations

- Define beta build naming and versioning
- Produce signed test APK or AAB
- Add a tester onboarding guide
- Add a rollback plan for broken builds

## P1 Strongly Recommended For Closed Beta

### Admin and operations

- Build a minimal admin page or operator panel for:
- Device list
- Order list
- Avatar profile list
- Recent router logs
- Shared binding list

- Add simple filters by user, device, and order status
- Add export of recent logs for support review

### Observability

- Add crash reporting
- Add client event logging for:
- wake to voice start
- voice recognized
- router response received
- local control executed
- local control failed
- payment state changed

- Add basic latency metrics for:
- speech recognition
- router intent resolution
- local control execution

### UX hardening

- Improve blocked-action copy per access scope
- Add explicit unsupported-app messages
- Add loading and timeout messaging for long model responses
- Add first-run setup hints for permissions and speech availability

## P2 Can Be Deferred Until After Closed Beta Starts

- Full OTA management console
- Production stablecoin reconciliation pipeline
- Credit card support
- Full financial back-office
- Real system hotword integration on privileged devices
- Remote avatar asset delivery
- Expanded control depth for `Netflix` and fragmented local players
- Multi-provider token pool optimization and marketplace-style API allocation

## Current Project Assessment

### Already in place

- Flutter Android TV client shell
- Native speech recognition bridge
- Native local control bridge
- Model router integration path
- SQLite persistence
- Stablecoin order flow prototype
- Shared device scope prototype
- Avatar configuration management prototype
- Foreground standby service skeleton

### Still demo or prototype grade

- Real auth and user isolation
- Payment safety and operator review flow
- Admin console
- Broader device validation
- Production logging and crash monitoring
- Release packaging discipline

## Minimum Test Matrix For Closed Beta

Recommended first batch:
- 1 Google TV device
- 1 Android TV box on Android 9
- 1 Android TV box on Android 11 or newer
- 1 low-end or no-GMS custom ROM device

For each device validate:
- install and launch
- mic permission flow
- speech recognition availability
- `YouTube` open and search
- `VLC` open and playback controls
- `Spotify` open and playback controls
- `Settings` open
- standby toggle
- backend offline fallback

## Exit Criteria For Closed Beta Launch

Closed beta can launch when all of the following are true:
- P0 items are complete
- Signed test build is generated successfully
- At least 3 target devices pass the minimum matrix
- Real tester accounts work without data leakage
- Billing is clearly marked as test mode and cannot silently over-credit
- Support staff can inspect logs, devices, and recent orders

## Recommended Next Execution Order

1. Implement real minimal auth and account isolation
2. Build the smallest admin/operator panel
3. Harden billing safety and review flow
4. Run target device regression
5. Prepare signed closed beta package and tester guide

