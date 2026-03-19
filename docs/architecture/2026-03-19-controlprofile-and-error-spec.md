# OpenClaw ControlProfile and Error Code Specification

## 1. 文档信息

- 文档名称：ControlProfile 协议与错误码规范
- 版本：v0.1
- 日期：2026-03-19
- 关联客户端设计：[android-client-architecture](C:\Users\soulzyn\Desktop\codex\docs\architecture\2026-03-19-android-client-architecture.md)
- 关联控制矩阵：[target-app-control-matrix](C:\Users\soulzyn\Desktop\codex\docs\testing\2026-03-19-target-app-control-matrix.md)

## 2. 文档目标

本规范用于统一客户端 Flutter 层、Android 原生插件层和后端配置中心之间对目标 App 控制策略的定义方式，同时统一控制执行结果与错误码，降低多设备、多 App 适配时的沟通成本。

## 3. ControlProfile 设计目标

- 统一描述每个目标 App 的控制能力
- 允许按设备、区域、版本动态开关
- 支持多条执行链路与回退顺序
- 支持线上热更新配置

## 4. ControlProfile 数据结构

建议使用 JSON 结构下发。

```json
{
  "appId": "youtube",
  "displayName": "YouTube",
  "enabled": true,
  "packageNames": ["com.google.android.youtube.tv"],
  "launchStrategies": [
    { "type": "package_launch", "priority": 1 },
    { "type": "deep_link", "priority": 2, "uri": "youtube://"}
  ],
  "supportedActions": [
    "open_app",
    "play",
    "pause",
    "resume",
    "back"
  ],
  "actionStrategies": {
    "pause": [
      { "type": "media_session_pause", "priority": 1 },
      { "type": "keyevent_media_pause", "priority": 2 }
    ],
    "back": [
      { "type": "keyevent_back", "priority": 1 }
    ]
  },
  "requiresAccessibility": false,
  "requiresMediaSession": true,
  "supportedDeviceTags": ["android_tv", "aosp_box"],
  "blockedDeviceTags": ["legacy_rom_x"],
  "minAndroidSdk": 24,
  "notes": "MVP only guarantees L1/L2 actions"
}
```

## 5. ControlProfile 字段说明

- `appId`
  - 平台内唯一标识
- `displayName`
  - 用户可见名称
- `enabled`
  - 是否启用该 App 控制
- `packageNames`
  - 允许识别的包名列表
- `launchStrategies`
  - 启动链路列表，按优先级排序
- `supportedActions`
  - 对外声明支持的动作
- `actionStrategies`
  - 每个动作的执行策略链
- `requiresAccessibility`
  - 是否强依赖辅助控制
- `requiresMediaSession`
  - 是否优先要求媒体会话
- `supportedDeviceTags`
  - 允许执行的设备类型标签
- `blockedDeviceTags`
  - 禁止执行的设备标签
- `minAndroidSdk`
  - 最低系统版本
- `notes`
  - 备注说明

## 6. 标准动作枚举

MVP 建议统一动作枚举：

- `open_app`
- `bring_to_front`
- `close_app`
- `play`
- `pause`
- `resume`
- `stop`
- `fast_forward`
- `rewind`
- `next`
- `previous`
- `back`
- `home`
- `move_up`
- `move_down`
- `move_left`
- `move_right`
- `select`
- `open_settings`

## 7. 执行策略类型枚举

建议策略类型：

- `package_launch`
- `deep_link`
- `media_session_play`
- `media_session_pause`
- `media_session_next`
- `media_session_previous`
- `keyevent_media_play_pause`
- `keyevent_media_play`
- `keyevent_media_pause`
- `keyevent_fast_forward`
- `keyevent_rewind`
- `keyevent_back`
- `keyevent_home`
- `keyevent_dpad_up`
- `keyevent_dpad_down`
- `keyevent_dpad_left`
- `keyevent_dpad_right`
- `keyevent_dpad_center`
- `accessibility_click`
- `accessibility_focus_search`

## 8. 执行结果协议

客户端控制执行建议统一返回：

```json
{
  "requestId": "req_123",
  "appId": "youtube",
  "action": "pause",
  "strategyUsed": "media_session_pause",
  "success": true,
  "errorCode": null,
  "message": "Paused successfully",
  "durationMs": 420
}
```

字段说明：

- `requestId`
  - 单次控制请求 ID
- `appId`
  - 目标 App
- `action`
  - 请求动作
- `strategyUsed`
  - 实际命中的执行策略
- `success`
  - 是否成功
- `errorCode`
  - 失败时错误码
- `message`
  - 用户或日志可读说明
- `durationMs`
  - 执行耗时

## 9. 错误码规范

建议采用三段式错误码：`领域-分类-编号`

示例：

- `CTRL-APP-001`
- `CTRL-PERM-002`
- `CTRL-EXEC-003`

## 10. 错误码分组

### App 状态类

- `CTRL-APP-001`
  - 目标 App 未安装
- `CTRL-APP-002`
  - 目标 App 未在前台
- `CTRL-APP-003`
  - 无法解析目标 App

### 权限类

- `CTRL-PERM-001`
  - 未授予录音权限
- `CTRL-PERM-002`
  - 未开启 Accessibility
- `CTRL-PERM-003`
  - 媒体控制权限不可用

### 执行类

- `CTRL-EXEC-001`
  - 启动 App 失败
- `CTRL-EXEC-002`
  - 媒体会话执行失败
- `CTRL-EXEC-003`
  - KeyEvent 注入失败
- `CTRL-EXEC-004`
  - Accessibility 执行失败
- `CTRL-EXEC-005`
  - 所有回退链路均失败

### 配置类

- `CTRL-CONF-001`
  - 未找到 ControlProfile
- `CTRL-CONF-002`
  - 当前设备被策略禁止
- `CTRL-CONF-003`
  - 当前 Android 版本不满足要求

### 系统类

- `CTRL-SYS-001`
  - 系统服务不可用
- `CTRL-SYS-002`
  - 前台应用获取失败
- `CTRL-SYS-003`
  - 设备能力探测失败

## 11. 用户提示映射

错误码需映射为可理解文案。

示例：

- `CTRL-APP-001` -> “当前设备未安装该应用”
- `CTRL-PERM-002` -> “请先开启辅助控制权限”
- `CTRL-EXEC-005` -> “当前应用暂不支持这个操作”
- `CTRL-CONF-002` -> “当前设备暂未开放该控制能力”

## 12. 日志字段规范

每次控制请求建议记录：

- `requestId`
- `userId`
- `deviceId`
- `appId`
- `action`
- `strategyAttempted[]`
- `strategyUsed`
- `success`
- `errorCode`
- `durationMs`
- `androidVersion`
- `romVendor`

## 13. 远程配置建议

ControlProfile 应支持远程下发与版本控制：

- `profileVersion`
- `updatedAt`
- `rolloutScope`
- `enabled`

同时建议支持：

- 按设备组开启
- 按区域开启
- 按 Android 版本过滤
- 按 App 版本过滤

## 14. 实施建议

### 第一阶段

- 定义基础 ControlProfile schema
- 接入 YouTube / VLC / Kodi / Spotify / 系统设置
- 实现统一结果协议和基础错误码

### 第二阶段

- 增加设备标签和灰度规则
- 增加更多 App 特化策略

### 第三阶段

- 支持配置热更新回滚
- 支持更细粒度的异常统计
