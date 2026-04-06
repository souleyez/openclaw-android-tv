# Repo Commit Scopes

This file defines how to split the current multi-project platform migration into repository-local commits without mixing unrelated work.

## Canonical repo roles

- `home`
  - the only shared platform repository
- `ai-data-platform`
  - AI assistant application repository
- `Sonance`
  - Sonance application repository
- `openclaw-android-tv`
  - Android TV application repository

## General rule

Do not create one cross-repo mega commit.

Each repository should be committed independently, and each commit should stay inside one of these buckets:

1. shared platform mainline
2. application-side integration contract
3. frozen legacy reference boundary
4. local runtime files that must never be committed

## home

### Status

`home` is the canonical platform mainline. The current dirty files are part of the platform migration and may be submitted from this repository.

### Recommended submit groups

1. `apps/platform-api/**`, `config/**`, platform docs
2. `app/**` public admin changes
3. `README.md`, `docs/deployment.md`, `docs/project-integration-contract.md`, `docs/project-workspace-guidelines.md`, `docs/plans/**`

### In scope now

- `apps/`
- `app/`
- `config/`
- `docs/`
- `README.md`
- `package.json`
- `.gitignore`

## ai-data-platform

### Status

This repository now has three different kinds of changes mixed together:

1. valid application-side integration work
2. valid boundary/freeze work
3. old duplicate control-plane implementation that should not be the primary submission target anymore

### Commit group A: application-side integration

Submit together:

- `README.md`
- `apps/api/.env.example`
- `apps/api/README.md`
- `apps/api/src/app.ts`
- `apps/api/src/lib/platform-integration.ts`
- `apps/api/src/routes/platform-integration.ts`
- `apps/api/test/platform-integration-routes.test.ts`
- `deploy/server/ai-data-platform.env.example`
- `deploy/server/update-server.sh`
- `deploy/server/systemd/ai-data-platform-control-plane-api.service`
- `deploy/server/systemd/ai-data-platform-control-plane-web.service`
- `docs/DEPLOYMENT_SERVER.md`
- `tools/deploy-remote.ps1`
- `tools/start-local.ps1`
- `tools/status-local.ps1`
- `tools/set-home-platform-token.ps1`
- `package.json`
- `.gitignore`

### Commit group B: legacy control-plane freeze boundary

Submit together only if you still want the old directories to remain as frozen references:

- `apps/control-plane-api/package.json`
- `apps/control-plane-api/README.md`
- `apps/control-plane-web/package.json`
- `apps/control-plane-web/README.md`
- `apps/control-plane-web/app/layout.js`
- `docs/APP_BOUNDARY_2026-04-05.md`

### Do not include in the normal app submission

These paths duplicate the shared platform that now lives in `home`:

- `apps/control-plane-api/src/**`
- `apps/control-plane-api/test/**`
- `apps/control-plane-web/app/api/**`
- `apps/control-plane-web/app/components/**`
- `apps/control-plane-web/app/lib/**`
- `apps/control-plane-web/app/login/**`
- `apps/control-plane-web/app/projects/**`

If those paths need to be preserved, treat them as a separate archive/reference commit, not as the main `ai-data-platform` app submission.

### Local-only files to exclude

- `storage/config/platform-integration.json`
- `tmp/**`
- any `.env` file

## Sonance

### Status

Current dirty files are aligned with the platform contract migration.

### Recommended single submission

Submit together:

- `README.md`
- `docs/APP_BOUNDARY_2026-04-05.md`
- `apps/backend-api/.env.example`
- `apps/backend-api/README.md`
- `apps/backend-api/src/app.module.ts`
- `apps/backend-api/src/main.ts`
- `apps/backend-api/src/shared/storage.service.ts`
- `apps/backend-api/src/platform-integration/**`
- `apps/admin-console/README.md`
- `apps/admin-console/app/layout.tsx`
- `apps/admin-console/app/page.tsx`
- `apps/admin-console/app/globals.css`

## openclaw-android-tv

### Status

Most current dirty files are aligned with the platform contract migration, but one file is pre-existing and should stay out of the platform contract submission.

### Recommended submission

Submit together:

- `README.md`
- `docs/APP_BOUNDARY_2026-04-05.md`
- `apps/backend-api/.env.example`
- `apps/backend-api/README.md`
- `apps/backend-api/src/app.module.ts`
- `apps/backend-api/src/main.ts`
- `apps/backend-api/src/platform-integration/**`
- `apps/admin-console/README.md`
- `apps/admin-console/app/layout.tsx`
- `apps/admin-console/app/page.tsx`
- `apps/admin-console/app/globals.css`

### Keep out of this submission

- `apps/backend-api/src/modules/auth/auth.service.ts`

That file is a separate local change and should not be mixed into the platform contract commit unless you explicitly decide to include it.

## Staging rule

When preparing real commits:

1. stage one repository at a time
2. stage one commit group at a time
3. leave local runtime files unstaged
4. do not mix `home` platform implementation with `ai-data-platform` legacy reference code
