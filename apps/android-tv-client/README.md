# OpenClaw Android TV Client

OpenClaw 的 Android / Android TV Flutter 客户端原型工程。

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

如果需要连接本地以外的后端，使用 `dart-define` 覆盖 API 地址：

```bash
flutter run --dart-define=OPENCLAW_API_BASE_URL=http://192.168.1.10:3000
```

说明：

- 未传 `OPENCLAW_API_BASE_URL` 时，默认使用 `http://127.0.0.1:3000/api`
- 如果传入的地址不带 `/api`，客户端会自动补上
- Android 模拟器通常需要改成 `http://10.0.2.2:3000`
- 真机或电视设备需要改成局域网可访问地址

## 当前主要技术债

- `lib/features/tv_home/tv_home_page.dart` 体量过大，需要拆层
- 测试覆盖率很低，当前只有基础 widget test
- 部分链路仍依赖 fallback 数据，后续需要和真实后端契约收敛
