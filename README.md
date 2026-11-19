# Vigil（守夜）

Vigil 是一个可独立安装和构建的 Minecraft 客户端观测与服务端校验 Mod。

客户端只上报观测事实，服务端负责验证、判定、审计和处置。协议使用标准密码学，密钥与玩家数据不进入仓库。

## 当前实现

- 有界协议信封、挑战/认证/报告编解码与 AAD 绑定。
- AES-256-GCM、RSA-OAEP、SHA256withRSA、一次性 nonce 和单调序号。
- 会话握手、限流、超时与并发重放防护。
- 服务端证据分级、保守处置门槛、SQLite 封禁与审计存储。
- NeoForge 1.21.1 双端 Mod 骨架与独立 server/client 产物。

客户端采集 Agent 和平台事件接入仍在开发中；当前版本不是可直接部署的完整反作弊产品。

## 构建与测试

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
```

需要 JDK 21。平台构建消费根构建发布的显式版本模块，不引用源码目录或本机绝对路径。

## 目录

- `modules/shared`：协议、加密、模型和平台 SPI。
- `modules/server`：会话、判定、存储与运行时装配。
- `platforms/neoforge-1.21.1`：独立的 NeoForge 1.21.1 构建。
- `test-support/session-harness`：协议与会话测试基座。

架构见 `docs/architecture.md`，隐私约束见 `docs/privacy.md`。

## License

MIT（见 `LICENSE`）。
