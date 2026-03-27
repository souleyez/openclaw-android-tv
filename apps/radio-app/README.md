# 声临

基于 Expo 的极简单页广播应用，目标平台为 Android、iOS、Web。

## 当前形态

- 单页无文字主界面
- 可更换背景图
- 中央主控支持播/停与长按录音发送
- 默认内置中文公开广播目录
- 启动时优先申请模型短租，能用模型时优先语音对话
- AI 播报期间暂停广播，结束后恢复
- AI 回复通过后端代理 MiniMax TTS 生成真实音频，并写入广播时间线
- 单一订阅产品预留为 `6元/月`

## 语音策略

- Android
  - 优先使用系统已安装中文语音
  - 若未发现可用本地中文语音，仅弹一次系统 TTS 设置引导
  - 不做应用内语音包下载
- iOS
  - 仅选择系统已有语音
  - 不做应用内下载
- Web
  - 仅使用浏览器已有 voice 列表
  - 不做包下载

## 订阅策略

- iOS 预留 Apple IAP
- Android 预留 Google Play Billing
- Web 可单独接微信支付
- 当前前后端已具备订阅计划和状态查询骨架，尚未接入真实商店购买 SDK

## 运行

```bash
npm install
npm run android
npm run ios
npm run web
```
