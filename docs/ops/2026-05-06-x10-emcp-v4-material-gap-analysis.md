# X10_EMCP_V4.0 原件与系统适配缺口分析

日期：2026-05-06

## 1. 结论

桌面上的 `X10_EMCP_V4.0生产资料` 是一套硬件生产资料包，能确认主板核心器件、接口轮廓和生产贴片信息，但还不能支撑 Android TV 系统级适配。

已能确认的硬件基础：

- 主控：`RK3128_BGA316-14X14_0.65`
- 内存/存储：`EMCP_LPDDR2 1+4G H9TP32A8JDAC BGA162`
- 无线：BOM 写 `W800CS（W8800DCS)`，方框图写 `8723DS`
- PCB：`GK_X10_EMCP_V4.0 4层 FR4板 1.2MM 90*60MM`
- 外设：USB、SIM、IR 接收、天线、FPC/卧贴连接器、4G 模块装配项

最大红点：

- `W800CS/W8800DCS` 与 `8723DS` 无线型号口径冲突，必须让厂家确认实际贴片模块、芯片方案、驱动包、固件文件和 SDIO/UART 连接。
- 这套资料没有 Android 固件、BSP、kernel config、device tree、bootloader、刷机 SOP、系统签名、SELinux、vendor service、音频/投屏/OTA 接口资料。没有这些，Android 端只能继续按普通 APK baseline 做，不能承诺系统级助手体验。

## 2. 原件清单

| 原件 | 类型 | 已确认内容 | 可用于 | 不能替代 |
| --- | --- | --- | --- | --- |
| `GK_X10_EMCP_V4.0 SCH.pdf` | 原理图 | RK3128、电源、USB、IR、eMCP 等电路页 | 硬件接口核对、问题定位辅助 | BSP、驱动、系统权限、固件 |
| `GK_X10_EMCP_V4.0-04（6P 1.0) （W800CS).xlsx` | BOM | RK3128、H9TP32A8JDAC、W800CS/W8800DCS、USB、SIM、IR、4G 装配项 | 核心器件确认、供应链追料 | 实际系统镜像、驱动版本、接口文档 |
| `GK_X10_EMCP_V4.0方框图.pdf` | 系统方框图 | HDMI、LVDS、USB OTG/HOST、Wi-Fi、GMAC/EMAC、SPDIF、SmartCard、12V/5V | 系统能力轮廓判断 | device tree、pinmux、内核配置 |
| `GK_X10_EMCP_V4.0位号.pdf` | 位号图 | 元件位号和布局位置 | 生产/维修定位 | 软件适配资料 |
| `GK_X10_EMCP_V4.0坐标.xlsx` | 贴片坐标 | 384 行坐标，含 U4、U7、U60 等 | SMT 生产、贴片校验 | Android 系统资料 |

## 3. 公开资料交叉验证

公开资料只能佐证“器件大致能力”，不能替代厂家交付资料。

| 对象 | 公开资料可佐证 | 对项目的含义 | 来源 |
| --- | --- | --- | --- |
| RK3128 | RK3128 面向 smart TV-Box，四核 Cortex-A7，Mali400 MP2，支持 1080P H.265 TV-Box 场景 | X10 用 RK3128 做低端 Android TV/盒子类产品是合理的，但性能边界偏低 | [Rockchip RK3128 Datasheet](https://www.rockchips.net/wp-content/uploads/2025/03/Rockchip-RK3128-Datasheet-V1.2-20170720.pdf) |
| RK3128 接口 | 公开 datasheet 显示 HDMI、SDIO、USB OTG/HOST、GMAC、SPDIF、LVDS、I2S/PCM 等模块 | 原件方框图里的主要接口方向能对上，但仍需要板级 pinmux/device tree | [Rockchip RK3128 Datasheet](https://www.rockchips.net/wp-content/uploads/2025/03/Rockchip-RK3128-Datasheet-V1.2-20170720.pdf) |
| RK3128 Android 构建 | 公开 RK3128 开发指南包含 `lunch rk3128_box-userdebug`、`device/rockchip/rk312x/build-rk3128_box.sh`、生成 `rockdev/Image-RK3128_box` 等流程 | 真正适配需要 SDK/BSP 和板级配置，不能只靠 BOM/原理图 | [XPI3128 Android Software Develop Guide](https://file.geniatech.com/download/xpi/xpi3128/XPI3128_Android_Software_Develop_Guide_En_v1.0.pdf) |
| Rockchip 刷机 | RK3128 公开开发板资料提到 Windows 侧 AndroidTool/RKDevelopTool，Linux 侧 `upgrade_tool`、`rkflashtool`、`rkflashkit` | 必须向厂家拿测试固件、刷机工具、驱动、回滚流程 | [Firefly RK3128 Firmware upgrade](https://wiki.t-firefly.com/en/Firefly-RK3128/Flash_Image.html) |
| W800CS | 公开规格写 W800CS 是 2.4GHz Wi-Fi + BT 模块，Wi-Fi 走 SDIO 2.0，BT 走 UART | 如果实际贴片是 W800CS，Android 侧要拿对应驱动、固件、BT UART 配置 | [W800CS Module Specification](https://armdesigner.com/download/W800CS_Module_Specification.pdf) |
| RTL8723DS | 公开 RTL8723DS 资料写 802.11b/g/n 1T1R WLAN + BT4.2，SDIO host interface + HS-UART | 如果方框图 `8723DS` 才是实物，则与 BOM 冲突，驱动和蓝牙栈都要按 Realtek 方案核对 | [RTL8723DS-CG Datasheet](https://edit.wpgdadawant.com/uploads/news_file/program/2020/35758/tech_files/00011175-RTL8723DS-CG_DataSheet_0.93_20171026_170209.pdf) |
| H9TP32A8JDAC | 第三方库存页能查到 SK Hynix、Memory、BGA；BOM 写 `LPDDR2 1+4G BGA162` | 只能证明料号存在，不能证明板级 DDR/eMMC 初始化参数；仍需厂家给 datasheet 和 DDR init | [Jotrin H9TP32A8JDACPR-KGM](https://www.jotrin.com/product/parts/H9TP32A8JDACPR_KGM) |

## 4. P0 必须补齐

| 缺口 | 需要厂家提供 | 为什么缺 |
| --- | --- | --- |
| 实际板型与系统基线 | 产品型号、板号、硬件版本、Android 版本、API level、是否 Android TV/GMS、固件版本 | 原件只有生产资料，没有系统软件身份 |
| RK3128 BSP/SDK | Android SDK/BSP、kernel source、kernel config、device tree、U-Boot、resource.img、boot.img、recovery.img、build script | 公开资料只能说明 RK3128 有标准开发流程，不能替代这块 X10 板的配置 |
| 可刷固件 | `update.img` 或分区镜像、刷机工具、USB 驱动、Maskrom/Loader 进入方式、回滚 SOP | 没有可控固件就无法稳定联调系统能力 |
| 无线方案确认 | 确认实物到底是 `W800CS/W8800DCS` 还是 `8723DS`；提供模块 datasheet、Wi-Fi driver、BT driver、firmware/NVRAM、SDIO/UART 端口配置、天线配置 | BOM 与方框图冲突，这是当前最大硬件口径风险 |
| eMCP/内存初始化 | H9TP32A8JDAC 官方规格、DDR init 参数、eMMC 分区表、boot partition 配置、量产烧录参数 | BOM 只能说明 1+4G 料号，不能让系统启动 |
| 权限与系统身份 | 平台签名、priv-app 白名单、权限白名单、预装路径、SELinux policy 增量 | 系统级助手、投屏、媒体控制、OTA、诊断都可能被权限挡住 |
| vendor service | 已有系统服务列表、AIDL/JAR/AAR、调用权限、错误码、sample APK | 没有接口就只能做公开 Android APK 能力 |
| 音频链路 | MIC 数量/位置、Audio HAL 路由、AEC/NS/AGC/VAD、播放回采、唤醒事件、cleaned audio 输出 | 播放中唤醒和 AI 助手入口必须依赖系统/音频资料 |
| 媒体控制 | MediaSession 兼容、媒体按键派发、前台 App/播放 owner 检测方式、重点 App 兼容矩阵 | 跨 App 暂停/继续/打开内容不是普通首页 UI 能保证的 |
| 投屏能力 | 是否支持 Miracast/Wi-Fi Display sink、Wi-Fi P2P、厂商投屏服务、DLNA 多播策略、设备名配置、投屏日志 | APK 可做 DLNA 媒体接收，但安卓手机镜像/系统级投屏要系统支持 |
| OTA 与诊断 | OTA 机制、升级状态接口、日志导出、crash/ANR/tombstone、网络/存储/温度健康状态 | home 后台要做版本更新分发管理，设备侧必须能上报和执行 |
| 遥控器/按键 | IR/蓝牙/2.4G 类型、keycode 映射、Home/Back/Menu/语音键策略 | 首页焦点、设置入口、语音入口都依赖实际按键行为 |

## 5. P1/P2 建议补齐

| 优先级 | 缺口 | 需要厂家提供 |
| --- | --- | --- |
| P1 | 接插件定义 | FPC1、CON1/2/6/7/8、SIM、USB、天线座的完整 pin definition 和外接模组说明 |
| P1 | 4G 模块 | 具体 4G 模块型号、驱动、拨号方式、天线方案、认证资料、是否量产默认装配 |
| P1 | 工厂测试 | Factory test APK/工具、老化测试报告、Wi-Fi/BT/IR/USB/SIM/4G 测试项 |
| P1 | 生产包补充 | Gerber、NC drill、钢网、Gerber 版本号、装配图、坐标单位/原点说明、BOM 替代料规则 |
| P1 | 性能与稳定性 | CPU/GPU 频率策略、内存压力边界、开机耗时、温度/电源稳定性报告 |
| P2 | 认证资料 | Wi-Fi/BT/4G 模块认证、CE/FCC/SRRC/ROHS 等与目标市场相关资料 |
| P2 | 结构资料 | 外壳、按键、红外窗口、天线位置、散热、喇叭/麦克风结构图 |
| P2 | 已知问题 | 固件 known issues、不可支持能力、规避方式、计划修复版本 |

## 6. 给厂家的最短追料清单

如果只发一版追料邮件，建议直接要下面这些：

| 序号 | 资料 | 验收标准 |
| --- | --- | --- |
| 1 | X10 当前量产/工程固件包和刷机 SOP | 我方能独立刷入、回滚、抓日志 |
| 2 | X10 对应 RK3128 Android BSP/SDK | 包含 device tree、kernel config、U-Boot、build script |
| 3 | 无线模块确认说明 | 明确 `W800CS/W8800DCS` 与 `8723DS` 谁是实物，附驱动/固件 |
| 4 | H9TP32A8JDAC 内存/存储初始化资料 | DDR init、eMMC 分区、boot 配置可用于构建/刷机 |
| 5 | 系统权限和预装策略 | 明确普通 APK、priv-app、platform signed app 各自能力 |
| 6 | 投屏、音频、媒体、OTA 的 vendor 接口 | 有 AIDL/JAR/AAR、接口文档、错误码、sample APK |
| 7 | 日志和诊断 SOP | 无 adb 场景也能导出系统/投屏/音频/OTA 相关日志 |
| 8 | 工厂测试和量产测试资料 | 能验证 Wi-Fi/BT/IR/USB/SIM/4G/音频/存储基础质量 |

## 7. 对当前 Android TV 开发的影响

| 厂商补料状态 | Android TV 端可做范围 |
| --- | --- |
| 只有当前 X10 生产资料 | 可继续普通 APK baseline：首页、runtime-manifest、APP 资源、广告展示、DLNA 媒体接收、设置跳转 |
| 补齐 P0 系统资料 | 可进入系统级语音、媒体控制、投屏、OTA、诊断联调 |
| 补齐 P1/P2 资料 | 可做量产稳定性、海外认证、硬件体验和售后闭环 |

