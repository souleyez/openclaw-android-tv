# Android TV 线程交接文档

日期：2026-05-12

## 1. 接手入口

下一线程直接从本文件开始，不需要重读完整聊天记录。

当前主线只做 `openclaw-android-tv` 的 Android TV APK。`home` 是控制面和内容管理面，负责不同产品线的内容管理能力，包括安卓 TV、声临等产品，但不做运营观测。Android TV 端只消费 home 下发的 runtime 数据，不在 TV 端扩展后台能力。

当前仓库：

| 项 | 路径 |
| --- | --- |
| Android TV 仓库 | `C:\Users\soulzyn\Desktop\openclaw-android-tv` |
| Kotlin APK 工程 | `C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin` |
| home 仓库 | `C:\Users\soulzyn\Desktop\codex\home` |
| X10 原件目录 | `C:\Users\soulzyn\Desktop\X10_EMCP_V4.0生产资料` |

## 2. 已锁定产品边界

| 主题 | 当前决策 |
| --- | --- |
| 主线产品 | 后续主线先集中 Android TV。 |
| home 角色 | home 要具备控制面和内容管理能力，覆盖不同产品线；不要做运营观测后台。 |
| TV 运行时 | Android TV 只看一套 `resource-session`。 |
| App 分发 | App 分发信息只走 `GET /api/me/runtime-manifest`。 |
| 首页广告 | 广告不新开接口，不回塞 `tv-home-config`，统一走 `runtime-manifest.adSlots[]`。 |
| 广告布局 | TV 端只认固定 `slotId`；尺寸、布局、渲染行为由 TV 本地常量决定。 |
| 首页刷新 | 首页没必要频繁刷新，启动时加载一次即可，运行中不要持续刷新 runtime-manifest。 |
| 支付信息 | payment 只下发摘要态，不下发敏感或原始账号字段。 |
| 视觉样稿 | 用户提供的两张暗色/亮色图是 APK 首页样稿，不是广告稿；当前出 APK 优先按暗色版。 |

## 3. 当前 APK 状态

| 模块 | 状态 |
| --- | --- |
| 首页视觉 | 已向暗色样稿靠拢，包含在线首页、离线 Wi-Fi 候选页两种版式。 |
| AI 首屏区 | 支持 AI 人物、对话气泡、状态提示；人物目标是更日系、更可爱的 10 岁小姑娘风格。 |
| 首页广告 | 已接入多图广告能力，放在主背景正中间、人物手左边，离线和在线都可见。 |
| 广告轮播 | 多张 creative 在启动加载后本地轮流显示；运行中不再更新广告列表。 |
| Wi-Fi 页面 | 离线 Wi-Fi 候选界面已放大，按 5-10 个候选网络的常见情况设计。 |
| 投屏入口 | 首屏内置投屏待机卡片，支持普通 APK baseline 的 DLNA 媒体接收。 |
| 系统级投屏 | Miracast / Wi-Fi Display / AirPlay 类能力不能靠普通 APK 承诺，需要系统或厂商服务资料。 |
| App 资源 | 首页推荐 App 来自 `runtime-manifest`，不依赖本地硬编码 catalog。 |
| 下载/安装 | 已接入 DownloadManager 跟踪、校验和安装提示的 UI 状态。 |

## 4. 当前未提交改动

当前工作区仍是脏的，下一线程不要直接重置。先阅读改动，再决定是否提交或继续收口。

| 状态 | 路径 | 说明 |
| --- | --- | --- |
| Modified | `apps/android-tv-client-kotlin/settings.gradle.kts` | 新增 `:feature:cast` 模块。 |
| Modified | `apps/android-tv-client-kotlin/feature/home/build.gradle.kts` | 首页模块依赖 cast 模块。 |
| Modified | `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt` | 首页视觉、广告展示、Wi-Fi 面板、DLNA 状态、焦点流转、广告点击。 |
| Modified | `apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt` | runtime-manifest 启动锁定、heroAds、多图广告、DLNA 状态汇总。 |
| Modified | `apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml` | 首页布局加入广告卡、Wi-Fi 大面板、投屏待机区。 |
| Modified | `apps/android-tv-client-kotlin/feature/home/src/test/java/com/openclaw/tv/feature/home/HomeViewModelTest.kt` | 覆盖广告、启动锁定、离线复用、resource-session 等用例。 |
| Modified | `docs/ops/2026-04-15-rk3128-board-engineer-worklist.md` | 增加系统级适配资料表引用。 |
| Untracked | `apps/android-tv-client-kotlin/feature/cast/` | 新增轻量 DLNA renderer、协议处理、播放 Activity 和测试。 |
| Untracked | `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable/bg_cast_protocol_chip.xml` | 投屏协议 chip 背景。 |
| Untracked | `apps/android-tv-client-kotlin/feature/home/src/main/res/drawable/bg_cast_standby_card.xml` | 投屏待机卡片背景。 |
| Untracked | `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md` | 系统级适配厂商资料总表。 |
| Untracked | `docs/ops/2026-05-06-x10-emcp-v4-material-gap-analysis.md` | X10 原件与系统适配缺口分析。 |
| Untracked | `docs/ops/2026-05-06-x10-p0-required-materials.xlsx` | P0 必须补齐资料 Excel 追料表。 |
| Untracked | `docs/ops/2026-05-12-android-tv-thread-handoff.md` | 本交接文档。 |

## 5. 关键代码点

| 主题 | 代码位置 | 接手说明 |
| --- | --- | --- |
| 首页状态模型 | `feature/home/.../HomeViewModel.kt` | `HomeUiState.heroAds` 是首页广告入口。 |
| runtime-manifest 启动锁定 | `HomeViewModel.lockStartupRuntimeManifest()` | 启动后锁定首份非 fallback manifest，避免运行中频繁刷新占资源。 |
| 广告可见 manifest | `HomeViewModel.visibleRuntimeManifest()` | UI 使用启动锁定后的 manifest；后续 store 更新不应改变首页广告。 |
| 多图广告渲染 | `HomeFragment` 的 hero ad 相关方法 | 广告位置是首页主背景中间、人物左侧；点击支持 deeplink/url。 |
| Wi-Fi 大候选页 | `HomeFragment` + `fragment_home.xml` | 离线模式主屏给 Wi-Fi 候选，不要再压缩成小卡片。 |
| DLNA 接收 | `feature/cast` | 轻量实现，适合 APK baseline；不是系统级镜像投屏。 |
| 投屏状态汇总 | `HomeViewModel.bindCastReceiverState()` | 首页只展示 receiver 状态和错误摘要。 |

## 6. home 契约来源

下一线程若要核对服务端契约，优先看 home 侧这些文档/测试：

| 文件 | 作用 |
| --- | --- |
| `C:\Users\soulzyn\Desktop\codex\home\docs\architecture\2026-04-20-openclaw-tv-runtime-contract.md` | TV runtime 总契约。 |
| `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api\src\lib\control-plane-tv-contract.ts` | home 下发 DTO/契约口径。 |
| `C:\Users\soulzyn\Desktop\codex\home\apps\platform-api\test\tv-contract.test.ts` | 契约测试。 |
| `C:\Users\soulzyn\Desktop\codex\home\docs\plans\2026-04-20-home-openclaw-tv-runtime-alignment.md` | home 侧执行计划。 |
| `C:\Users\soulzyn\Desktop\codex\home\docs\2026-04-20-android-tv-runtime-handoff.md` | home/Android TV 总交接边界。 |

广告字段口径：

| 层级 | 字段 |
| --- | --- |
| manifest | `adSlots[]` |
| slot | `slotId`, `enabled`, `creatives[]` |
| creative | `creativeId`, `mediaType`, `assetUrl`, `altText`, `clickActionType`, `clickActionValue`, `startsAt`, `endsAt` |

TV 端规则：

| 规则 | 说明 |
| --- | --- |
| `adSlots` 必须是数组 | 可为空。 |
| 未知 `slotId` | TV 忽略。 |
| 缺 slot 或无有效 creative | TV 不报错，直接不渲染。 |
| home 不下布局 | 不下坐标、宽高、栅格顺序。 |
| creative 内容 | 只下发素材和点击动作。 |

## 7. X10 硬件资料状态

桌面 `X10_EMCP_V4.0生产资料` 已核对过。这是一套生产制造资料，不是系统适配资料。

已确认：

| 项 | 内容 |
| --- | --- |
| 主控 | `RK3128_BGA316-14X14_0.65` |
| 内存/存储 | `EMCP_LPDDR2 1+4G H9TP32A8JDAC BGA162` |
| 无线 BOM | `WIFI(2.4G)+BT MODEM W800CS（W8800DCS)` |
| 无线方框图 | `8723DS` |
| PCB | `GK_X10_EMCP_V4.0 4层 FR4板 1.2MM 90*60MM` |
| 外设 | USB、SIM、IR、天线、FPC、4G 模块装配项 |

最大红点：

| 问题 | 影响 |
| --- | --- |
| BOM 写 `W800CS/W8800DCS`，方框图写 `8723DS` | Wi-Fi/BT 实物模块不明确，驱动、固件、SDIO/UART、蓝牙栈都无法确定。 |
| 只有生产资料，无 BSP/固件 | 无法启动系统级适配，只能做普通 APK baseline。 |

已落文档：

| 文件 | 说明 |
| --- | --- |
| `docs/ops/2026-05-06-system-level-adaptation-vendor-materials.md` | 系统级适配厂商资料总表。 |
| `docs/ops/2026-05-06-x10-emcp-v4-material-gap-analysis.md` | X10 原件和公开资料交叉核对。 |
| `docs/ops/2026-05-06-x10-p0-required-materials.xlsx` | 可发厂家的 P0 追料 Excel。 |

## 8. 验证状态

本交接文档生成时未重新跑 Gradle 测试。下一线程接手后建议先跑下面三步：

```powershell
cd C:\Users\soulzyn\Desktop\openclaw-android-tv\apps\android-tv-client-kotlin
.\gradlew.bat :feature:home:testDebugUnitTest
.\gradlew.bat :feature:cast:testDebugUnitTest
.\gradlew.bat assembleDebug
```

如果模拟器可用，继续做实机/模拟器 smoke：

```powershell
.\gradlew.bat :app:installDebug
```

需要重点人工验证：

| 验证项 | 通过标准 |
| --- | --- |
| 暗色在线首页 | 人物、对话、App 入口、底部功能区、广告位符合暗色样稿方向。 |
| 离线 Wi-Fi 页 | Wi-Fi 列表能容纳 5-10 个候选，焦点和按钮可用。 |
| 广告位 | 运行中不重新拉取 manifest；多张图只在本地轮播。 |
| 广告点击 | deeplink/url 动作合法时可打开，非法值不崩。 |
| DLNA | 手机 DLNA 推送时 TV 能被发现并打开播放页。 |
| 焦点 | 遥控器上下左右在在线、离线、投屏、App 列表间不丢焦。 |

## 9. 下一线程建议顺序

1. 先跑 `git status --short`，确认没有用户新增改动。
2. 跑 `:feature:home:testDebugUnitTest` 和 `:feature:cast:testDebugUnitTest`。
3. 如测试失败，先收测试和编译问题，不要扩新功能。
4. 安装到本机模拟器，按暗色样稿看首页和离线 Wi-Fi 页。
5. 若首页视觉偏差明显，优先调 XML 和 drawable，不要改契约。
6. 若 DLNA 编译或运行有问题，先保证首页不被 cast 模块拖崩。
7. 收成一版可提交改动，建议提交信息为 `Implement Android TV home ads and DLNA baseline` 或拆成 `Add DLNA cast baseline`、`Align Android TV home runtime ads` 两个提交。

## 10. 不要踩的坑

| 坑 | 处理方式 |
| --- | --- |
| 把用户给的首页样稿当广告稿 | 不要。那两张图是 APK 首页视觉方向。 |
| 再把广告塞回 `tv-home-config` | 不要。广告只走 `runtime-manifest.adSlots[]`。 |
| 运行中频繁刷新首页广告 | 不要。启动加载一次，后续本地轮播。 |
| 普通 APK 承诺系统级投屏 | 不要。DLNA 可以做，Miracast/AirPlay/系统镜像需要厂商系统能力。 |
| 用 X10 生产资料替代 BSP | 不要。生产资料只能辅助硬件判断，系统适配必须追 P0。 |
| 直接清理脏文件 | 不要。Windows 本机清理必须用 `Safe-RemoveToBackup.ps1` 备份优先。 |

## 11. 新线程第一句话建议

可直接对新线程说：

```text
从 C:\Users\soulzyn\Desktop\openclaw-android-tv\docs\ops\2026-05-12-android-tv-thread-handoff.md 接着做。先核对当前工作区，跑 home/cast 单测和 assembleDebug，再按暗色首页样稿收一版可提交 APK baseline。
```

