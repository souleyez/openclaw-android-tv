# OpenClaw Admin Console Architecture

## 1. Document Info

- Name: Admin Console Architecture
- Version: v0.1
- Date: 2026-03-19
- Related backend design: [2026-03-19-backend-architecture.md](C:\Users\soulzyn\Desktop\codex\docs\architecture\2026-03-19-backend-architecture.md)
- Related operations plan: [2026-03-19-billing-and-operations-plan.md](C:\Users\soulzyn\Desktop\codex\docs\ops\2026-03-19-billing-and-operations-plan.md)

## 2. Goal

Build a proper multi-page admin console for OpenClaw that supports:

- Closed beta operations now
- Scalable frontend fleet management later
- Manual financial and support workflows
- Model API pool operations
- OTA, avatar, and device policy control
- High-volume observability without making the control plane slow

The target is not a generic ERP-style backend. It is an operator console for an AI TV assistant platform.

## 3. Core Design Principles

- Admin console and client-serving APIs are different planes.
- The write path for device traffic must stay simple and fast.
- Heavy analytics queries must not hit the same tables and indexes used by hot online control flows.
- Manual operator actions must be auditable.
- Every page should answer one operational question clearly.
- Start with a modular monolith backend plus a dedicated admin web app, then split services only when load proves it is necessary.

## 4. Recommended Product Shape

### 4.1 Admin form factor

Use a dedicated admin web app, not a single HTML page served from the API layer forever.

Recommended:

- `apps/admin-console`
- Framework: `Next.js`
- UI style: clean data console, table-heavy, fast, low animation
- Auth: operator login with RBAC

The current `/api/admin` server-rendered HTML can stay as a bootstrap fallback, but the long-term proper console should be a separate web frontend.

### 4.2 Admin page map

Recommended top-level sections:

1. Overview
2. Device Fleet
3. Device Users And Rights
4. Billing And Orders
5. Model API Pool
6. Router And Logs
7. OTA And Versions
8. Avatar And Content
9. Finance And Reconciliation
10. Risk And Audit
11. System Settings

## 5. Multi-Page Information Architecture

### 5.1 Overview

Purpose:

- Let operators know whether the system is healthy in under 30 seconds.

Key widgets:

- Online devices now
- Active device users
- Requests per minute
- Voice turns per minute
- Local-control success rate
- Model routing success rate
- Stablecoin orders by status
- API pool accounts expiring in 7 days
- OTA rollout health
- Top alarms

This page should be mostly read-only and cached aggressively.

### 5.2 Device Fleet

Purpose:

- Operate the client fleet.

Primary views:

- Device list
- Device detail
- Capability snapshot
- Online status timeline
- Installed version distribution
- Android version / ROM / region distribution

Key actions:

- Search by `device_uuid`
- Search by `device_user_id`
- Filter by online/offline
- Filter by version
- Mark suspicious device
- Freeze device
- View last commands and failures

### 5.3 Device Users And Rights

Purpose:

- Manage the lightweight account system centered on `device_user_id`.

Primary views:

- Device user list
- Rights detail
- Transfer history
- Recovery proof history

Key actions:

- Update plan code
- Update entitlement expiry
- Disable user
- Add recovery hint
- Trigger manual transfer
- View inherited device chain

### 5.4 Billing And Orders

Purpose:

- Operate stablecoin-led payment flows and support edge cases.

Primary views:

- Orders list
- Order detail
- Payment proof review queue
- Wallet ledger

Key actions:

- Filter by `orderId`
- Filter by `txHash`
- Filter by chain / coin / status
- Mark `reviewing`
- Mark `failed`
- Mark `expired`
- Force `confirmed` only under privileged approval
- Add review note

### 5.5 Model API Pool

Purpose:

- Operate all upstream model provider accounts and quotas.

Primary views:

- Pool account list
- Provider health view
- Expiry calendar
- Pool usage chart
- Routing policy summary

Key objects:

- Provider
- Account label
- Plan label
- Status
- Renews at
- Expires at
- Daily quota
- Remaining quota
- Region scope
- Notes

Key actions:

- Add account
- Pause account
- Mark expiring
- Disable exhausted key
- Switch routing weight
- Assign fallback priority

This is one of the most important pages and should exist as a first-class section, not a small form.

### 5.6 Router And Logs

Purpose:

- Debug assistant behavior and performance.

Primary views:

- Recent voice turns
- Recent chat turns
- Control blocked events
- Routing failures
- Model fallback reasons
- Device token consumption events

Key actions:

- Search by `device_id`
- Search by `device_user_id`
- Search by phrase
- Filter by route
- Filter by provider
- Filter by mode
- Filter by confidence bucket

### 5.7 OTA And Versions

Purpose:

- Manage staged rollout for client versions.

Primary views:

- Version list
- Rollout policy list
- Rollout wave progress
- Failure rate by version

Key actions:

- Create release
- Pause release
- Resume release
- Roll back release
- Target by region / device class / version

### 5.8 Avatar And Content

Purpose:

- Manage frontend visible assistant identity.

Primary views:

- Avatar list
- Active avatar preview
- Voice / locale mapping
- Asset references

Key actions:

- Activate avatar
- Update colors
- Upload or set asset URL
- Bind avatar to rollout group

### 5.9 Finance And Reconciliation

Purpose:

- Give operators and finance a clean settlement view.

Primary views:

- Revenue summary
- Top-up summary
- Provider cost summary
- API pool cost summary
- Gross margin summary

Key actions:

- Export order CSV
- Export wallet ledger CSV
- Review large manual adjustments

### 5.10 Risk And Audit

Purpose:

- Keep a full paper trail.

Primary views:

- Operator audit log
- Device rights transfers
- Manual order changes
- API pool changes
- Suspicious device events

Every manual action in the admin console must write here.

## 6. Recommended Backend Module Split

Keep the client API and admin API in one backend repo for now, but split the backend into explicit admin-facing modules:

- `admin-overview`
- `admin-device-fleet`
- `admin-device-users`
- `admin-billing`
- `admin-api-pool`
- `admin-router-observability`
- `admin-ota`
- `admin-avatar`
- `admin-finance`
- `admin-audit`

This is still a modular monolith, not a premature microservice breakdown.

## 7. Data Plane Separation For Scale

This is the most important part for your “many frontends” requirement.

### 7.1 Why separation is needed

When large numbers of client devices connect, three workloads become very different:

- Hot online control traffic
- Operational dashboard reads
- Analytics and historical queries

If they all hit the same tables and indexes in the same way, admin pages will eventually hurt live traffic.

### 7.2 Recommended logical layers

#### Layer A: Online transaction store

Used for:

- Device registration
- Rights checks
- Billing orders
- Wallet ledger
- Routing decisions
- Control authorization

Recommended database:

- `PostgreSQL`

Characteristics:

- Strong consistency
- Small indexed tables for hot reads
- Short write transactions

#### Layer B: Fast cache and counters

Used for:

- Device online presence
- Session-level rate limiting
- API pool quota counters
- Dashboard near-real-time counters

Recommended:

- `Redis`

Characteristics:

- TTL-based presence
- atomic counters
- queue support

#### Layer C: Event stream / async ingestion

Used for:

- Assistant logs
- Device token usage events
- Device heartbeat events
- Routing and fallback events
- API pool usage events

Recommended:

- `BullMQ` now
- can evolve to `Kafka` later only if scale justifies it

Characteristics:

- write-behind
- decouples hot path from reporting path

#### Layer D: Analytics / observability store

Used for:

- log search
- dashboards
- historical trends
- route failure analysis
- high-cardinality event inspection

Recommended:

- `ClickHouse` for event-scale analytics
- or `PostgreSQL + Timescale` only if load remains modest

Recommendation:

- Start with Postgres for transactional data
- Add ClickHouse once device event volume gets high

Do not run long log queries against the live transactional tables forever.

## 8. Performance Design For Large Frontend Fleet

### 8.1 Expected pressure points

The biggest scaling risks are:

- Heartbeat / presence flood
- Voice turn burst traffic
- Logging volume
- OTA check storms after release
- Dashboard aggregate queries
- API pool quota contention

### 8.2 Concrete performance strategy

#### Device heartbeat

- Do not write every heartbeat directly to primary relational tables.
- Store “online now” in Redis with TTL.
- Flush summarized heartbeat snapshots periodically to Postgres.

#### Voice and control logs

- Write core request result synchronously only if needed for correctness.
- Push detailed log events to async queue.
- Batch ingest into analytics store.

#### OTA checks

- Use cached release policy snapshots.
- Use deterministic rollout matching logic with precomputed segments.
- Avoid complex joins on every device version check.

#### Admin dashboard

- Precompute aggregates every 15 to 60 seconds.
- Dashboard pages should hit summary tables or Redis snapshots, not raw log scans.

#### API pool routing

- Keep account state and quota counters in Redis.
- Persist durable changes back to Postgres asynchronously or transactionally at lower frequency.
- Use local in-process cache for routing policy with short TTL.

#### Table indexing

For Postgres, priority indexes should include:

- `devices(device_uuid)`
- `devices(account_id, updated_at desc)`
- `stablecoin_payment_orders(account_id, updated_at desc)`
- `stablecoin_payment_orders(tx_hash)`
- `assistant_logs(account_id, created_at desc)`
- `device_entitlement_transfers(to_account_id, created_at desc)`
- `api_pool_accounts(provider, status, expires_at)`

### 8.3 Capacity planning guidance

For a serious closed beta moving toward larger rollout:

- Keep hot synchronous API p95 under `150ms` for device control authorization
- Keep voice-turn routing API p95 under `600ms` before upstream model time
- Keep admin dashboard summary queries under `500ms`
- Keep log search queries off the hot OLTP database once logs exceed low millions per day

## 9. Security And RBAC

Recommended roles:

- `super_admin`
- `ops_admin`
- `support_agent`
- `finance_operator`
- `model_pool_operator`
- `release_manager`
- `read_only_auditor`

Examples:

- `support_agent` can view logs and device users but cannot confirm orders
- `finance_operator` can change order state but cannot change API pool routing
- `release_manager` can control OTA but cannot touch finance

All sensitive actions must require:

- operator identity
- timestamp
- before value
- after value
- reason note

## 10. Recommended Technical Stack

### Admin frontend

- `Next.js`
- `TypeScript`
- `TanStack Query`
- `AG Grid` or high-quality data table
- simple charting only where necessary

### Admin backend

- existing `NestJS`
- route grouping under `/admin/*`
- Redis for cache / presence / counters
- PostgreSQL for transactional state
- ClickHouse later for event analytics

## 11. Rollout Plan

### Phase A: Proper operator console

- Keep current backend
- Add multi-page admin frontend
- Build pages:
  - Overview
  - Device Users
  - Orders
  - API Pool
  - Logs
  - Avatars

### Phase B: Operational hardening

- Add Redis counters and presence
- Add summary materialization
- Add audit log UI
- Add better search and filters

### Phase C: Scale prep

- Introduce analytics/event store
- Move log and trend queries away from OLTP
- Add API pool quota scheduler
- Add OTA staged rollout UI

## 12. Near-Term Build Recommendation

If we start implementation next, I recommend this exact order:

1. Create `apps/admin-console`
2. Build layout + navigation shell
3. Build `Overview`
4. Build `Model API Pool`
5. Build `Billing And Orders`
6. Build `Device Users`
7. Build `Logs`
8. Add audit layer
9. Add Redis-backed summary counters

This order is best because it first unlocks operations, finance, and model-pool control, which are the highest leverage backend tasks for your current project stage.

## 13. Conclusion

The correct “serious backend” shape for OpenClaw is:

- client-serving API plane stays fast and simple
- admin becomes a separate multi-page console
- API pool is a first-class page
- analytics reads are separated from hot online writes
- Redis absorbs presence, counters, and quota pressure
- Postgres remains the source of truth

That gives us a backend that is still practical to build now, but does not corner us when many frontend devices start hitting the platform.
