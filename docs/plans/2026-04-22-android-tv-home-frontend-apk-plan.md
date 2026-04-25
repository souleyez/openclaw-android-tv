# Android TV Frontend APK Plan Aligned With Current Home Runtime

> Status
> This is the active frontend APK execution plan for `openclaw-android-tv`.
> Follow these documents first when this plan conflicts with older client notes:
> - [2026-04-22-home-openclaw-latest-guidance.md](C:/Users/soulzyn/Desktop/codex/home/docs/2026-04-22-home-openclaw-latest-guidance.md)
> - [2026-04-20-openclaw-tv-runtime-contract.md](C:/Users/soulzyn/Desktop/codex/home/docs/architecture/2026-04-20-openclaw-tv-runtime-contract.md)
> - [2026-04-20-tv-client-home-app-pool-lease-alignment-plan.md](C:/Users/soulzyn/Desktop/openclaw-android-tv/docs/plans/2026-04-20-tv-client-home-app-pool-lease-alignment-plan.md)

Date: 2026-04-22

## Goal

基于当前已经落地的 `home` 运行时契约，继续收口 Android TV 前端 APK，使其满足以下目标：

- 首页按暗色样稿作为主发布版式
- 同时覆盖有网与无网两种首页版式
- 首页广告只走 `runtime-manifest.adSlots`
- 广告固定落在主视觉区中部、人物左侧
- 同一启动周期内首页内容按启动快照展示，不做运行中频繁刷新
- APK 能稳定完成启动、缓存恢复、资源会话保活、下载提示与首页交互闭环

## Source Of Truth

当前前后端协作边界已经固定：

- `home` 是唯一控制面
- `home` 已提供 Android TV 所需公开运行时接口：
  - `GET /api/me/tv-home-config`
  - `GET /api/me/runtime-manifest`
  - `GET /api/me/entitlement`
  - `POST /api/client/resource-session/request`
  - `GET /api/client/resource-session/status`
  - `POST /api/client/resource-session/renew`
  - `POST /api/client/resource-session/release`
- `tv-home-config` 只负责路径、轮询节奏和开关，不承载首页内容
- `runtime-manifest` 负责：
  - 首页应用入口元数据
  - 广告位 `adSlots`
  - 广告 creative 内容和点击动作
- TV 客户端只认固定 `slotId`
- `home` 不下发布局尺寸、坐标、栅格顺序
- `adSlots` 必须是数组，可为空
- 未知 `slotId` 直接忽略
- payment 只看摘要态
- 客户端只看一套 `resource-session`

## Current Status

### 1. `home` backend reality

当前 `home` 侧已经不是“等接口”状态，运行时契约和路由已经落地，可直接作为 APK 联调基线：

- TV 契约文档已冻结
- `me` 路由已落：
  - [me.ts](/Users/soulzyn/Desktop/codex/home/apps/platform-api/src/routes/me.ts)
- `resource-session` 路由已落：
  - [client.ts](/Users/soulzyn/Desktop/codex/home/apps/platform-api/src/routes/client.ts)
- 契约测试与资源会话测试已存在：
  - [me-routes.test.ts](/Users/soulzyn/Desktop/codex/home/apps/platform-api/test/me-routes.test.ts)
  - [resource-session-routes.test.ts](/Users/soulzyn/Desktop/codex/home/apps/platform-api/test/resource-session-routes.test.ts)

这意味着 Android TV 前端后续工作的重点，不再是等待 `home` 发明新接口，而是把现有接口吃稳、吃透、吃成可出包行为。

### 2. Android TV client reality

当前 Android TV 客户端也已经不是空壳，相关模块已经成形：

- 网络与 DTO：
  - `core/network`
- 本地持久化：
  - `core/storage`
- 启动与 bootstrap：
  - `feature/bootstrap`
- 运行时资源会话：
  - `feature/runtime`
- 应用分发与下载：
  - `feature/appdelivery`
- 首页 UI：
  - `feature/home`

首页当前已经具备的能力：

- `tv-home-config` 远端读取 + 本地缓存回退
- `runtime-manifest` 远端读取 + 本地缓存回退
- entitlement 摘要读取 + 本地缓存
- `resource-session` 状态读取 + 本地缓存
- 首页应用位已转成 `runtime-manifest.apps` 驱动
- 首页广告位已转成 `runtime-manifest.adSlots` 驱动
- `home.hero` 已支持多张图片 creative
- hero 广告在一次启动内会锁定为启动期拿到的那批 creative，不在运行中跟随后续刷新替换
- 无网络态 Wi-Fi 候选列表已经扩成可占主要内容区的大面板
- 首页已有安装中、待安装、失败重试等应用状态展示
- 已具备 APK 下载完成后的安装提示衔接

关键代码落点：

- [OpenClawTvApplication.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/OpenClawTvApplication.kt)
- [ApplicationRuntimeCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/runtime/ApplicationRuntimeCoordinator.kt)
- [HomeViewModel.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt)
- [HomeFragment.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt)
- [fragment_home.xml](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml)
- [HomeRuntimeManifestRepository.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeRuntimeManifestRepository.kt)
- [ResourceSessionCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionCoordinator.kt)
- [AppDownloadCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/AppDownloadCoordinator.kt)

### 3. Verified baseline on 2026-04-22

本轮已重新验证当前客户端基线：

- `:feature:home:testDebugUnitTest` 通过
- `:app:assembleDebug` 通过

这说明当前计划应该建立在“可运行、可构建、可继续收口”的代码基线之上，而不是重新起草一套从零开始的前端路线。

### 4. 2026-04-23 frontend closeout update

本轮继续把前端 APK 收到更接近可提交样稿的状态：

- 首页启动快照策略已收口：
  - steady sync 不再反复刷新 `tv-home-config`
  - entitlement 每个活跃 session 只做一次远端读取，之后走缓存/空态
  - `runtime-manifest` 的首页可见内容已按启动期整体锁定，包含 featured apps 与 hero ads
  - `resource-session` 后台保活仍保留
- 暗色首页版式已继续对齐样稿：
  - 在线 / 离线共用暗色主视觉方向
  - hero 广告位放大并固定在主视觉中部、人物左侧
  - 离网态 Wi-Fi 候选区继续作为主内容区，单项密度压到适配 5-10 个候选网络
- hero 广告交互已补齐：
  - `none` 静默展示
  - `deeplink` 通过 `ACTION_VIEW` 拉起
  - `url` 仅允许 `http/https`
  - 空值、未知动作、不可解析 URI 均静默忽略
- 首页应用入口已补手动下载：
  - `runtime-manifest.apps` 的下载 URL、SHA、版本信息会透传到首页卡片
  - `NOT_INSTALLED / prompt` 且元数据完整时，按确定会加入后台下载队列
  - 失败态仍复用原有“按确定重试下载”链路
- 首屏 AI 交互入口已开始绑定单一 `resource-session`：
  - 首页派生 `aiEntryLabel` / `aiEntryMessage` / `aiEntryAvailable`
  - 只有在线、账号未受限、`queueStatus=granted` 且存在 model lease 时，首屏 AI 入口才视为可用
  - queued / allocating / expired / rejected / degraded / suspended / offline 都只展示本地摘要提示，不暴露 `resourceSessionId`、`priorityClass` 或 lease 细节
- hero 广告解析口径已统一：
  - 远端 manifest 与缓存 manifest 都走 `HomeAdSlotRegistry`
  - `home.hero` 固定 slot 继续由 TV 本地识别
  - 远端与缓存路径都限制首屏 hero creative 数量，避免过长广告列表影响启动页
- 已在本机 `OpenClaw_TV34` 模拟器做 APK smoke：
  - debug APK 可安装并启动主 Activity
  - 在线首屏可正常渲染
  - debug 离线预览可正常渲染 Wi-Fi 主面板
  - Wi-Fi 操作按钮已去掉系统默认紫色 tint，回到暗色本地按钮样式
- Phase 1 启动快照边界已补齐：
  - 首页 UI 统一读取 `visibleRuntimeManifest`
  - 首个非 fallback manifest 会成为本次启动的首页内容快照
  - 后续 `RuntimeManifestStore` 更新不会热替换当前首页的应用位或广告位
  - 下载状态、entitlement 摘要、`resource-session` 状态仍可继续刷新

本轮验证通过：

- `:feature:home:testDebugUnitTest`
- `:feature:appdelivery:testDebugUnitTest`
- `:feature:runtime:testDebugUnitTest`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`

## Gaps To Close Before A Submit-Ready APK

当前还没有完全收口的点，主要集中在下面几项：

> 2026-04-23 note
> A-D 已完成代码层低风险收口，但仍保留在本节作为设备验证清单；E 已完成首页门禁层的第一步绑定，后续还要接真实语音/AI 交互动作；F 仍是后续主线。

### A. 首页刷新边界还需要彻底锁死

你已经定下了首页“每次启动刷新一次，运行中不再频繁刷新”的规则。

当前代码里：

- hero 广告已经做了启动期锁定
- 但运行时主循环仍会继续刷新 config / manifest / entitlement / resource-session

所以还需要把“后台资源保活”与“首页可见内容刷新”明确拆开：

- 后台可以继续做 `resource-session` 保活、下载跟踪、必要的 entitlement 同步
- 首页可见文案、应用位、广告位、主视觉状态，应以启动快照为主
- 只有重启应用后才重新采用新的首页内容快照

### B. 暗色版样稿对齐还不是最终版

现在首页已经有暗色基础版式，也有在线 / 离线两种布局切换，但距离“按样稿出 APK”还差最后一层收边：

- 顶部品牌栏、视觉留白、边框厚度、阴影层级还需要继续精修
- 人物主视觉资源还需要最终定稿并固化为正式资源
- 有网与无网两种视觉的背景材质和层次要进一步拉开
- Wi-Fi 候选列表需要按 5-10 个网络的常见密度验证滚动、聚焦、截断和按钮区比例

### C. 首页广告展示已完成大半，但交互闭环还没做完

当前已完成：

- 多图广告列表
- 本地轮播
- 启动期锁定
- 固定位置展示

但还缺：

- `clickActionType` / `clickActionValue` 的前端执行
- `deeplink` / `url` / `none` 的本地行为约束
- 广告聚焦态、按下态、不可点击态的可视反馈

目前 `HomeFragment` 会渲染图片和文案，但还没有把 creative 点击动作真正接出去。

### D. 首页应用入口还缺“手动拉起下载”闭环

当前首页应用位已经能显示：

- 已安装
- 排队中
- 下载中
- 已暂停
- 校验中
- 待安装
- 下载失败

但对于 `NOT_INSTALLED` 的 prompt 型应用，当前交互仍偏保守，主要还是提示“设备里还没装”或等待后台下发。要让 APK 更可用，需要补：

- 明确的手动下载入口
- 明确的“下载中 / 下载完成 / 按确定安装”链路
- 安装失败后的重试入口

### E. `resource-session` 仍需和真实首页意图绑定

当前 `feature/runtime` 已经具备单一 `resource-session` 状态机，但主运行时请求时仍偏向通用会话续租。

还需要继续明确：

- 首页 AI 交互区触发时，到底请求哪类 runtime intent
- 是否需要带 `appId`
- 是否需要带 `providerScope`
- 哪些首页行为只依赖 entitlement 摘要，哪些真正依赖 granted session

这一层不收口，首页文案虽然能展示“资源状态”，但和真实 AI 交互入口之间仍然是弱连接。

### F. 真实设备联调与出包回归仍需系统化

当前代码和构建链可用，但还需要补一套稳定的设备验证闭环：

- 首次启动在线
- 首次启动离线
- 缓存恢复
- 运行中断网
- 下载完成后安装
- 资源过期 / 退化 / 恢复
- 多个 Wi-Fi 候选时的 D-pad 体验

## Frontend APK Development Plan

## Phase 1: Freeze The Startup Home Snapshot

目标：把“首页启动快照”和“后台运行时保活”从行为上彻底分开。

执行点：

- 以一次启动为单位，冻结首页可见内容快照：
  - hero 文案
  - hero 广告
  - featured apps
  - entitlement 摘要映射后的首页状态文案
- 后台继续保活：
  - `resource-session` request / poll / renew / release
  - entitlement 摘要同步
  - 下载状态跟踪
- 不把 steady loop 的 manifest 变更直接热更新到首页 UI
- 首页需要重新启动后才采用新的 manifest 内容

主要代码范围：

- [ApplicationRuntimeCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/runtime/ApplicationRuntimeCoordinator.kt)
- [HomeViewModel.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt)
- [HomeRuntimeManifestRepository.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeRuntimeManifestRepository.kt)

验收标准：

- 冷启动在线时，首页只加载一次远端首页内容
- 运行中服务端 manifest 变化不会改动当前首页展示
- 重启应用后才采用新的首页内容
- `resource-session` 仍能继续按后台节奏保活

## Phase 2: Align The Dark Homepage To The Release Mockup

目标：把当前首页 UI 从“已能工作”收成“能作为 APK 样稿出包”的暗色版。

执行点：

- 以暗色方案作为主发布版式
- 明确两种首页版式：
  - 在线首页
  - 无网络首页
- 保留你已经定下的广告位置：
  - 主背景区域
  - 正中偏左
  - 位于人物左侧
  - 在线 / 离线都可见
- 继续放大 Wi-Fi 候选区域，确保 5-10 个候选网络时依然可用
- 继续优化：
  - 标题字号
  - 卡片边距
  - 阴影层级
  - 顶部状态区
  - 角色立绘占比
  - 焦点态高亮

主要代码范围：

- [fragment_home.xml](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/res/layout/fragment_home.xml)
- `feature/home/src/main/res/drawable/*`
- [HomeFragment.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt)

验收标准：

- 在线首页和无网络首页都能稳定还原暗色版方向
- 无网络态 Wi-Fi 列表成为主视觉内容之一，而不是附属小模块
- 焦点切换不丢失，不穿透，不跳区

## Phase 3: Finish Hero Ad Interaction

目标：把首页广告从“能显示”收成“可运营、可点击、可控行为”的成品能力。

执行点：

- 继续坚持 `home.hero` 为唯一首页主视觉广告位
- 同一启动周期内只使用启动期拿到的 creatives
- 多张图片仅本地轮播，不重新拉取替换
- 增加 creative 点击动作支持：
  - `none`
  - `deeplink`
  - `url`
- 约束错误处理：
  - 不支持的动作直接忽略
  - 缺值直接不跳转
  - 不弹异常
- 为广告卡片增加聚焦态和按下态反馈

主要代码范围：

- [HomeRuntimeManifestRepository.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeRuntimeManifestRepository.kt)
- [HomeFragment.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt)

验收标准：

- `adSlots` 为空时首页不报错
- creative 无效时首页不报错
- 多广告轮播稳定
- 广告点击行为与 contract 字段完全一致

## Phase 4: Close The Featured App Delivery Loop

目标：让首页应用入口不仅能展示状态，还能把下载和安装动作闭环跑通。

执行点：

- 区分：
  - 自动后台下载型应用
  - 需要用户主动拉起的 prompt 型应用
- 为 `NOT_INSTALLED` 的 prompt 型应用提供明确下载动作
- 为失败态提供显式重试
- 保持已存在的：
  - 校验
  - `ready_to_install`
  - 系统安装提示
  - 包安装完成后状态回收
- 继续复核安装文案，避免只弹 Toast 而没有明确下一步

主要代码范围：

- [AppDownloadCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/appdelivery/src/main/java/com/openclaw/tv/feature/appdelivery/AppDownloadCoordinator.kt)
- [HomeViewModel.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt)
- [HomeFragment.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeFragment.kt)

验收标准：

- 首页应用位能从“未安装”走到“下载中”再走到“待安装”
- 下载失败可直接重试
- 安装完成后首页状态能正确恢复为已安装

## Phase 5: Bind AI Home Surface To One Resource Session Model

目标：让首页 AI 交互区与后台 `resource-session` 形成真实的一对一运行时关系。

执行点：

- 明确首页哪些操作只需要 entitlement 摘要
- 明确哪些操作必须持有 granted `resource-session`
- 把首页 AI 交互入口的 runtime intent 映射清楚：
  - 默认 lease profile
  - 是否需要 providerScope
  - 是否需要 appId
- 统一首页状态文案和资源状态来源，避免一部分来自缓存、一部分来自瞬时请求

主要代码范围：

- [ResourceSessionCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionCoordinator.kt)
- [ResourceSessionRepository.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/runtime/src/main/java/com/openclaw/tv/feature/runtime/ResourceSessionRepository.kt)
- [ApplicationRuntimeCoordinator.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/app/src/main/java/com/openclaw/tv/runtime/ApplicationRuntimeCoordinator.kt)
- [HomeViewModel.kt](/Users/soulzyn/Desktop/openclaw-android-tv/apps/android-tv-client-kotlin/feature/home/src/main/java/com/openclaw/tv/feature/home/HomeViewModel.kt)

验收标准：

- 首页状态和资源实际状态一致
- 断网、过期、退化、恢复都有明确展示
- 不再直接把内部 app-account / model-lease 细节暴露给首页交互

## Phase 6: Device Verification And APK Output

目标：形成一版可以持续回归、可以拿来出 APK 的前端验证清单。

执行点：

- 单测回归：
  - `:feature:home:testDebugUnitTest`
  - `:feature:runtime:testDebugUnitTest`
  - `:feature:appdelivery:testDebugUnitTest`
  - `:app:testDebugUnitTest`
- 构建回归：
  - `:app:assembleDebug`
- 模拟器 / 设备验证：
  - 在线冷启动
  - 离线冷启动
  - 缓存恢复
  - Wi-Fi 长列表聚焦
  - 广告多图轮播
  - 应用下载和安装
  - `resource-session` 过期与恢复
- 产出一版暗色首页样稿 APK

验收标准：

- 首页在在线 / 离线两种状态下都能完整工作
- 不依赖 debug 页面
- 不依赖本地手工改数据
- APK 可以稳定构建并用于对外样稿验证

## Non-Goals

这个前端 APK 计划明确不做这些事情：

- 不在 TV repo 内重建控制面
- 不把广告布局参数拉回客户端接口
- 不把首页做成运行中频繁刷新面板
- 不在 APK 内增加观测、监控、日志看板页面
- 不把 `model-lease` 老接口重新暴露成首页主流程

## Recommended Next Step

前端执行顺序建议如下：

1. 先收 `Phase 1`
   - 把“首页启动快照只刷新一次”从广告扩展到整个首页可见内容
2. 再收 `Phase 2`
   - 按暗色版样稿继续对齐在线 / 离线首页
3. 然后并行推进 `Phase 3` 和 `Phase 4`
   - 一个收广告交互
   - 一个收应用下载安装闭环
4. 最后收 `Phase 5` 和 `Phase 6`
   - 把 AI runtime 行为和设备验证彻底打通

## Bottom Line

当前前端 APK 的正确方向不是“继续补接口”，而是：

- 以已落地的 `home` runtime 契约为准
- 把首页内容刷新策略锁成启动快照
- 以暗色样稿收在线 / 离线首页
- 把主视觉广告位做成稳定、内容驱动、可点击的固定 slot
- 把首页应用下载安装和 `resource-session` 运行时闭环补完
- 在此基础上产出可持续回归的 Android TV APK
