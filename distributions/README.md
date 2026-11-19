# 分发产物规则

每个平台分别构建服务端与客户端产物，命名包含：

`vigil-<side>-<loader>-<minecraft>-<version>-<channel>.jar`

- `side`：`server` / `client`
- `loader`：`forge` / `neoforge` / `fabric`
- `minecraft`：`1.20.1`、`1.20.4`、`1.21.1`
- `channel`：`private` / `oss`

公共核心不作为玩家需要额外安装的 Mod。客户端共享代码在打包时确定性重定位到 `...client.internal.*`，避免与服务端 jar 形成 split package。

M0 必须先用空 Mod 验证：

1. 双端启动。
2. channel/payload 注册。
3. 包内容与包名隔离。
4. Kotlin 无关、voidkit_core 无关。
5. 公开包不含任何私有模块或私有依赖坐标。
