# OpenClaw Android TV Client

OpenClaw 的 Android / Android TV Flutter 客户端工程。

控制面已经迁到 `home`，客户端默认复用统一入口：

- `http://1.12.246.48/api`

这意味着：

- `/api/router/**` 和 `/api/ota/**` 由 `home` 提供
- `/api/radio/**` 和 `/uploads/radio/**` 走共享 Sonance runtime
- Android TV 仍然是独立 APP，但不再要求一套独立公共后端

当前状态不是空骨架，已经包含这些能力：

- Flutter 端 TV 首页控制台
- Android 原生 MethodChannel 语音桥接
- Android 控制桥接，支持应用启动、DPAD、音量、媒体控制
- 网络状态读取与离线本地路由
- 账号、设备、头像、账单、OTA 等 API 客户端与 fallback 数据

## 项目结构

- `lib/app_shell`: 应用壳层与主题入口
- `lib/core`: API 配置、API 客户端、全局状态
- `lib/features/tv_home`: TV 首页控制台主页面
- `lib/features/voice`: 语音桥接封装
- `lib/features/control`: 控制动作、控制服务、离线路由
- `lib/features/account`: 用户资料与权益恢复
- `lib/features/device_sync`: 设备注册与同步
- `lib/features/billing`: 充值订单与支付状态
- `lib/features/network`: 网络状态快照
- `lib/features/ota`: OTA 清单与回报
- `android/app/src/main/kotlin/...`: Android TV 原生桥接与前台服务

## 本地运行

安装依赖：

```bash
flutter pub get
```

直接运行：

```bash
flutter run
```

如果需要覆盖默认入口，使用 `dart-define` 指定 API 地址：

```bash
flutter run --dart-define=OPENCLAW_API_BASE_URL=http://192.168.1.10:3312
```

说明：

- 未传 `OPENCLAW_API_BASE_URL` 时，默认使用 `http://1.12.246.48/api`
- 如果传入的地址不带 `/api`，客户端会自动补上
- 只有在你刻意启动本地 Android TV 兼容 backend 时，才需要覆盖到 `3312` 之类的本地端口
- Android 模拟器或真机如果连本地兼容 backend，需要改成局域网可访问地址或模拟器专用地址

## 当前后端口径

- 共享控制面：`home`
- 共享统一入口：`http://1.12.246.48`
- 本仓 `apps/backend-api`：只作为迁移兼容层和本地 smoke 面，不再继续长公共后端能力

## 当前主要技术债

- `lib/features/tv_home/tv_home_page.dart` 体量过大，需要拆层
- 测试覆盖率很低，当前只有基础 widget test
- 部分链路仍依赖 fallback 数据，后续需要和真实后端契约收敛
