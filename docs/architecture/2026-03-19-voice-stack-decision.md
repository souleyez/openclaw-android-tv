# OpenClaw Voice Stack Decision

## 1. 文档信息

- 文档名称：语音技术选型决策
- 版本：v0.1
- 日期：2026-03-19
- 关联 PRD：[openclaw-android-tv-prd](C:\Users\soulzyn\Desktop\codex\docs\product\2026-03-19-openclaw-android-tv-prd.md)
- 关联客户端架构：[android-client-architecture](C:\Users\soulzyn\Desktop\codex\docs\architecture\2026-03-19-android-client-architecture.md)

## 2. 目标

为 OpenClaw Android / Android TV 客户端确定一套兼顾以下目标的语音技术路线：

- 兼容低版本 Android 与 Android TV
- 支持中文、英语、西班牙语、土耳其语、俄语等多语言
- 控制首包体积
- 在无 GMS、弱网、低算力设备上仍可运行
- 尽量优先复用现成 API 和开源能力

## 3. 结论

采用“三层语音栈”：

1. 系统语音能力为默认主路径
2. 开源离线能力为设备兼容兜底
3. 云端高精度 ASR/TTS 为质量兜底

## 4. ASR 选型

### 4.1 主路径

- `Android SpeechRecognizer`

原因：

- Android 原生集成最简单
- 首包体积最小
- 适合 MVP 快速起步
- 对高版本和标准 Android TV 设备优先兼容

适用场景：

- 已有系统语音服务
- 网络可用
- 设备 ROM 对系统语音支持较好

### 4.2 离线兜底

- 首推：`sherpa-ncnn`
- 备选：`Vosk`

决策理由：

- `sherpa-ncnn` 更适合现代移动端离线实时推理与多语言扩展
- `Vosk` 更成熟稳妥，适合作为 PoC 或保守路线

适用场景：

- 无 GMS
- 弱网
- 系统语音能力缺失
- 需要本地离线唤起或本地指令识别

### 4.3 云端高精度兜底

- `Whisper` 类服务端 ASR

决策理由：

- 准确率高
- 多语言鲁棒性强
- 适合复杂问答与噪声环境

不建议：

- 直接在低端安卓TV设备本机跑 Whisper

## 5. TTS 选型

### 5.1 主路径

- `Android TextToSpeech`

原因：

- 集成简单
- 体积最小
- 与系统音频焦点管理更自然

### 5.2 离线兜底

- `Piper`

原因：

- 开源
- 多语种可扩展
- 本地播报效果较好

适用场景：

- 系统 TTS 缺失
- 无 GMS
- 需要更统一的多语言离线播报体验

### 5.3 云端兜底

- 云端合成音频返回客户端播放

适用场景：

- 需要更自然音色
- 本地无可用 TTS
- 某些语言本地音库缺失

## 6. MVP 路线

MVP 建议严格按以下顺序落地：

### Phase A

- `SpeechRecognizer`
- `TextToSpeech`

目标：

- 最小集成成本
- 最小首包体积
- 先验证主要 TV 场景

### Phase B

- 引入 `sherpa-ncnn` 作为离线 ASR fallback

目标：

- 提升无 GMS 与弱网设备覆盖率

### Phase C

- 引入 `Piper` 作为离线 TTS fallback

目标：

- 提升多语言离线播报一致性

### Phase D

- 复杂指令或高精度转写走云端 `Whisper`

目标：

- 提升复杂问答质量

## 7. 包体策略

### 建议原则

- 首包不内置全部离线语音模型
- 离线模型按语言按需下载
- 角色音色按需下载
- 客户端保留模型管理入口和缓存策略

### 预估体积

- 纯系统语音版：
  - 下载包约 `35MB - 60MB`
  - 安装后约 `80MB - 150MB`
- 带单语种离线 ASR 或单语种离线 TTS：
  - 下载包约 `70MB - 160MB`
  - 安装后约 `150MB - 350MB`
- 多语种全离线首包：
  - 可能达到 `200MB - 600MB+`

## 8. 为什么不全离线首包

- Android TV 和盒子安装环境复杂
- 低版本设备存储与解压能力有限
- 首包过大会显著降低安装成功率与 OTA 效率
- 多语言模型会让 APK/AAB 快速膨胀

## 9. 风险与缓解

### 风险 1：系统语音能力碎片化

- 缓解：
  - 启动时做能力探测
  - 自动切换离线或云端路径

### 风险 2：离线模型体积大

- 缓解：
  - 模型拆分
  - 首次按需下载
  - 仅为重点语言预装

### 风险 3：云端语音成本高

- 缓解：
  - 简单控制命令优先本地规则和系统 ASR
  - 复杂问答再走云端高精度

## 10. 最终决策

当前项目正式采用以下语音路线：

- MVP 默认：
  - `SpeechRecognizer`
  - `TextToSpeech`
- 兼容增强：
  - `sherpa-ncnn`
- 离线 TTS 增强：
  - `Piper`
- 高精度云端：
  - `Whisper` 类服务端 ASR

## 11. 下一步实现顺序

1. 在 Android 原生层接入 `SpeechRecognizer` 占位实现
2. 在 Android 原生层接入 `TextToSpeech` 占位实现
3. 加入语音能力探测结果上报
4. 再评估 `sherpa-ncnn` 与 `Piper` 的模型下载方案
