# Android TV 系统级适配厂商资料表

## 1. 使用说明

这份表用于向硬件厂家、主板厂家、BSP 团队索取系统级适配资料。

当前 APK baseline 可以先跑首页、应用分发、DLNA 媒体接收、系统设置跳转等公共 Android 能力。但如果要继续做到“系统级助手”体验，例如播放中唤醒、跨 App 媒体控制、投影硬件控制、系统级投屏/镜像、设备级 OTA 与诊断，就必须由厂商提供系统资料、接口和可联调固件。

最新 P1 追料入口：`docs/ops/2026-05-16-vendor-p1-follow-up.md`。该文档基于 2026-05-16 的 P1 acceptance APK 和真机日志，把当前 APP 已验证能力、厂商阻塞项、可直接发给厂商的话术、以及“blocked -> development-ready”的验收门槛整理成短版清单。

优先级说明：

| 优先级 | 含义 | 对项目影响 |
| --- | --- | --- |
| P0 | 必须提供 | 不提供则无法进入系统级适配，只能做普通 APK baseline。 |
| P1 | 强烈建议提供 | 影响体验上限、量产稳定性和售后闭环。 |
| P2 | 可选补充 | 不阻塞首轮联调，但影响兼容性、认证和量产优化。 |

## 2. P0 必须提供

| 分类 | 厂商需提供资料 | 我方用途 | 缺失影响 |
| --- | --- | --- | --- |
| 硬件基础 | 主控 SoC 型号、板型、内存、存储、Wi-Fi/BT 芯片型号 | 判断性能边界、投屏能力、音频链路和适配风险 | 无法确认 APK baseline 和系统能力边界 |
| 系统版本 | Android 版本、API level、是否 Android TV、是否 GMS | 确定公开 API、权限、兼容策略 | 无法确定安装、启动、权限和投屏策略 |
| BSP/固件 | BSP 分支、固件版本、内核版本 | 定位系统能力来源和问题归属 | 无法稳定复现和追踪系统问题 |
| 固件包 | 可刷测试固件、刷机工具、刷机步骤、回滚步骤 | 建立可控联调环境 | 无法独立联调，问题只能口头排查 |
| 调试方式 | adb/root/串口调试方式 | 抓日志、看 tombstone、验证 service | 系统级问题不可定位 |
| 签名策略 | 平台签名、priv-app 白名单、权限白名单 | 确定 APK 是否能调用系统接口 | vendor service 可能无法被 APK 调用 |
| SELinux | SELinux 状态、相关 policy 增量 | 判断 Binder、音频、投屏、诊断访问限制 | 接口可能存在权限通过但运行被拦截的问题 |
| 保活策略 | 开机自启、后台保活、任务清理策略 | 保证助手服务和投屏接收稳定 | 运行中可能被系统清掉 |
| vendor service | 已有 vendor service 列表 | 确定系统级能力入口 | 无法规划适配层 |
| 接口文档 | AIDL/Binder/SDK 文档、jar/aar/aidl 文件 | 编写客户端 adapter | 无法开发系统级能力 |
| 权限模型 | 普通 app、priv-app、platform-signed app 各自可调用能力 | 确定打包、预装、签名方案 | 联调后可能发现身份不够 |
| 生命周期 | service 启动时机、重启策略、binder death 行为 | 处理开机、进程重启、异常恢复 | 容易出现启动 race 和断连 |
| 错误模型 | 错误码、降级状态、超时策略 | 建立 UI 提示和后端诊断 | 失败只能静默或笼统报错 |
| 示例程序 | sample client 或 demo APK | 快速验证接口真实性 | 适配成本和沟通成本显著增加 |
| 联调版本 | 最小可联调固件和对应接口版本 | 固定双方联调基线 | 容易出现固件/API 不匹配 |

## 3. P0 音频、媒体、投屏专项资料

| 专项 | 厂商需提供资料 | 我方用途 | 缺失影响 |
| --- | --- | --- | --- |
| 麦克风 | 麦克风数量、阵列布局、采样率、声道数 | 判断唤醒、AEC、ASR 输入质量 | 播放中语音体验不可评估 |
| 音频 HAL | 音频 HAL 路由说明、输入输出设备映射 | 处理采集、播放、ducking | 容易出现录不到音或播放冲突 |
| 播放回采 | playback reference 是否可取 | 支撑 AEC 和播放中唤醒 | 无法稳定做到边播边唤醒 |
| 语音前端 | AEC、NS、AGC、VAD、唤醒词由谁提供 | 确定 app 做轻适配还是系统接管 | 如果 app 自己做，低端板性能风险高 |
| 唤醒事件 | 播放中唤醒是否支持、事件如何上报 APK | 做系统级助手入口 | 只能做手动入口或普通 APK 语音 |
| cleaned audio | cleaned audio 如何给 APK 或云端 ASR | 接入 ASR 链路 | 语音识别质量不稳定 |
| 音频策略 | 音频焦点、ducking、打断/不打断播放策略 | 做用户体验策略 | 唤醒可能打断当前播放 |
| 媒体 owner | 当前前台 App 和当前播放 owner 检测方式 | 做跨 App 媒体控制 | “暂停/继续”无法准确命中 |
| MediaSession | MediaSession 兼容情况、媒体按键 dispatch 策略 | 决定控制链路 | 部分 App 控制会失效 |
| 控制动作 | 播放、暂停、继续、上一首、下一首、快进、快退、seek | 建立统一媒体控制接口 | 只能承诺最低级遥控器模拟 |
| 内容打开 | 搜索、打开指定内容、deeplink 系统支持情况 | 做“打开 YouTube 搜索某内容”等能力 | 只能打开 App，不能打开内容 |
| 重点 App | Netflix、YouTube、Spotify、本地播放器控制差异 | 建立 App 兼容矩阵 | 海外重点 App 风险不可控 |
| 系统投屏 | Miracast / Wi-Fi Display sink 支持情况 | 判断是否能做系统镜像投屏 | 普通 APK 只能做 DLNA 媒体接收 |
| Wi-Fi P2P | Wi-Fi P2P 支持情况和系统入口 | 支撑安卓手机镜像/系统投屏 | 安卓镜像链路不可承诺 |
| 投屏入口 | 系统投屏设置 Intent 或 vendor API | 首页投屏入口适配 | 用户可能找不到系统投屏入口 |
| 厂商投屏 | 厂商自带投屏服务及第三方 APK 唤起方式 | 复用已有投屏能力 | 需要重新做或无法调用 |
| 网络发现 | DLNA/UPnP 多播是否被系统或路由策略限制 | 验证 DLNA 发现稳定性 | 手机可能找不到 TV |
| 设备名 | 设备名来源和修改方式 | 保证手机投屏列表显示正确 | 用户找不到对应设备 |
| DRM/HDCP | HDCP、DRM、分辨率、音频输出限制 | 判断投屏播放限制 | 受保护内容可能无法播放 |
| 投屏日志 | 投屏失败错误码和日志路径 | 问题定位和售后 | 投屏失败难以定位 |

## 4. P1 强烈建议提供

| 分类 | 厂商需提供资料 | 我方用途 | 缺失影响 |
| --- | --- | --- | --- |
| 自动对焦 | 自动对焦、手动对焦 API | 做语音/首页快捷控制 | 投影硬件只能靠系统设置或遥控器 |
| 梯形校正 | 梯形校正 API、参数范围、状态查询 | 做投影画面校正能力 | 无法承诺投影设备级控制 |
| 光机控制 | 亮度、色彩模式、光机控制 API | 做观影模式、护眼、亮度调节 | 投影体验上限受限 |
| 输入源 | HDMI/本地/系统输入源切换 API | 做“切到 HDMI”等控制 | 输入源无法系统级控制 |
| 电源状态 | 待机、唤醒、休眠 API | 做设备级待机/唤醒 | 只能打开系统设置，不能控制硬件状态 |
| 硬件状态 | 风扇、温度、光机寿命、硬件忙状态 | 做诊断和保护策略 | 售后和异常保护能力不足 |
| OTA 机制 | A/B、recovery、整包、差分机制说明 | 规划系统版本更新 | 无法做系统更新分发管理闭环 |
| OTA 状态 | 下载、校验、安装、重启、失败、回滚状态定义/API | 首页/后台展示升级状态 | 升级失败不可诊断 |
| APK 预装 | 预装位置、更新策略、priv-app 是否允许普通 APK 覆盖 | 确定量产升级路径 | 可能出现预装后无法更新 |
| 版本矩阵 | 固件与 APK 版本兼容矩阵 | 避免错配升级 | 容易升级到不兼容组合 |
| 系统日志 | logcat、dmesg、tombstone、ANR 路径 | 做问题定位 | 线上问题无法回溯 |
| 专项日志 | 投屏、音频、媒体控制、OTA 的专项日志 | 定位系统级能力失败 | 只能看到 APK 层现象 |
| 日志导出 | 无 adb 场景下的日志导出方式 | 售后和灰度排障 | 用户现场难以收集证据 |
| 健康状态 | 存储、内存、温度、网络、crash/ANR 摘要接口 | 设备诊断和后台内容管理决策 | 故障只能靠人工描述 |
| 遥控器 | 红外/蓝牙/2.4G 类型、keycode 映射 | 保证 10-foot UI 和快捷键 | 焦点和按键体验不可控 |
| 自定义键 | Home、Back、Menu、语音键、设置键、长按、组合键行为 | 决定是否接管语音键或快捷入口 | 无法做硬件键级入口 |

## 5. P2 可选补充

| 分类 | 厂商需提供资料 | 我方用途 | 缺失影响 |
| --- | --- | --- | --- |
| 编解码 | H.264、H.265、VP9、AV1、AAC、AC3 等能力列表 | 判断本地播放和投屏兼容性 | 个别媒体播放失败难提前规避 |
| DRM | Widevine level、HDCP 版本 | 判断 Netflix/YouTube/受保护内容限制 | 海外内容 App 体验风险不清 |
| 显示 | 分辨率、刷新率、HDR 支持情况 | UI 和播放适配 | 高低清、HDR 表现不可控 |
| 网络 | Wi-Fi 2.4G/5G、热点、蓝牙共存限制 | 投屏和联网体验优化 | 网络发现和投屏稳定性风险 |
| 量产测试 | 量产测试工具、老化测试报告 | 评估硬件稳定性 | 批量质量风险不可量化 |
| 预装清单 | 出厂预装 App 列表和可裁剪 App 列表 | 控制系统资源和首页入口 | 低配设备资源可能被占满 |
| 地区策略 | 海外认证限制、地区限制、默认语言/时区策略 | 国际化和内容分发 | 海外版本行为不一致 |

## 6. 厂商交付物表

| 交付物 | 格式建议 | 验收标准 |
| --- | --- | --- |
| 系统资料说明文档 | Markdown / PDF / Word | 能说明硬件、系统、BSP、权限、限制。 |
| 接口文件 | AIDL / SDK / jar / aar | APK 能编译并调用 sample。 |
| 接口文档 | Markdown / PDF / Swagger 类似文档 | 有方法、参数、返回值、错误码、权限说明。 |
| demo APK | APK + 源码更佳 | 能在测试固件上调通核心 vendor service。 |
| 测试固件 | 可刷 image / OTA 包 | 能刷入、能回滚、版本号明确。 |
| 刷机工具 | 工具包 + SOP | 我方能独立刷机，不依赖远程口头指导。 |
| 日志 SOP | 文档 + 脚本更佳 | 能导出 APK、系统、vendor service 相关日志。 |
| 能力矩阵 | 表格 | 明确每项系统能力支持/不支持/限制条件。 |
| 已知问题 | `known-issues.md` | 有问题描述、影响范围、规避方式、计划修复版本。 |
| 联调联系人 | 表格 | 有技术负责人、响应方式、时区和问题升级路径。 |

建议目录结构：

```text
vendor-drop/
  firmware/
  flash-tools/
  docs/
  aidl-or-sdk/
  sample-app/
  logs-guide/
  compatibility-matrix/
  known-issues.md
```

## 7. 我方验收表

| 验收项 | 通过标准 | 结论 |
| --- | --- | --- |
| 固件可刷 | 能刷入测试固件并稳定启动 | 待验证 |
| APK 身份 | APK 可按约定身份安装或预装 | 待验证 |
| 接口可调 | vendor service 可从 APK 调通 | 待验证 |
| 能力状态 | 语音、媒体、投影、设备诊断至少各有明确可用/不可用状态 | 待验证 |
| 错误可解释 | 不支持能力有明确错误码或降级状态 | 待验证 |
| 生命周期恢复 | 重启、进程被杀、网络切换后能恢复绑定 | 待验证 |
| 日志可定位 | 日志能定位失败原因，而不是只能口头排查 | 待验证 |

## 8. 与当前 APK baseline 的关系

| 厂商资料状态 | 我方可承诺能力 |
| --- | --- |
| 未提供资料 | 只承诺普通 APK baseline：首页、应用入口、runtime-manifest、DLNA 媒体接收、系统设置跳转。 |
| 提供 P0 资料 | 可进入语音、媒体、投屏基础联调。 |
| 提供 P1 资料 | 可进入投影硬件、OTA、诊断闭环。 |
| 提供 P2 资料 | 可进入量产兼容、海外认证和体验优化。 |

## 9. 2026-05-16 P1 状态补充

| 项目 | 当前判断 | 最新证据/下一步 |
| --- | --- | --- |
| 普通 APK baseline | 可作为 P1 acceptance 包继续验收 | `openclaw-tv-p1-acceptance-debug-20260516-131509.apk` 已通过真机 smoke |
| DLNA 媒体接收 | APP 侧服务可启动，手机侧发现和推流仍需实测 | `OpenClawCast` 记录 `wlan0`, `192.168.1.2`, SSDP `239.255.255.250:1900` |
| 苹果/小米镜像 | 普通 APK 不承诺完美自建镜像，继续以乐播兜底或 vendor 系统能力为准 | 乐播 `AirPlayService` 可 service-only 拉起；需 vendor 提供系统 sink 能力 |
| 后台清理 | 普通 APK 只能 best-effort，不做冻结/强杀承诺 | `OpenClawTrim` 记录候选包清理；系统包/顽固后台仍需系统权限 |
| 开机自启 | receiver 已具备，但真实开机是否投递取决于系统策略 | shell 不能伪造标准 `BOOT_COMPLETED`；quickboot receiver smoke 可拉起首页 |
| OTA/诊断/硬件控制 | 仍为 vendor 阻塞 | 需按 `2026-05-16-vendor-p1-follow-up.md` 追料 |

## 10. 2026-06-30 工厂出货前权限决策清单

本清单用于 `0.1.14` 工厂试装包进入 fresh 机器验证前向厂商确认。每一项必须得到明确答复，不能用“应该可以”替代。

| 问题 | 可接受答案 | 需要证据 | 决策口径 |
| --- | --- | --- | --- |
| OpenClaw 是否可预装为 system app 或 priv-app | 支持 / 不支持 / 需新固件 | 预装路径、固件版本、安装后 `dumpsys package com.openclaw.tv` | 支持则走系统镜像预装；不支持则只能工程安装 |
| 默认 Home 是否可在固件内固化到 `com.openclaw.tv/.MainActivity` | 支持 / 工具设置 / 不支持 | 冷启动后 `resolve-activity MAIN/HOME` 输出 | 支持则可量产默认首页；工具设置则必须纳入工厂 SOP |
| `INSTALL_PACKAGES` 是否可白名单授予 | 支持 / 仅 platform 签名 / 不支持 | `dumpsys package` 权限段 | 支持则 OTA 可尝试静默安装；不支持则保留系统安装器 UI fallback |
| 恢复出厂后 OpenClaw 是否保留或自动重装 | 保留 / 工厂 provisioning 重装 / 删除 | 恢复出厂后的包存在性和 Home 状态 | 删除则必须定义重装流程或系统镜像预装 |
| 乐播或等价投屏服务是否保留并白名单 | 保留 / 替换 vendor 投屏 / 删除 | iPhone 和小米同 Wi-Fi 发现、连接、音频、结束返回 Home 记录 | 删除且无替代时不能承诺镜像投屏体验 |
| 无 ADB 日志导出方式 | 系统菜单 / U 盘导出 / 工厂工具 / 无 | logcat、crash、ANR、tombstone、投屏、OTA 日志样例 | 无日志路径则不能进入大规模试装 |
| 系统 OTA 能力 | A/B / recovery 整包 / 差分 / 无 | OTA 包格式、状态 API、失败回滚说明 | 有能力则纳入 home 后续系统 OTA 管理；无能力则仅管理 OpenClaw APK OTA |

## 11. 决策分类

| 分类 | 条件 | 后续动作 |
| --- | --- | --- |
| APK-only acceptable | fresh 机器安装、默认 Home、冷启动、投屏 fallback、APK OTA 均通过；恢复出厂不是产品承诺或有工厂重装流程 | 可进入小批量 factory-pilot |
| factory provisioning required | APK 可用，但默认 Home、权限或恢复出厂依赖工厂工具 | 工厂 SOP 必须包含安装、授权、设 Home、验收、重装 |
| system image preinstall required | 恢复出厂必须保留，或普通安装无法稳定授予权限/保活 | 需要固件预装或 priv-app 方案 |
| vendor API required | 投屏、日志、系统 OTA、硬件控制需要系统接口才能达标 | 进入 vendor service 联调，不再按普通 APK 承诺 |
| blocked | 安装失败、默认 Home 不能持久、恢复出厂无法处理、投屏无可接受 fallback、无日志路径 | 不扩大出货范围 |

结构化分类入口：

```text
docs\ops\templates\android-tv-vendor-system-permission.template.json
scripts\android-tv-classify-vendor-permission.ps1
```

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\android-tv-classify-vendor-permission.ps1 -FeedbackPath docs\ops\templates\android-tv-vendor-system-permission.template.json
```

脚本输出：

```text
vendor-permission-classification.json
summary.txt
```

分类结果必须进入 `docs/testing/2026-06-30-android-tv-production-readiness.md` 的 dated evidence note。返回 `INCOMPLETE` 时不能调整生产 readiness gate；返回 `blocked` 时不得扩大出货范围。

## 12. 证据采集要求

我方提供只读采集脚本：

```powershell
scripts\android-tv-capture-production-readiness.ps1
```

工厂或本地测试每次验证需返回：

```text
device-info.txt
home-resolve.txt
package-openclaw.txt
window-focus.txt
activity-focus.txt
meminfo-openclaw.txt
meminfo-lebo.txt
process-list.txt
crash-logcat.txt
ui.xml
screenshot.png
summary.txt
```

无 ADB 场景下，厂商必须提供等价的日志导出包，至少包含：

```text
system logcat
kernel/dmesg
ANR traces
tombstone/native crash
OpenClaw APK log
Lebo/vendor casting log
OTA download/install log
firmware version and build fingerprint
```
