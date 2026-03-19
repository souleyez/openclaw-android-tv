# ADR-001: Unified Model Router for All LLM Requests

## Status

Accepted

## Date

2026-03-19

## Context

OpenClaw Android / Android TV Assistant 需要调用多个模型供应商来完成语音意图解析、问答、控制指令理解与未来扩展功能。供应商价格、稳定性、区域可用性和免费额度差异明显。

如果客户端直接接入各家模型，会带来以下问题：

- 上游密钥泄露风险
- 无法统一计费和审计
- 无法做成本控制和回退
- 无法实现 API 池化与租约复用

## Decision

所有模型请求统一经过后端 `Model Router Service`。

客户端只调用平台统一接口，不直接持有供应商真实密钥。

Model Router 负责：

- 选择供应商
- 选择模型
- 失败回退
- 成本记录
- 预算与限流
- API 池租约管理

## Consequences

### Positive

- 可统一做计费与账本
- 可在不同供应商之间回退
- 可根据套餐控制模型档位
- 可集中审计和风控

### Negative

- 后端复杂度上升
- 增加一层网络延迟
- 需要维护供应商适配器

## Alternatives Considered

### 方案 A：客户端直连模型供应商

未采纳。

原因：

- 安全性差
- 难以审计
- 无法做平台级成本优化

### 方案 B：只接一家供应商

未采纳。

原因：

- 供应商锁定风险高
- 成本与区域可用性波动大

## Notes

MVP 阶段可先用模块化单体中的一个 `model-router` 模块实现，后续随着调用量增长再独立拆分。
