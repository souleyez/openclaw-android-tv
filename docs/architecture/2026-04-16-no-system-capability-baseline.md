# OpenClaw Android TV 无系统能力 Baseline 方案

## 1. 文档目的

这份文档定义一个前提非常明确的 Kotlin 原生客户端 baseline：

- 不依赖 `vendor.voice`
- 不依赖 `vendor.media`
- 不依赖 `vendor.projector`
- 不依赖 `vendor.deviceops`
- 只依赖标准 Android 公共能力和现有 `home` 后端

它的目标不是交付最终体验上限，而是在主板系统能力尚未落地时，先把 Android TV 原生客户端重构到一个可运行、可联调、可继续演进的状态。

## 2. 适用边界

本方案只适用于以下前提：

- 主板厂商尚未提供可用的系统服务
- 项目当前需要先完成客户端 Kotlin 化
- 业务上接受“可运行 baseline”而非“系统级助手最终态”

本方案不改变仓库边界：

- `home` 继续作为共享后端和控制面
- `openclaw-android-tv` 只负责 Android TV 客户端

## 3. 允许依赖的能力

baseline 允许使用：

- Android TV launcher
- 原生 Activity / Fragment / View
- `RecyclerView`
- `SpeechRecognizer`
- `TextToSpeech`
- PackageManager
- Intent / Search Intent
- Media3
- DataStore / Room
- `home` 的固定客户端接口

## 4. 明确不允许假设的能力

baseline 不允许假设：

- 常驻唤醒词
- 系统级清洗后语音流
- 播放中无打断插话
- 跨第三方 App 的助手前景浮层
- 前台媒体 owner 的系统级精确识别
- 投影硬件控制
- OTA 系统服务深度集成

如果后续主板系统能力可用，这些能力再按可选适配层追加。

## 5. Baseline 版本必须能做的事情

这一版原生客户端必须能完成：

- 启动进入 Android TV 原生壳
- 接入 `home` 的 bootstrap / policy / lease
- 展示低配首页壳
- 在应用内触发 push-to-talk
- 把识别结果映射到本地控制动作
- 打开目标 App
- 对支持搜索的 App 发起显式搜索 Intent
- 把“不支持的系统能力”明确展示给用户

## 6. Baseline 明确不承诺的事情

这一版不承诺：

- “你好小爪”这类常驻热词唤醒
- 视频播放不停时的稳定语音插话
- 在 YouTube / Netflix / Plex 等第三方 App 画面之上悬浮助手
- 亮度、输入切换、待机之外的系统级主板能力
- 高级媒体自动化
- 设备级 OTA 执行与诊断闭环

## 7. UI 和交互要求

针对 `RK3128 + 1GB RAM + 4GB eMMC`，baseline UI 要严格低配：

- 原生 Views 优先
- `RecyclerView` 作为首页 rail 基线
- 显式焦点管理
- 无重动画
- 无视频背景
- 无大图长驻缓存
- 明确展示“基础版”或“能力受限”状态

## 8. 语音策略

在无系统语音前端的前提下：

- 仅支持应用内 push-to-talk
- 优先使用 `SpeechRecognizer`
- `TextToSpeech` 只作为简单播报能力
- 当系统语音能力不可用时，要有明确错误和降级提示

不要在这一版里引入：

- 本地离线 ASR 模型
- 本地离线 TTS 模型
- 本地回声抵消复杂实现

## 9. 媒体与控制策略

在无 `vendor.media` 的前提下：

- 控制路径以 app launch 和显式 Intent 为主
- 搜索只对明确支持的 App 开启
- 通用媒体控制只保留最低可行集合
- 无法可靠控制时必须返回结构化失败，不允许静默失败

## 10. 后续升级方式

这份 baseline 方案要求客户端结构从一开始就预留能力适配层。

后续如果主板厂商补齐系统能力，应按“可选适配层”升级，而不是推翻 baseline：

- 先接入 `vendor.voice`
- 再接入 `vendor.media`
- 再接入 `vendor.projector`
- 最后接入 `vendor.deviceops`

也就是说，baseline 不是废案，而是最终产品的最小运行内核。

## 11. 当前结论

如果主板系统能力暂时不可用，项目仍然可以先做 Kotlin 原生重构。  
但必须把这一版明确定义为：

- Android TV 原生 baseline
- 非最终系统级助手体验
- 结构为后续主板能力接入预留边界
