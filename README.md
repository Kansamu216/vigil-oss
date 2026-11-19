# Vigil（守夜）— OSS

Vigil 的公开稳定子集：跨 Forge、NeoForge、Fabric 的 Minecraft 反作弊组件。

> 当前状态：**仓库骨架 / 架构验证阶段**。尚未实现反作弊业务逻辑，当前构建只用于验证模块结构与 Java 工具链。

本仓库由私有主线按模块白名单导出，不是独立维护的 fork。公共代码在私有主线修改后同步到此仓库；公开贡献会先回port 到主线再重新导出。

## 公开范围

- 签名挑战、加密会话、nonce 防重放与保活。
- Mod 清单、SHA-256 白名单和基础自身完整性。
- UUID/IP 封禁与基础审计。
- 仅游戏窗口的管理员主动截图。
- 保守的行为统计告警。

不包含全桌面截图、硬件账号关联、进程/目录深度扫描、实验性注入检测和私有管理 API。公开版不是完整防护承诺，具体边界见 `docs/public-scope.md` 与 `docs/privacy.md`。

## 当前平台目标

Forge 1.20.1 / 1.21.1、NeoForge 1.20.4 / 1.21.1、Fabric 1.20.1 / 1.21.1。

```bash
./gradlew build
```

平台目录为独立 Gradle 构建，详见 `platforms/README.md`。

## 工程规范

开发、测试、发布与回滚的统一流程见 `docs/development-and-testing.md`。

## License

MIT（见 `LICENSE`）。
