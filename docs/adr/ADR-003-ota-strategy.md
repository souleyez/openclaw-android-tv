# ADR-003: Use Controlled Rollout OTA Instead of Global Immediate Release

## Status

Accepted

## Date

2026-03-19

## Context

客户端需要兼容低版本 Android、Android TV、无 GMS 设备和多种定制 ROM。一次性全量推送新版本，极容易在未知 ROM 上引入大面积崩溃、控制失效或升级失败。

因此 OTA 必须具备风险控制能力。

## Decision

采用“分批、分时、分区、分设备组”的 OTA 灰度发布策略，而不是全量即时发布。

发布系统至少支持：

- 指定设备组
- 指定区域
- 指定时间窗口
- 指定比例
- 暂停发布
- 回滚发布

## Consequences

### Positive

- 可降低大规模兼容性事故风险
- 可先在白名单设备上验证
- 可根据异常指标及时暂停或回滚

### Negative

- 发布流程更复杂
- 需要维护设备分组与命中规则
- 需要更完善的 OTA 日志与监控

## Alternatives Considered

### 方案 A：所有用户立即收到更新

未采纳。

原因：

- 对低版本安卓 TV 风险过高
- 不利于问题定位和止损

### 方案 B：完全手工分发 APK，不做 OTA 策略

未采纳。

原因：

- 无法支撑持续运营
- 运维效率低

## Notes

MVP 阶段可以先支持基础灰度维度，复杂的多区域策略后续逐步增强。
