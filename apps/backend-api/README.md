# Backend API Skeleton

This is the OpenClaw Android and Android TV backend workspace.

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

## MiniMax

The backend supports MiniMax through the official OpenAI-compatible API.

1. Copy `.env.example` to `.env`
2. Fill in `MINIMAX_API_KEY`
3. Restart the backend

Default base URL:
`https://api.minimaxi.com/v1`

If no API key is configured, the backend falls back to the local mock router so development can continue.

## Admin Email Login

The admin console supports email verification-code login for whitelisted operator emails.

1. Copy `.env.example` to `.env`
2. Fill in the `ADMIN_SMTP_*` settings
3. Restart the backend

The default seeded admin email is:
`soulzyn@outlook.com`
