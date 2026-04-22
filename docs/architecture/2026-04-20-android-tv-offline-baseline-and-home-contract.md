# OpenClaw Android TV 离线基线与 home 协同接口说明

> Warning
> This document is now a historical baseline snapshot for the Kotlin TV shell.
> The current runtime contract and latest shared guidance are now frozen in:
> - `C:\Users\soulzyn\Desktop\codex\home\docs\2026-04-22-home-openclaw-latest-guidance.md`
> - `C:\Users\soulzyn\Desktop\codex\home\docs\2026-04-20-android-tv-runtime-handoff.md`
> - `C:\Users\soulzyn\Desktop\codex\home\docs\architecture\2026-04-20-openclaw-tv-runtime-contract.md`
>
> When these documents conflict, follow the `home` documents above.
> In particular:
> - app delivery metadata now belongs to `runtime-manifest`
> - TV ad creatives now belong to `runtime-manifest`
> - `tv-home-config` should be treated as runtime path and cadence metadata only
> - the earlier route-gap analysis in this document is no longer current
> - the earlier country or region-first assumptions should no longer drive phase-1 work

## 1. 文档目的

这份文档用于明确两件事：

- Android TV Kotlin 客户端在 `home` 完全不可用时，仍然必须具备基础离线工作能力。
- `home` 需要提供哪些公网路由、接口、兼容策略和管理数据，才能支撑 Android TV 客户端稳定上线。

这是一份交接文档，主要给 `home` 线程使用。

## 2. 结论先行

当前 Android TV 客户端已经具备一部分离线降级能力，但要把“服务器完全不响应仍可正常处理大多数操作”做扎实，需要同时满足下面两层条件：

- 客户端继续坚持“本地壳优先、远端增强”的原则。
- `home` 必须补齐 Android TV 的公网接入面，尤其是 `/api/client/**`、`/api/me/**` 和 `tv-home-config`。

当前实际缺口不是单一问题，而是三个问题叠加：

- `platform-api` 虽然已经实现了大部分 `client` 路由，但公网网关没有放出 `/api/client/**`。
- Android TV 客户端依赖的 `/api/me/tv-home-config` 目前在 `home` 里没有发现实现。
- 客户端默认 `projectKey=openclaw`、`principalType=device`，而 `home` 当前的正式项目键和 principal 约定并不完全一致。

## 3. Android TV 客户端离线基线

### 3.1 离线基线的定义

这里的“离线可用”不是指完全无后端也能拥有完整智能能力，而是指：

- App 冷启动不能因为 `home` 不可用而卡死、黑屏或无法进入首页。
- 首页必须能展示本地壳、网络状态、本地入口和本机应用入口。
- 用户仍然能完成大多数本地操作。
- 只有模型对话、租约、远端策略、远端节目位、远端 OTA 信息属于可降级能力。

### 3.2 离线状态下客户端必须可做的事

- 正常进入首页壳。
- 正常显示联网状态或 Wi-Fi 引导。
- 打开系统 Wi-Fi 设置。
- 打开系统设置。
- 打开投屏入口。
- 打开本地文件入口。
- 查看本机应用列表。
- 启动本机已安装应用。
- 使用最近一次缓存的首页配置。
- 在没有缓存时回退到内置默认首页配置。

### 3.3 离线状态下允许降级的事

- 模型对话不可用。
- 模型租约申请、续约、释放不可用。
- 远端推荐位、区域化内容位不刷新。
- OTA 最新版本信息不刷新。
- 项目策略不刷新。

### 3.4 客户端当前已具备的离线能力

当前 Kotlin 客户端已经具备以下基础：

- `TvHomeRepository` 采用 `remote -> cache -> fallback` 链路。
- `TvHomeRepository` 远端首页配置请求超时为 `1500ms`。
- `SessionStore`、`LeaseStore`、`DeviceIdentityStore`、`TvHomeConfigStore` 已有本地持久化。
- `BootstrapRuntime` 已把部分 `404` 视为运行时不可用，而不是致命错误。
- 首页已有离线模式，可直接展开 Wi-Fi 引导和本地快捷入口。

这意味着客户端架构方向是对的，但后端协同面还没有完全接上。

## 4. 当前现状核对

### 4.1 Android TV 客户端当前实际调用的接口

客户端当前会调用以下路径：

- `POST /api/client/bootstrap/auth`
- `GET /api/me/tv-home-config?countryCode=...&regionCode=...`
- `GET /api/client/policy?projectKey=...`
- `GET /api/client/releases/latest?projectKey=...&channel=...`
- `POST /api/client/model-lease`
- `GET /api/client/model-lease/status`
- `POST /api/client/model-lease/renew`
- `POST /api/client/model-lease/release`

### 4.2 home 当前已经有的能力

`home` 的 `platform-api` 里已经实现了以下 Android TV 客户端路由：

- `POST /client/bootstrap/auth`
- `GET /client/policy`
- `GET /client/releases/latest`
- `POST /client/model-lease`
- `GET /client/model-lease/status`
- `POST /client/model-lease/renew`
- `POST /client/model-lease/release`

也就是说，控制面主体逻辑并不是空的。

### 4.3 home 当前确认存在的缺口

已确认存在这些缺口：

- 当前统一公网网关没有放出 `/api/client/**`。
- 当前统一公网网关没有放出 `/api/me/**`。
- `home` 代码中没有发现 `tv-home-config` 路由实现。
- Android TV 项目正式项目键是 `openclaw-android-tv`，但客户端默认值还是 `openclaw`。
- `home` 的项目别名里有 `android-tv`，但没有 `openclaw`。
- 客户端默认 `principalType=device`，而 `home` 的正式 principal 语义是 `phone` 或 `project_identity`。

### 4.4 这几个缺口带来的实际后果

- 客户端公网访问 `client` 路由时会得到网关级 `404`。
- 客户端首页远端配置一定拿不到。
- 不同环境如果没有手工覆盖 `projectKey`，可能会落到错误项目空间。
- `principalType=device` 目前依赖 `home` 的隐式归一化行为，不属于稳态契约。

## 5. home 必须提供的公网能力

### 5.1 必须开放的网关路由

统一公网网关必须新增转发：

- `location /api/client/ { proxy_pass http://home_platform_api; }`
- `location /api/me/ { proxy_pass http://home_platform_api; }`

这两条是 Android TV 客户端当前接入的硬前提。

如果不放出这两条，客户端即使本地逻辑正确，也只能长期运行在降级模式。

### 5.2 必须稳定提供的接口

`home` 必须把下面这些接口视为 Android TV 正式对外契约：

- `POST /api/client/bootstrap/auth`
- `GET /api/client/policy`
- `GET /api/client/releases/latest`
- `POST /api/client/model-lease`
- `GET /api/client/model-lease/status`
- `POST /api/client/model-lease/renew`
- `POST /api/client/model-lease/release`
- `GET /api/me/tv-home-config`

前七条当前已有实现，最后一条需要补。

## 6. `tv-home-config` 接口要求

### 6.1 接口目的

这条接口用于给 Android TV 首页下发轻量的区域化首页配置，而不是承担完整内容分发平台职责。

它只负责回答：

- 当前国家或区域应该显示什么背景图。
- 当前国家或区域应该显示哪些推荐 App。

### 6.2 请求方式

- 方法：`GET`
- 路径：`/api/me/tv-home-config`
- 查询参数：`countryCode`
- 查询参数：`regionCode`，可选

### 6.3 返回结构

建议返回结构与客户端当前 DTO 保持一致：

- `id`
- `countryCode`
- `regionCode`
- `backgroundImageUrl`
- `featuredAppIds`
- `status`
- `version`
- `createdAt`
- `updatedAt`

### 6.4 数据语义要求

- `countryCode` 必须为大写国家码，拿不到时可落 `GLOBAL`。
- `regionCode` 可为空。
- `backgroundImageUrl` 可为空。
- `featuredAppIds` 可为空数组，但不能返回 `null`。
- `status` 至少需要支持可发布态和停用态。
- `version` 用于客户端和后台排查配置版本。

### 6.5 查询匹配规则

建议按下面优先级命中：

1. `countryCode + regionCode`
2. `countryCode`
3. `GLOBAL`

如果最终仍没有可用配置，接口也不应该报错，可以直接返回一个空配置对象。

原因很简单：Android TV 客户端本身已经有 fallback 机制，接口层不需要把“没有配置”升级成故障。

## 7. 项目标识与 principal 兼容要求

### 7.1 项目标识

当前 Android TV 客户端默认：

- `OPENCLAW_PROJECT_KEY=openclaw`

当前 `home` 正式项目键：

- `openclaw-android-tv`

因此二选一必须尽快落地：

- 方案 A：`home` 把 `openclaw` 显式加入 `openclaw-android-tv` 的项目别名。
- 方案 B：客户端后续发版时把默认项目键改成 `openclaw-android-tv`。

本轮为了尽快联通，建议先做方案 A，再安排客户端在后续版本切正式默认值。

### 7.2 principalType

当前 Android TV 客户端默认：

- `OPENCLAW_BOOTSTRAP_PRINCIPAL_TYPE=device`

当前 `home` 的正式 principal 语义：

- `phone`
- `project_identity`

虽然当前代码会把未知类型兜底归一到 `project_identity`，但这属于隐式兼容，不建议继续依赖。

建议 `home` 明确把 Android TV 的 `device` 视为合法兼容值，等价归一到 `project_identity`，并在接口文档里写清楚。

## 8. 错误语义与可用性要求

### 8.1 错误语义

Android TV 接口要避免再出现“网关没配但客户端看到的是业务 `404`”这种情况。

建议明确约束：

- `401` 只用于未认证或会话无效。
- `403` 只用于策略禁止、用户禁用、租约禁用。
- `404` 只用于真实资源不存在。
- `503` 用于租约池容量不足、依赖服务不可用、暂时性后端故障。
- 网关层不得用 `404` 掩盖漏配路由。

### 8.2 时延要求

Android TV 首页配置拉取当前超时只有 `1500ms`，因此：

- `tv-home-config` 必须是低延迟接口。
- 不要把首页配置查询设计成重联表、重计算或远端聚合。
- 最好是本地表直读，必要时内存缓存。

### 8.3 降级要求

即使下面这些子能力异常，也不应该拖垮整个 Android TV 首页：

- `tv-home-config`
- `policy`
- `releases/latest`
- `model-lease`

也就是说，`home` 要把“局部不可用”和“整体不可用”区分开。

## 9. home 侧建议的数据与管理面

### 9.1 最小管理对象

为了满足 Android TV 首页配置，`home` 至少需要一个 `tv_home_configs` 管理对象，字段至少包括：

- `id`
- `projectKey`
- `countryCode`
- `regionCode`
- `backgroundImageUrl`
- `featuredAppIds`
- `status`
- `version`
- `createdAt`
- `updatedAt`

### 9.2 最低后台能力

后台最少需要支持：

- 新增配置
- 编辑配置
- 按国家和区域查询配置
- 发布和停用配置
- 查看当前版本

当前不要求在 `home` 一次性做复杂审核流，先把 CRUD 和发布可用性做出来即可。

## 10. 推荐执行顺序

`home` 线程建议按这个顺序做：

1. 先补统一公网网关，把 `/api/client/**` 和 `/api/me/**` 放出来。
2. 实现 `GET /api/me/tv-home-config`。
3. 落 `tv_home_configs` 的存储层和基础查询逻辑。
4. 增加后台最小 CRUD，能录入国家、区域、背景图和推荐 App。
5. 显式补 `openclaw -> openclaw-android-tv` 项目别名兼容。
6. 显式补 `principalType=device -> project_identity` 的兼容说明或实现。
7. 更新 `home` 的部署文档和项目集成文档。

## 11. 联调验收清单

以下清单全部通过，才算 `home` 侧对 Android TV 接入完成：

- 访问 `http://1.12.246.48/api/client/bootstrap/auth` 不再是网关 `404`。
- 访问 `http://1.12.246.48/api/client/policy` 不再是网关 `404`。
- 访问 `http://1.12.246.48/api/client/releases/latest` 不再是网关 `404`。
- 访问 `http://1.12.246.48/api/me/tv-home-config` 不再是网关 `404`。
- Android TV 客户端在线时能拉到远端首页配置。
- Android TV 客户端断网或后端停机时仍能进入首页。
- Android TV 客户端在无远端配置时能使用缓存或默认配置继续运行。
- Android TV 客户端在租约池不可用时，只有模型能力降级，不影响首页和本地入口。

## 12. 当前建议

这轮不要先重写客户端协议，也不要先改客户端默认值，优先把 `home` 的公网接入面补齐。

原因是：

- Android TV 客户端已经有离线和降级基础。
- 当前真正阻断联调的是 `home` 网关缺口和 `tv-home-config` 缺口。
- 先把 `home` 接口面补齐，才能在真机上快速验证完整链路。

等 `home` 打通后，再安排客户端做下一轮收口：

- 正式切 `projectKey=openclaw-android-tv`
- 评估是否把 `principalType` 从 `device` 明确切到 `project_identity`
- 优化离线时的首页展示策略和缓存命中策略
