# Android TV P1 Acceptance Ledger

Date: 2026-05-16, updated 2026-05-17

Device: `64WGLJQS5R`

Home API base: `https://oc.goods-editor.com/api`

Latest checked APK: `artifacts/android-tv/openclaw-tv-0.1.2-payment-service-debug-20260517.apk`

Latest screenshot: `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-smoke-20260517-132552.png`

Latest SHA256: `22F05E71DDF793E69D6BF1BF42834EC5110448780A1017332EB3A87C387B0056`

Latest version: `versionCode=2026051702`, `versionName=0.1.2`

## Ledger

| Area | P1 target | Current status | Test evidence | Owner | Blocker | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Home visual shell | Home is understandable at first paint and fits 1280x720. | Dark home shell renders with hero area, app rail, quick actions, and status notice. | Real-device screenshot `openclaw-p1-api-notice-20260516-125751.png`; UI tree captured at `openclaw-p1-api-notice-ui-20260516-125751.xml`. | APP | None known. | Pass |
| Home character sprite assets | Character is a lightweight sprite system, not only a static bust. | Six PNG sprite atlases are present under `drawable-nodpi`. | `:feature:home:testDebugUnitTest :app:assembleDebug` passed; screenshot shows character rendered. | APP | Final art direction can still replace temporary generated frames. | Pass for APK baseline |
| Dialogue-driven character actions | Character state changes from dialogue/status/focus. | `HomeUiState.assistantSpriteState` maps online/offline/warning/critical/hero-ad states; UI temporarily uses `talk` when dialogue changes. | `HomeViewModelTest` covers guide/idle/point_left/think/worried states. | APP | Real AI listening/speaking events are not wired yet. | Pass for P1 |
| Character-left ad slot | `home.hero` image creative can render left of character without covering content. | `hero_ad_card` is laid out before the character and bound from `runtime-manifest.adSlots[]`. | Code binding and unit coverage exist; current device run had no valid creative to show. | APP + home content | Needs actual `home.hero` creative from runtime manifest for final visual evidence. | Partial |
| Ad slot empty/fallback state | Missing, disabled, invalid, or empty slot does not break layout. | Empty slot is hidden; screenshot shows no awkward blank ad card. | Device screenshot with no creative; no crash. | APP | None known. | Pass |
| App rail defaults | Default rail includes required mainstream apps and compact `+` entry. | App rail shows compact install card; previous defaults are implemented in home/app catalog path. | UI tree confirms `安装应用`; visual screenshot confirms compact card. | APP + runtime content | Exact runtime-manifest app content depends on home data. | Partial |
| App management `+` entry | `+` opens management overlay. | Real-device OK press opens `应用管理` overlay. | UI tree `openclaw-p1-app-management-ui-after-ok-20260516-130926.xml` includes `应用管理`, `返回首页`, `新增入口`, and local/installed app rows. | APP | None known. | Pass |
| Local APK install and upgrade | USB/local APK files appear as install or upgrade candidates. | Overlay lists local APK candidates and labels install vs upgrade. | UI tree includes `夏杰语音` with `按确定升级` and `OpenClaw TV` with `按确定安装`. | APP | Actual Android package installer confirmation was not opened in this pass to avoid disturbing the bench APK. | Partial |
| App uninstall prompt | Non-system installed apps can open Android uninstall confirmation. | Overlay exposes uninstall affordance for installed apps. | UI tree includes `CIBN酷喵`, `乐播投屏`, and `按确定打开 · 长按确定卸载`. | APP | Confirmation dialog not opened in this pass to avoid accidentally uninstalling useful test apps. | Partial |
| Cast self-built DLNA | OpenClaw exposes a lightweight DLNA media renderer. | Renderer starts on `wlan0`, obtains multicast lock, publishes `description.xml`, and joins SSDP multicast. | Logcat tag `OpenClawCast`: `DLNA endpoint resolved ip=192.168.1.2 interface=wlan0 descriptionUrl=http://192.168.1.2:54579/dlna/description.xml`; `DLNA SSDP responder joined group=239.255.255.250:1900`. | APP | Router/multicast and phone media app behavior can still affect discovery. | Partial; phone discovery/playback pending |
| Cast Lebo fallback | AirPlay/Xiaomi mirror remains available through Lebo fallback where needed. | Service-only Lebo start succeeds without foregrounding the Lebo UI during home startup. | Logcat: `Lebo service-only start result=true`; services include `com.hpplay.happyplay.aw/.AirPlayService`; Lebo PSS about `27011 KB`. | APP + system/Lebo | Full mirror stack is not implemented by ordinary APK; Lebo remains the pragmatic fallback. | Partial |
| Cast return-to-home behavior | Casting or fallback should return to OpenClaw home instead of sticking in Lebo UI. | Return guard starts after service-only Lebo discovery. | Logcat: `Lebo return guard starting`; no active phone cast session tested in this pass. | APP | Lebo private session detection is heuristic. | Partial |
| Background trimming | Returning home trims obvious playback/store background processes best-effort. | Trim path now emits `OpenClawTrim` evidence and preserves Lebo while discovery/cast may need it. | Logcat: `Home background trim collected count=20 preserveLeboPackages=true`; `Background trim attempting count=19 ... packages=...com.dangbeimarket...`; OpenClaw PSS `30357 KB`. | APP | Ordinary APK cannot freeze or force-stop privileged/system packages. | Pass for best-effort public API |
| Boot launch | APK launches on boot where public boot receivers are delivered. | Manifest declares `RECEIVE_BOOT_COMPLETED`; receiver launches `MainActivity`. Explicit `QUICKBOOT_POWERON` receiver smoke can bring OpenClaw to foreground when the app is not in Android's bad-process state. | Focus after quickboot receiver smoke: `com.openclaw.tv/.MainActivity`; standard `BOOT_COMPLETED` cannot be spoofed from shell on this device due `SecurityException`. | APP + device OS | Real reboot evidence still needed; OEM launcher/background policy may block public boot behavior. | Partial |
| Home API production domain | APK uses `oc.goods-editor.com` as production home API. | Runtime log shows canonical base URL on 0.1.1 clean-data launch. | Logcat: `Using canonical platform API baseUrl=https://oc.goods-editor.com/api`; public curl: `/api/health` 200, TV routes 401 without auth. | APP | None known. | Pass |
| Runtime manifest content | Apps and ads come from `runtime-manifest`, not new TV endpoints. | Android code reads `runtime-manifest`; no new ad/app endpoint added. 0.1.1 startup no longer blocks on legacy `client/model-lease`. | Code and tests align with `runtime-manifest.adSlots[]`; latest device log showed `Runtime steady sync state queueStatus=queued phase=active hasResourceSession=true`. | home + APP | Production resource capacity currently queues instead of immediately granting. | Pass for runtime-manifest path |
| Low-end memory budget | OpenClaw remains lightweight on 1 GB board. | Latest clean-data 0.1.1 home run showed OpenClaw PSS around 30 MB; service-only Lebo fallback adds about 27 MB when kept for discovery. | `dumpsys meminfo com.openclaw.tv` total PSS `30012 KB`; earlier same-session `dumpsys meminfo com.hpplay.happyplay.aw` total PSS about `27 MB`. | APP | Need repeated after real phone cast flow. | Pass for OpenClaw home; observe Lebo cost |
| Vendor system projection | Miracast, Wi-Fi Display, and AirPlay sink are not promised from ordinary APK. | Explicitly treated as system/vendor blocker; Lebo fallback remains pragmatic path. | Vendor materials table and X10 gap analysis document missing interfaces. | vendor | Need SDK/service/firmware/sample for system projection. | Blocked |
| Vendor OTA and diagnostics | OTA, no-adb log export, and diagnostics require vendor interfaces. | Documented as outside ordinary APK baseline. | `2026-05-06-system-level-adaptation-vendor-materials.md`. | vendor | Need OTA API, log export SOP, firmware, permissions. | Blocked |
| Vendor hardware controls | Focus, keystone, brightness, fan, temperature, input source require vendor APIs. | Documented as vendor/material blocker. | `2026-05-06-x10-emcp-v4-material-gap-analysis.md`. | vendor | Need AIDL/SDK/docs/sample and test firmware. | Blocked |

## Latest Real-Device Notes

- 2026-05-17 0.1.2 payment-service APK installed successfully: `openclaw-tv-0.1.2-payment-service-debug-20260517.apk`; SHA256 `22F05E71DDF793E69D6BF1BF42834EC5110448780A1017332EB3A87C387B0056`.
- Package metadata on device: `versionCode=2026051702`, `versionName=0.1.2`, `lastUpdateTime=2026-05-17 13:25:31`.
- 0.1.2 smoke foreground: `com.openclaw.tv/.MainActivity`; filtered logcat found no OpenClaw fatal crash.
- 0.1.2 UI tree confirms `服务中心`, `已升级到 0.1.2`, `OpenClaw TV test advertisement`, `安装应用`, and `连「Soulzy」`.
- 0.1.2 runtime log confirms canonical production API base `https://oc.goods-editor.com/api`.
- 0.1.2 runtime log confirms `Runtime steady sync state queueStatus=granted phase=active hasResourceSession=true`.
- 0.1.2 cast log confirms DLNA renderer starts on `wlan0`, IP `192.168.1.2`, description URL `http://192.168.1.2:44811/dlna/description.xml`, and SSDP multicast joins `239.255.255.250:1900`.
- 0.1.2 service center UI tree confirms `大会员套餐`, `AI服务套餐`, `CNY 0.01`, and `已生效，点此可继续续费。`.
- 0.1.2 memory spot check: `dumpsys meminfo com.openclaw.tv` total PSS `35403 KB`, `Activities: 1`, `WebViews: 0`.
- 0.1.2 evidence files:
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-smoke-20260517-132552.png`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-smoke-ui-20260517-132552.xml`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-smoke-logcat-filtered-20260517-132552.txt`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-service-center-20260517-132702.png`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.2-service-center-ui-20260517-132702.xml`
- 2026-05-17 0.1.1 resource-session APK installed successfully: `openclaw-tv-0.1.1-home-resource-session-debug-20260517.apk`; SHA256 `EAE82B415835B49154890448295144DDCC7DAC39317F8CF152A50C5215A70165`.
- Package metadata on device: `versionCode=2026051701`, `versionName=0.1.1`, `lastUpdateTime=2026-05-17 08:38:08`.
- Clean-data smoke used `adb shell pm clear com.openclaw.tv`, then launched from `monkey -p com.openclaw.tv`.
- Clean-data smoke foreground: `com.openclaw.tv/.MainActivity`.
- Clean-data UI tree confirms `网络已连`, `安装应用`, `投屏`, and `连「Soulzy」`.
- Clean-data runtime log confirms `Using canonical platform API baseUrl=https://oc.goods-editor.com/api`.
- Clean-data runtime log confirms `Runtime steady sync state queueStatus=queued phase=active hasResourceSession=true nextDelayMs=53927`.
- Clean-data cast log confirms DLNA renderer starts on `wlan0`, IP `192.168.1.2`, description URL `http://192.168.1.2:56787/dlna/description.xml`, and SSDP multicast joins `239.255.255.250:1900`.
- Clean-data trim log confirms background trim collected `20` candidates and attempted `19` eligible playback/store packages while preserving Lebo.
- Clean-data memory: `dumpsys meminfo com.openclaw.tv` total PSS `30012 KB`, `Activities: 1`, `WebViews: 0`.
- 0.1.1 clean-data evidence files:
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.1-clean-20260517-083749.png`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.1-clean-ui-20260517-083749.xml`
  - `C:\Users\soulzyn\Desktop\codex\artifacts\android-tv-smoke\openclaw-0.1.1-clean-logcat-filtered-20260517-083749.txt`
- Installed APK on `64WGLJQS5R`: success.
- Foreground activity after launch: `com.openclaw.tv/.MainActivity`.
- Crash buffer: no OpenClaw crash found.
- UI tree confirms cast copy `连「Soulzy」`.
- Runtime log confirms canonical production API base `https://oc.goods-editor.com/api`.
- Latest Bootstrap refresh log identifies the remaining `503` source as `client/model-lease`.
- Home UI now hides raw `HTTP 503`, `Platform API`, and route names from user-facing copy; it shows `首页仍可继续使用，后台会自动重试最新策略同步。 服务暂时忙，请稍后再试。`.
- Cast diagnostics build installed successfully: `openclaw-tv-p1-cast-diagnostics-debug-20260516-130614.apk`.
- Self-built DLNA starts on the real device: `wlan0`, IP `192.168.1.2`, SSDP multicast `239.255.255.250:1900`, description URL `http://192.168.1.2:54579/dlna/description.xml`.
- Lebo lightweight fallback service starts in the background: `com.hpplay.happyplay.aw/.AirPlayService`.
- Latest evidence files: `openclaw-p1-cast-diagnostics-20260516-130718.png`, `openclaw-p1-cast-diagnostics-ui-20260516-130718.xml`.
- App management overlay verified from the `+` card with DPAD OK. Evidence: `openclaw-p1-app-management-20260516-130857.png`, `openclaw-p1-app-management-ui-after-ok-20260516-130926.xml`.
- App management UI shows install, upgrade, open, and long-press-uninstall affordances; actual install/uninstall confirmation dialogs still need a controlled test app before marking destructive flows fully accepted.
- Trim diagnostics build installed successfully: `openclaw-tv-p1-trim-diagnostics-debug-20260516-131128.apk`.
- `OpenClawTrim` evidence shows home trim runs twice on return/startup, preserves Lebo while discovery is kept alive, and attempts 19 eligible playback/store packages through public `killBackgroundProcesses`.
- Latest trim evidence files: `openclaw-p1-trim-diagnostics-20260516-131220.png`, `openclaw-p1-trim-diagnostics-ui-20260516-131220.xml`.
- Boot receiver smoke: shell cannot spoof protected `android.intent.action.BOOT_COMPLETED` on this board (`SecurityException`), but explicit `android.intent.action.QUICKBOOT_POWERON` receiver smoke brought `com.openclaw.tv/.MainActivity` to foreground after the app had been previously launched.
- P1 acceptance APK installed successfully: `openclaw-tv-p1-acceptance-debug-20260516-131509.apk`; SHA256 `1FFB92DD86470AC2FE712DFB97C79E9231571481C7B8C97C001016DB2F35F50D`.
- Full local verification passed: `:feature:home:testDebugUnitTest :feature:cast:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
- Acceptance smoke foreground: `com.openclaw.tv/.MainActivity`; crash buffer had no OpenClaw crash; evidence files `openclaw-p1-acceptance-20260516-131556.png`, `openclaw-p1-acceptance-ui-20260516-131556.xml`.
