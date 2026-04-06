# Sonance Admin Console

Status: frozen legacy console.

- This workspace is kept only for migration compatibility, rollback safety, and reference.
- New control-plane/admin development for Sonance now belongs in `C:\Users\soulzyn\Desktop\codex\home`.
- Do not add new admin features here unless the change is required for compatibility or emergency maintenance.

## Run

```powershell
C:\Users\soulzyn\develop\node\npm.cmd install
C:\Users\soulzyn\develop\node\npm.cmd run dev
```

Default URL: `http://127.0.0.1:3001`

Set backend base URL with:

```powershell
$env:ADMIN_API_BASE_URL="http://127.0.0.1:3000/api"
```

Preferred future admin entry:

- unified gateway / `home` admin on `http://1.12.246.48/login`
