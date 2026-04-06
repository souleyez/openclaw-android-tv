# Sonance Backend API

This is the runtime backend workspace for Sonance.

Control-plane migration status:

- `home/` is now the active control-plane workspace for Sonance.
- New work for model leases, OTA/config release control, admin APIs, and project governance should go to `home`.
- The Sonance-local control-plane modules remain here only for local compatibility, migration rollback, and legacy maintenance.
- Do not grow new product-control features in Sonance-local `model-router`, `ota`, or `admin` unless the change is required for compatibility.

Current modules:
- `src/modules/auth`
- `src/modules/user`
- `src/modules/billing`
- `src/modules/wallet`
- `src/modules/device`
- `src/modules/model-router`
- `src/modules/chat`
- `src/modules/ota`
- `src/modules/admin`
- `src/modules/audit`
- `src/modules/risk-control`
- `src/modules/radio`

Active runtime ownership in this repo:

- `src/modules/radio`
- `src/modules/chat`
- `src/modules/platform-integration`
- upload serving under `/uploads/radio`

Frozen legacy compatibility ownership in this repo:

- `src/modules/model-router`
- `src/modules/ota`
- `src/modules/admin`

## Model Pool

Status: frozen compatibility surface. The active control-plane lease implementation now lives in `home`.

The backend already supports a shared provider credential pool with:

- provider accounts
- imported API keys
- temporary leases

Current client model:

- client boots and calls `POST /api/router/bootstrap-session`
- backend issues a temporary provider lease for 3-5 minutes
- client keeps user habits and station-switch logic fully local
- client only comes back for lease renewal, explicit release, or uploads

Relevant endpoints:

- `GET /api/router/status`
- `POST /api/router/bootstrap-session`
- `POST /api/router/provider-lease`
- `POST /api/router/provider-lease/release`

These endpoints are kept here only for compatibility and local fallback. Unified production routing now points `/api/router/**` at `home`.

## Radio API

Status: active runtime surface. New Sonance backend work should be concentrated here.

Current radio endpoints:

- `GET /api/radio/stations`
- `GET /api/radio/stations/:id`
- `GET /api/radio/broadcasts`
- `GET /api/radio/broadcasts/:id`
- `POST /api/radio/broadcasts/upload`
- `POST /api/radio/ai/respond`

Uploaded audio files are served from:

- `/uploads/radio/<filename>`

`POST /api/radio/ai/respond` creates a lightweight AI broadcast record first. MiniMax TTS
then runs in the background queue and updates that record later. Clients can poll
`GET /api/radio/broadcasts/:id` until the item becomes ready.

The station catalog also supports gradual public-station augmentation by client country:

- client requests `GET /api/radio/stations` with `x-client-country`
- backend returns the current local catalog immediately
- if the country has too few stations, backend only queues a small background import task
- imported stations are kept in the local database and reused by later users in that country

Optional env:

- `RADIO_IMPORT_COUNTRY_WHITELIST=CN,JP,US,GB`
- `RADIO_TASK_QUEUE_CONCURRENCY=1`
- `LOG_FLUSH_DELAY_MS=1200`
- `LOG_FLUSH_BATCH_SIZE=20`

If the whitelist is set, only those countries are allowed to trigger automatic import.

Read-heavy endpoints are intentionally cache-friendly:

- `GET /api/radio/stations` uses short public cache with stale-while-revalidate
- `GET /api/radio/broadcasts` uses shorter cache and is still treated as non-realtime

This keeps the backend biased toward low fan-out, single-direction reads instead of realtime push.
Assistant logs are also buffered briefly in memory and then flushed to SQLite in batches,
which reduces write amplification under high terminal counts.

## MiniMax

The backend supports MiniMax through the official OpenAI-compatible API.

1. Copy `.env.example` to `.env`
2. Fill in `MINIMAX_API_KEY`
3. Restart the backend

Default base URL:
`https://api.minimax.io/v1`

Speech settings:

- `MINIMAX_SPEECH_MODEL`
- `MINIMAX_SPEECH_VOICE_ID`
- `MINIMAX_SPEECH_SPEED`

If no API key is configured, model leasing can still use the local mock router, but
`POST /api/radio/ai/respond` will not synthesize audio until `MINIMAX_API_KEY` is set.

## Home Integration Contract

Sonance also exposes the fixed `home` control-plane integration endpoints:

- `GET /internal/platform/health`
- `POST /internal/platform/broadcasts`

Both endpoints expect the shared secret header:

- `x-home-platform-token`

Set the shared secret in Sonance with:

- `HOME_PLATFORM_TOKEN`

`GET /internal/platform/health` stays cheap and side-effect free.
`POST /internal/platform/broadcasts` accepts a platform broadcast envelope and writes it
into the local radio timeline as a `system` broadcast, so Sonance can stay independently
runnable while still receiving shared control-plane notices from `home`.

Production routing note:

- `/api/router/**` -> `home`
- `/api/ota/**` -> `home`
- `/api/admin/**` -> `home`
- `/api/radio/**` -> Sonance runtime
- `/uploads/radio/**` -> Sonance runtime
- `/internal/platform/**` -> Sonance runtime

## Admin Email Login

The admin console supports email verification-code login for whitelisted operator emails.

1. Copy `.env.example` to `.env`
2. Fill in the `ADMIN_SMTP_*` settings
3. Restart the backend

The default seeded admin email is:
`soulzyn@outlook.com`
