# OpenClaw Closed Beta Device Test Runbook

Date: 2026-03-19
Audience: QA, operator, product, engineering
Goal: Validate the first closed beta build on a small set of real devices

## Test Goal

This runbook is for the first invite-only beta build. It is not a generic compatibility study.

The goal is to answer:
- Can the build install and launch on the chosen devices?
- Can users complete the main voice-to-control loop?
- Can the team observe billing, device identity, and recovery behavior clearly?
- Which actions are reliable enough to promise during closed beta?

## Closed Beta Promise

Only test and promise the following:
- Voice interaction on selected Android TV and Android box devices
- Local control for `YouTube`, `VLC`, `Spotify`, `Settings`
- Local conversation with model routing
- Stablecoin billing in test mode
- `device_user_id` identity and entitlement behavior
- Foreground standby and visible command progress

Do not promise:
- Public billing
- Netflix reliability
- Full local player compatibility
- Full system hotword support on every device

## Required Test Devices

Prepare at least:
- 1 Google TV device
- 1 Android 9 TV box
- 1 Android 11+ TV box
- 1 no-GMS or custom ROM TV box

Optional but useful:
- 1 projector device
- 1 all-in-one Android TV

## Build Under Test

- Client APK: [app-debug.apk](C:\Users\soulzyn\Desktop\codex\apps\android-tv-client\build\app\outputs\flutter-apk\app-debug.apk)
- Backend:
  - `npm run build` must pass
  - MiniMax key configured when model tests are needed

## Pre-Test Setup

Before each device run:
- Clear old app data if previous builds were installed
- Confirm the target apps are installed:
- `YouTube`
- `VLC`
- `Spotify`
- `Settings`

- Confirm network connectivity
- Confirm microphone permission can be granted
- Confirm backend base URL points to the active backend host

## Core Test Cases

### A. Install and startup

- Install APK successfully
- Launch app successfully
- Home screen renders without overflow or crash
- Remote focus navigation works
- Virtual host and large subtitle area render correctly

Pass criteria:
- No startup crash
- Home UI usable within 5 seconds

### B. Device identity

- Confirm the build can operate with the shipped `device_user_id`
- Verify `/api/me` returns a non-demo device user identity path
- Verify device registration succeeds
- Verify active device count updates correctly

Pass criteria:
- Device appears under the correct `device_user_id`
- No cross-device data leak between test identities

### C. Voice and conversation

- Trigger `Press To Talk`
- Grant microphone permission if prompted
- Speak a simple command in English
- Speak a normal non-control query
- Verify the app shows:
- listening
- received text
- understanding / pending state
- final execution or chat reply

Pass criteria:
- User always gets visible feedback even if model response is slow
- No silent failure after recognition

### D. App control

Run each on every priority device:
- `Open YouTube`
- `Search YouTube for lo-fi`
- `Open VLC`
- `Pause VLC`
- `Open Spotify`
- `Next on Spotify`
- `Open Settings`
- `Go back`

Record for each:
- worked
- partially worked
- failed
- device / ROM notes

Pass criteria:
- L1 open actions >= 95 percent on target devices
- main L2 playback actions >= 85 percent on target devices

### E. Billing in test mode

- Create a top-up order
- Verify beta/test billing disclosure is visible
- Copy deposit address
- Submit a tx hash
- Refresh confirmations
- Verify payment panel updates correctly
- Verify latest order status updates on home screen

Pass criteria:
- Billing is clearly labeled test-only
- No misleading production wording
- Status transitions are visible and understandable

### F. Recovery and entitlement transfer

- Open recovery portal on a separate browser using `/api/recovery`
- Use a known `txHash` to recover a device user
- Submit an entitlement transfer from one test device user to another
- Verify the transfer is visible in the admin console

Pass criteria:
- Recovery lookup works with known payment proof
- Transfer writes a visible audit trail
- Source entitlement is no longer treated as active ownership

### G. Admin console

- Open `/api/admin`
- Verify device users render
- Verify API pool records render
- Add one manual API pool account
- Update one device user entitlement expiry date
- Verify data persists after refresh

Pass criteria:
- Operators can inspect the minimum closed beta data without touching SQLite directly

### H. Standby behavior

- Enable background standby
- Verify notification / armed state behavior on the device
- Simulate hotword path if needed
- Confirm visible feedback appears when a wake event is triggered

Pass criteria:
- Standby toggle does not crash
- Wake or simulated wake produces clear visible progress

## Logging Template Per Device

Record the following for every tested device:
- Brand
- Model
- Android version
- GMS yes or no
- Installed app versions
- Passed cases
- Failed cases
- Partial cases
- Notes for ROM quirks
- Final verdict: pass / conditional pass / fail

## Closed Beta Exit Rule

Ship the first closed beta only if:
- At least 3 target devices pass the core loop
- Voice feedback is visible and understandable on all pass devices
- Billing is clearly marked as test mode
- Device user identity and transfer flow are demonstrable
- Admin console can show users, orders, transfers, and API pool state

## Recommended Owner Split

- Engineering:
- backend readiness
- client build
- recovery flow
- admin console

- QA:
- device matrix execution
- action success tracking
- UI and focus regression

- Operator:
- API pool account maintenance
- payment proof review
- transfer verification

