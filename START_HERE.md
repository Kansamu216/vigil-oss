# Vigil 当前交接页

## 当前状态

- 版本：`0.1.0-dev.0`
- 当前平台：NeoForge 1.21.1。
- 核心：协议、加密、会话、判定、存储与运行时装配已实现。
- 测试：59 个单元测试通过，包含 20,000 个畸形输入 fuzz 和并发重放回归。
- 平台产物：server/client 双 jar，含重定位与 split package 校验。
- 尚未实现：客户端采集 Agent、平台事件与真实玩家连接接入。

## 下一步

- [ ] 将运行时接入 NeoForge 1.21.1 的进服、收帧与处置事件。
- [ ] 实现客户端采集、报告构建与握手流程。
- [ ] 在真实开发服务端完成一次端到端握手验收。

## 常用命令

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
./gradlew -p platforms/neoforge-1.21.1 runServer
./gradlew -p platforms/neoforge-1.21.1 runClient
```

## 禁止事项

- 不提交密钥、玩家数据、数据库、截图或本机路径。
- 不添加未实际使用的扩展接口、预留配置或空实现。
- 不让核心依赖平台或加载器类型。
- 未运行相关测试不得交付。

## 接手顺序

阅读 `START_HERE.md` → `docs/architecture.md` → `docs/privacy.md`。完成后更新本页和 `CHANGELOG.md`。
