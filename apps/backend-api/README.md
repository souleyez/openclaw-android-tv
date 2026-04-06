# Sonance Backend API

This is the backend workspace for Sonance.

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

## Model Pool

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

## Radio API

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

## Admin Email Login

The admin console supports email verification-code login for whitelisted operator emails.

1. Copy `.env.example` to `.env`
2. Fill in the `ADMIN_SMTP_*` settings
3. Restart the backend

The default seeded admin email is:
`soulzyn@outlook.com`
