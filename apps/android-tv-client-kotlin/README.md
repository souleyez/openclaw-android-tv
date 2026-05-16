# OpenClaw Android TV Kotlin Client

这是新的原生 Android TV 客户端工程。

当前约束：

- `apps/android-tv-client` 继续作为冻结的 Flutter 过渡客户端
- 共享后端能力继续来自 `home`
- 这个 baseline 默认不依赖任何主板厂商系统服务

这份工程的第一目标不是做最终系统级体验，而是先建立一个可运行、可联调、可持续演进的 Kotlin 原生客户端骨架。

## Runtime API Smoke

默认 TV 客户端 canonical API 基线是：

- `https://oc.goods-editor.com/api`

仓库提供一个 PowerShell smoke 脚本，用已有 session token 直接验证关键 runtime 路由：

- `GET /me/tv-home-config`
- `GET /me/runtime-manifest`
- `GET /me/entitlement`
- `GET /client/resource-session/status`

如果没有现成 session token，也可以先用 bootstrap 身份信息自动换取 session，再继续 smoke。

默认 smoke 是只读的，不会主动申请或释放新的 `resource-session`。

运行 canonical smoke：

```powershell
$env:OPENCLAW_SESSION_TOKEN = "<session-token>"
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\smoke-runtime-api.ps1
```

临时覆盖 API 入口 smoke：

```powershell
$env:OPENCLAW_SESSION_TOKEN = "<session-token>"
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\smoke-runtime-api.ps1 -BaseUrl https://oc.goods-editor.com/api
```

仅检查 URL、认证要求和 artifact 输出而不发请求：

```powershell
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\smoke-runtime-api.ps1 -DryRun
```

显式开启 `resource-session` 生命周期 smoke：

```powershell
$env:OPENCLAW_SESSION_TOKEN = "<session-token>"
$env:OPENCLAW_RESOURCE_SESSION_REQUEST_APP_ID = "youtube"
$env:OPENCLAW_RESOURCE_SESSION_REQUEST_PROVIDER_SCOPE = "moonshot"
$env:OPENCLAW_RESOURCE_SESSION_REQUEST_LEASE_PROFILE = "server_10m"
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\smoke-runtime-api.ps1 -ExerciseResourceSessionLifecycle
```

这个模式会按顺序执行：

- `POST /client/resource-session/request`
- `GET /client/resource-session/status?resourceSessionId=...`
- `POST /client/resource-session/release`

如果生命周期 smoke 中途失败，脚本会尝试做一次 best-effort `release` 清理，避免把测试申请的 session 留在队列里。

可选环境变量：

- `OPENCLAW_API_BASE_URL`
- `OPENCLAW_SESSION_TOKEN`
- `OPENCLAW_RESOURCE_SESSION_ID`
- `OPENCLAW_RESOURCE_SESSION_REQUEST_APP_ID`
- `OPENCLAW_RESOURCE_SESSION_REQUEST_PROVIDER_SCOPE`
- `OPENCLAW_RESOURCE_SESSION_REQUEST_LEASE_PROFILE`
- `OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE`
- `OPENCLAW_BOOTSTRAP_PRINCIPAL_KEY`
- `OPENCLAW_BOOTSTRAP_PRINCIPAL_LABEL`
- `OPENCLAW_PROJECT_KEY`
- `OPENCLAW_DEVICE_FINGERPRINT`
- `OPENCLAW_DEVICE_NAME`
- `OPENCLAW_CLIENT_VERSION`
- `OPENCLAW_OS_FAMILY`

用 bootstrap 自动取 session 再跑 smoke：

```powershell
$env:OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE = "device"
$env:OPENCLAW_BOOTSTRAP_PRINCIPAL_KEY = "tv-install-01"
$env:OPENCLAW_DEVICE_FINGERPRINT = "install-01"
powershell -ExecutionPolicy Bypass -File C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin\scripts\smoke-runtime-api.ps1
```

脚本会把结果写到：

- `apps/android-tv-client-kotlin/artifacts/runtime-smoke/`
