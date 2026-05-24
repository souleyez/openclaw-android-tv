# OpenClaw Android TV 工厂联调包与 OTA 交付说明

日期：2026-05-24  
当前建议交付版本：`0.1.12 / 2026052409`  
项目：`openclaw-android-tv`  
平台 API：`https://oc.goods-editor.com/api`

## 交付目标

给工厂合作方一个可安装、可联网、可远程升级的 APP baseline，方便后续双方围绕系统权限、投屏、预装、静默 OTA 和售后诊断继续协作。

这不是最终量产签名包。当前目标是先建立一条稳定的“工厂联调签名 lineage”：工厂装入该包后，后续 OTA 必须继续使用同一签名，或者换成厂商 platform 签名后重新建立新的 baseline。

## 当前 OTA 能力

已跑通：

- 客户端固定请求 `https://oc.goods-editor.com/api`。
- 客户端可获取 `/api/client/updates/manifest`。
- 客户端可上报 `/api/client/updates/report`。
- 平台可发布 `full_apk` OTA release。
- 客户端可后台下载整包 APK。
- 下载完成后校验 SHA-256。
- 首页出现“新版本已准备好”安装提示。
- 有系统安装权限时尝试静默安装。
- 无系统安装权限时拉起系统安装器。
- 安装完成并重启新版后，上报 `installed`。

2026-05-24 真机烟测结果：

- 设备：`64WGLJQS5R`
- 路径：`0.1.10 / 2026052407` -> `0.1.11 / 2026052408`
- 平台报告链路：`offered -> downloading -> downloaded -> verified -> installed`
- 结论：远程 OTA 主链路可用。

## 当前不承诺项

- 普通 APK 无法保证真静默安装。
- 当前测试机没有授予 `android.permission.INSTALL_PACKAGES`。
- 真静默需要厂商 platform 签名、`priv-app` 预装、权限白名单或厂商设备管理能力。
- `delta_apk` 仍为协议/后台能力，当前建议工厂联调先只走整包 `full_apk`。
- 降级回滚不能靠降低 `versionCode` 完成。恢复包必须使用更高 `versionCode`。

## 工厂需要确认

1. 是否允许我方 APK 以 `priv-app` 方式预装。
2. 是否提供 platform signing 或由工厂代签。
3. 是否能配置 `privapp-permissions` 授予安装权限。
4. 首次安装方式：ADB、U 盘、工厂刷机预装、系统安装器。
5. 是否提供稳定设备 ID/序列号作为后台设备识别。
6. 是否允许设备长期访问 `oc.goods-editor.com`。
7. 是否需要独立的工厂测试 channel，例如 `factory` 或 `pilot`。

## 构建方式

调试包：

```powershell
.\scripts\android-tv-build-factory-pilot.ps1
```

release/factory 包：

```powershell
$env:OPENCLAW_RELEASE_STORE_FILE="C:\path\to\openclaw-tv-factory.jks"
$env:OPENCLAW_RELEASE_STORE_PASSWORD="***"
$env:OPENCLAW_RELEASE_KEY_ALIAS="openclaw-tv"
$env:OPENCLAW_RELEASE_KEY_PASSWORD="***"
.\scripts\android-tv-build-factory-pilot.ps1 -Release
```

如果本机存在以下 ignored 文件，脚本会在 `-Release` 时自动加载，不需要手动设置环境变量：

```text
artifacts/signing/openclaw-tv-factory-pilot.signing.local.properties
```

使用 PKCS12/JKS 做 pilot 签名时，建议 store password 与 key password 保持一致，避免部分 Android/Gradle 工具链读取失败。

输出目录：

```text
artifacts/android-tv/factory-pilot/
```

输出内容：

- APK
- 同名 `.sha256.txt`

签名材料不得提交 Git。当前仓库已忽略 `*.jks`、`*.keystore` 和本地 signing 目录。

## 平台发布口径

工厂联调包首次安装后，后续 OTA release 要满足：

- `versionCode` 必须大于设备当前版本。
- APK 签名必须和设备当前安装包一致。
- `artifactSha256` 必须使用实际交付 APK 的 SHA-256。
- 先小范围 `rolloutPercent` 或指定 `targetScope`，验证通过后再扩大。
- 默认使用 `installPolicy=deferred_prompt`，直到厂商系统权限跑通。

推荐状态流：

```text
draft -> rolling(小范围) -> rolling(扩大) -> completed
```

失败恢复：

```text
paused 当前 release
发布更高 versionCode 的恢复包
设备安装恢复包后上报 installed
```

## 工厂验收步骤

1. 安装工厂联调包。
2. 开机联网，确认首页能进入。
3. 平台后台确认设备上线。
4. 发布一个更高 `versionCode` 的 `full_apk` OTA。
5. 等待客户端拉取 manifest。
6. 确认设备上报 `offered`、`downloading`、`downloaded`、`verified`。
7. 点击首页安装提示。
8. 如果没有系统权限，确认系统安装器能正常弹出并完成安装。
9. 新版启动后，确认平台收到 `installed`。

## 给工厂的简短说明

当前 APP 已具备远程更新闭环，但普通 APK 只能做到“下载、校验、提示安装、系统安装器确认”。如需真静默 OTA，请工厂提供 platform 签名或 priv-app 预装与权限白名单。后续所有 OTA 包必须使用同一签名并递增 `versionCode`。
