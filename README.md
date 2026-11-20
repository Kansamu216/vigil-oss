# Vigil（守夜）

Vigil 是面向 Minecraft 服务端的会话校验与观测组件。客户端提交运行事实，服务端完成验证、审计与处置。

核心使用标准密码学，协议字段有明确边界；密钥、配置与玩家数据由部署方自行保管，不进入源码仓库。

## 特性

- 有界二进制协议：版本、消息类型、方向、单调序号与长度上限。
- 标准密码学：AES-256-GCM、RSA-OAEP、SHA256withRSA 与公钥指纹。
- 会话生命周期：一次性挑战、限流、超时与状态管理。
- 服务端判定：审计留痕、保守处置门槛、SQLite 封禁与审计存储。
- NeoForge 1.21.1 支持：服务端生命周期接入、客户端信任锚校验、Mod 清单摘要回发。
- 双端产物分离：客户端构建不包含服务端运行时代码。

客户端需要在 `config/vigil/client.properties` 中配置服务端指纹；该版本为开发预览，建议先在内测环境完成配置与指纹分发验证。

## 构建

需要 JDK 21。

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
```

平台构建使用根构建发布的显式版本模块，不引用源码目录或本机路径。

## 目录

- `modules/shared`：协议、加密、模型与平台接口。
- `modules/server`：会话、判定、存储与运行时装配。
- `platforms/neoforge-1.21.1`：NeoForge 1.21.1 独立构建。
- `test-support/session-harness`：会话测试基座。

开发说明见 `DEVELOPMENT.md`，架构见 `docs/architecture.md`，数据处理说明见 `docs/privacy.md`。

## License

MIT（见 `LICENSE`）。
