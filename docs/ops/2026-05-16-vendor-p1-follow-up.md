# Android TV Vendor P1 Follow-Up

日期：2026-05-16

适用范围：X10 / Android TV 低端板，当前 APK baseline 已可跑首页、APP 位、应用管理、运行状态、DLNA 媒体接收、乐播兜底和 best-effort 后台清理。

## 1. 当前 APP 侧已验证边界

| 能力 | 当前状态 | 证据 | 后续归属 |
| --- | --- | --- | --- |
| 首页与 APP 位 | 已有可验收 baseline，包含紧凑 APP 位和 `+` 应用管理入口 | `openclaw-tv-p1-acceptance-debug-20260516-131509.apk` 已装机 smoke | APP |
| 应用管理 | `+` 可打开列表；可显示已安装应用、USB/本机 APK、安装/升级/打开/长按卸载提示 | `openclaw-p1-app-management-ui-after-ok-20260516-130926.xml` | APP |
| 自建 DLNA | 能在 `wlan0` 启动，加入 SSDP 多播并发布 `description.xml` | `OpenClawCast` 日志：`192.168.1.2`, `239.255.255.250:1900` | APP + 网络环境 |
| 乐播兜底 | `AirPlayService` 可后台轻量拉起，保持苹果/小米镜像入口的可见性 | `Lebo service-only start result=true`，Lebo PSS 约 `27011 KB` | APP + 乐播 |
| 回首页清理 | 通过公开 `killBackgroundProcesses` best-effort 清理播放/商店候选包 | `OpenClawTrim` 日志显示尝试 19 个候选包 | APP |
| 开机自启 | Manifest 和 receiver 已具备；quickboot receiver smoke 可拉起首页 | 标准 `BOOT_COMPLETED` 不能由 shell 伪造，真实重启待验 | APP + 系统策略 |

结论：当前 APK 已能作为普通 Android TV APK baseline 验收。下面这些能力不能靠普通 APK 单独承诺，需要 vendor 给系统接口或固件能力。

## 2. 本轮 P1 必追资料

| 优先级 | 资料/接口 | 厂商需给到 | 为什么现在需要 |
| --- | --- | --- | --- |
| P0 | 实际无线模块确认 | 明确实物是 `W800CS/W8800DCS` 还是 `8723DS`，附驱动、固件、SDIO/UART 配置和天线说明 | 当前 BOM 与方框图冲突；直接影响投屏、Wi-Fi、蓝牙、网络稳定性归因 |
| P0 | 当前工程固件与刷机 SOP | 固件包、刷机工具、驱动、Maskrom/Loader 进入方式、回滚步骤 | 没有可控固件就无法做真实开机、自启、投屏、系统服务联调 |
| P0 | 系统身份策略 | 平台签名、priv-app 路径、权限白名单、SELinux policy 增量、预装/升级策略 | 进程冻结、系统投屏、OTA、诊断、硬件控制都可能被权限挡住 |
| P0 | 投屏系统能力 | Miracast/Wi-Fi Display sink、Wi-Fi P2P、AirPlay sink、厂商投屏服务入口、设备名策略、投屏日志路径 | 普通 APK 只能做 DLNA 媒体接收；苹果/小米镜像目前只能乐播兜底 |
| P0 | DLNA/UPnP 网络策略 | 多播是否被系统/路由限制、SSDP 端口策略、设备名来源和可修改方式 | APP 已证明 DLNA 服务能起；手机发现失败需要网络/系统侧证据 |
| P1 | OTA 机制与状态 API | OTA 类型、下载/校验/安装/失败/回滚状态、调用接口、sample | home 后台要做版本更新分发，设备侧必须能执行和回报状态 |
| P1 | 无 adb 日志导出 SOP | logcat、dmesg、tombstone、ANR、投屏、音频、OTA 专项日志导出方式 | 现场售后不能依赖 adb；投屏失败必须可追踪 |
| P1 | 投影硬件控制 API | 对焦、梯形校正、亮度、色彩模式、输入源、电源、温度、风扇、光机寿命接口 | 首页和语音后续要控制投影硬件，目前只能跳系统设置 |
| P1 | 工厂测试和老化资料 | Factory test APK/工具、Wi-Fi/BT/IR/USB/SIM/4G/音频/存储测试项、老化报告 | 低端板稳定性和批量质量需要可复核证据 |

## 3. 可以直接发给厂商的短版话术

请补充 X10 当前 Android TV 工程联调资料。我们现在的普通 APK baseline 已能运行首页、应用入口、应用管理、DLNA 媒体接收和乐播兜底，但系统级投屏、开机策略、OTA、日志诊断、投影硬件控制和低层资源管理需要厂商资料才能继续。

请优先提供：

1. 实际 Wi-Fi/BT 模块确认：`W800CS/W8800DCS` 还是 `8723DS`，以及对应驱动、固件、SDIO/UART 配置。
2. 当前工程固件、刷机工具、刷机 SOP、回滚 SOP。
3. 平台签名、priv-app、权限白名单、SELinux policy 和预装升级策略。
4. Miracast / Wi-Fi Display / AirPlay sink 能力说明、vendor service 或系统入口、设备名策略、投屏日志路径。
5. DLNA/UPnP 多播策略，是否限制 `239.255.255.250:1900`，设备名如何设置。
6. OTA 机制和状态 API，包含下载、校验、安装、失败、回滚。
7. 无 adb 场景日志导出 SOP，至少覆盖 logcat、tombstone、ANR、投屏、音频、OTA。
8. 投影硬件 API：对焦、梯形、亮度、输入源、电源、温度、风扇。
9. Factory test APK/工具和老化测试报告。

每个接口请同时提供接口文件或 SDK、方法文档、错误码、demo APK 或 sample client、对应测试固件、以及能证明接口在目标板运行的日志。

## 4. 升级为开发就绪的验收门槛

系统级能力只在同时满足以下条件后，从 `blocked` 改为 `development-ready`：

| 验收项 | 通过标准 |
| --- | --- |
| 接口实体 | 有 AIDL/JAR/AAR/SDK、系统 Intent 契约或可调用 service 名称 |
| 文档 | 有方法、参数、返回值、权限、错误码、超时和生命周期说明 |
| 固件 | 有明确版本号的测试固件，能刷入、回滚、复现 |
| 示例 | 有 demo APK、sample client 或命令行调用样例 |
| 日志 | 有成功和失败日志，能证明接口在目标板上真实运行 |
| 权限 | 明确普通 APK、priv-app、platform-signed app 各自可调能力 |
| 降级 | 不支持时有明确错误码或状态，而不是静默失败 |

## 5. 当前不可承诺项

| 项目 | 当前结论 |
| --- | --- |
| 自建 APK 完美支持苹果/小米镜像 | 不承诺。普通 APK 已有 DLNA 媒体接收，镜像仍依赖系统或乐播兜底。 |
| 冻结/强杀系统包 | 不承诺。普通 APK 只能 best-effort `killBackgroundProcesses`。 |
| 无系统权限 OTA | 不承诺。需要 vendor OTA 机制和状态 API。 |
| 无 adb 售后日志闭环 | 不承诺。需要 vendor 日志导出 SOP 或系统诊断接口。 |
| 对焦/梯形/亮度/风扇/温度控制 | 不承诺。需要投影硬件 vendor API。 |

