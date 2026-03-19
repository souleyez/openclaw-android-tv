# OpenClaw Android TV Workspace

本工作区用于规划和实现一个兼容低版本 Android 与 Android TV 的 OpenClaw 客户端及后端系统。

当前阶段已完成：

- 产品计划
- PRD
- 控制适配矩阵
- 兼容性矩阵
- 客户端技术设计
- 后端技术设计
- ADR
- PoC 执行清单

建议开发顺序：

1. 执行 `Phase 0 PoC`
2. 搭建客户端与后端工程
3. 打通最小控制闭环

目录说明：

- `docs/`：方案、架构、PRD、运营、测试文档
- `apps/android-tv-client/`：Flutter 客户端工程骨架
- `apps/backend-api/`：NestJS 后端工程骨架
- `tools/dev-env.cmd`：打开一个带 `flutter/node/git` 临时 PATH 的开发终端
- `tools/verify-tools.cmd`：验证 `flutter/node/npm/git` 是否可用
