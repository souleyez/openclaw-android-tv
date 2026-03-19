# OpenClaw Android TV Phase 0 PoC Checklist

## 1. 文档信息

- 文档名称：Phase 0 PoC 执行清单
- 版本：v0.1
- 日期：2026-03-19
- 关联计划：[openclaw-android-tv-plan](C:\Users\soulzyn\Desktop\codex\docs\plans\2026-03-19-openclaw-android-tv-plan.md)
- 关联 PRD：[openclaw-android-tv-prd](C:\Users\soulzyn\Desktop\codex\docs\product\2026-03-19-openclaw-android-tv-prd.md)
- 关联兼容矩阵：[android-tv-compatibility-matrix](C:\Users\soulzyn\Desktop\codex\docs\testing\2026-03-19-android-tv-compatibility-matrix.md)

## 2. Phase 0 目标

在正式进入 MVP 开发前，验证以下三件事：

1. 低版本 Android / Android TV 上的语音交互链路是否可用
2. 首批目标 App 的 L1/L2 控制是否足够稳定
3. 当前方案是否适合以 `Flutter + Android 原生插件 + 云端控制` 作为正式路线

## 3. PoC 交付物

Phase 0 结束必须产出：

- 设备兼容性结论表
- 目标 App 控制成功率统计
- 语音识别与 TTS 可用性结论
- 权限与 ROM 限制清单
- MVP 第一批支持 App 名单
- 不建议承诺的功能清单

## 4. 执行周期建议

- 总周期：2 周
- 第 1 周：设备准备、基础语音链路、VLC/Kodi 验证
- 第 2 周：YouTube/Spotify/Netflix/系统设置/本地播放器验证，输出结论

## 5. 设备准备清单

至少准备以下设备：

1. Android 7 盒子，无 GMS
2. Android 8 / 8.1 盒子，无 GMS
3. Android 9 盒子
4. Google TV 设备
5. 国产定制 Android TV 设备
6. 智能投影设备

每台设备记录：

- 品牌
- 型号
- Android 版本
- ROM 信息
- 是否 GMS
- 遥控器类型
- 默认本地播放器

## 6. App 验证优先顺序

建议按以下顺序做：

1. VLC
2. Kodi
3. Spotify
4. YouTube
5. 系统设置
6. Netflix
7. 本地播放器

## 7. PoC 验证项

## 7.1 安装与启动

- App 是否可安装
- 首次启动是否崩溃
- 首页是否显示正常
- 遥控器焦点是否可用

## 7.2 权限与基础能力

- 录音权限申请是否正常
- 前台服务是否可启动
- Accessibility 是否可开启
- 系统语音识别是否可用
- 系统 TTS 是否可用

## 7.3 语音链路

每台设备至少验证以下语言中的 2 种：

- 中文
- 英语
- 西班牙语
- 俄语
- 土耳其语

测试项：

- 按键触发语音录入
- 识别结果返回
- 语音播报返回
- 大字字幕显示
- 弱网或失败提示

## 7.4 App 控制

每个目标 App 验证以下动作：

- 打开 App
- 返回
- 播放
- 暂停
- 继续
- 快进
- 下一项或下一首

如果动作不适用于具体 App，可记录“不适用”。

## 7.5 设备共享模拟

即使 PoC 阶段后端未完成，也应模拟以下场景：

- 同一账号在两台设备登录
- 第二台设备进入首页
- 超出设备上限时提示逻辑

## 8. 测试记录模板

每次测试记录以下字段：

- 日期
- 测试人
- 设备型号
- Android 版本
- App 名称
- 动作
- 控制链路
- 结果：成功 / 失败 / 部分成功
- 错误说明
- 是否可进入 MVP

## 9. 通过标准

### 设备层面

- 安装成功率 >= 95%
- 首页启动成功率 >= 95%
- 语音链路可用率 >= 85%

### App 控制层面

- L1 成功率 >= 95%
- L2 成功率 >= 85%

### 整体层面

- 至少 5 个目标 App 能进入 MVP
- 至少 4 个目标 App 能稳定支持核心 L2 动作

## 10. 判定规则

### 可进入 MVP

满足以下任一条件：

- L1 稳定，L2 大部分可用
- 失败场景可明确降级，不影响主要体验

### 延后支持

满足以下任一条件：

- L1 可用但 L2 失败率高
- 依赖复杂 Accessibility 逻辑且 ROM 差异大

### 不建议承诺

满足以下任一条件：

- 页面级复杂控制严重依赖节点结构
- 设备和版本之间表现差异过大

## 11. 最终结论模板

Phase 0 结束时，需要对每个 App 给出一条结论：

- `进入 MVP 强适配`
- `进入 MVP 限制性支持`
- `延后到二期`

同时对每类设备给出一条结论：

- `推荐支持`
- `有条件支持`
- `暂不推荐`

## 12. 风险复盘项

PoC 结束后要复盘：

- 哪些 App 依赖 Accessibility 过重
- 哪些设备缺少稳定语音能力
- 哪些 ROM 对后台保活有明显限制
- 哪些动作需要本地规则替代大模型

## 13. Phase 0 完成标志

满足以下条件视为完成：

- 设备池完成验证
- 目标 App 控制矩阵填完
- 第一批 MVP App 名单确定
- 关键风险和不做项写清楚
- 客户端和后端工程骨架开始搭建

## 14. 紧接着进入 Phase 1 的任务

PoC 结束后立即进入：

1. Flutter TV 客户端壳
2. Android 原生插件最小集合
3. 后端鉴权、设备注册和意图路由最小闭环
