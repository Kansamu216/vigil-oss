# 开发说明

## 环境

- JDK 21
- Gradle Wrapper（仓库已包含）

核心模块保持 Java 8 API 兼容，平台适配模块使用各自的 Java 版本。平台构建消费根构建发布的版本化模块，不引用源码目录或本机路径。

## 常用命令

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
./gradlew -p platforms/neoforge-1.21.1 runServer
./gradlew -p platforms/neoforge-1.21.1 runClient
```

## 模块关系

```text
platforms/neoforge-1.21.1 → modules/server/* → modules/shared/*
```

- `modules/shared`：协议、加密、模型与平台接口。
- `modules/server`：会话、判定、存储与运行时装配。
- `modules/client`：客户端会话、配置与文件摘要。
- `platforms/neoforge-1.21.1`：NeoForge 1.21.1 适配与双端产物。
- `test-support/session-harness`：会话协议测试基座。

## 交付检查

- 运行根构建与平台构建。
- 确认 server/client 源集与产物互不包含对端实现。
- 确认仓库中没有密钥、玩家数据、数据库或本机路径。
- 更新 `CHANGELOG.md` 与你修改的文档。

架构与数据处理约束分别见 `docs/architecture.md` 和 `docs/privacy.md`。
