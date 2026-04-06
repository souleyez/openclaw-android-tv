# 声临

基于 Expo 的单页广播应用，目标平台为 Android、iOS 和 Web。

## 当前形态

- 单页极简界面
- 支持更换背景图
- 中央主控球支持播停和长按录音
- 内置中文公开广播目录
- 启动后本地优先运行，模型租约后台慢拿
- AI 播报期间暂停广播，结束后恢复
- AI 回复通过后端代理 MiniMax TTS 生成真实音频
- 预留单一订阅产品 `6 元 / 月`

## 语音策略

- Android
  - 优先使用系统已安装的中文语音
  - 如未发现本地中文语音，只做一次系统 TTS 设置引导
  - 不做应用内语音包下载
- iOS
  - 只选择系统已有语音
  - 不做应用内下载
- Web
  - 只使用浏览器已有 voice 列表
  - 不做包下载

## 订阅策略

- iOS 预留 Apple IAP
- Android 预留 Google Play Billing
- Web 可单独接微信支付
- 当前前后端已具备订阅计划和状态查询骨架，未接真实商店购买 SDK

## 运行

```bash
npm install
npm run android
npm run ios
npm run web
```

## Android 真机联调

默认 Android 走模拟器地址 `http://10.0.2.2:3000/api`。真机联调时，需要在启动 Expo 前覆盖后端地址。

PowerShell 示例：

```powershell
$env:EXPO_PUBLIC_API_BASE_URL='http://192.168.1.145:3000/api'
npm run android
```

说明：

- `192.168.1.145` 是当前开发机的局域网地址，变更后替换成新的 WLAN IP
- 手机和电脑需要处于同一局域网
- 如果改走 `adb reverse`，也仍然需要先确认 `adb devices` 能识别出真机
