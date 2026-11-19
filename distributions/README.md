# 分发产物规则

每个平台分别构建服务端与客户端产物：

`vigil-<side>-<loader>-<minecraft>-<version>.jar`

- `side`：`server` / `client`
- `loader`：当前为 `neoforge`
- `minecraft`：当前为 `1.21.1`

公共模块不作为玩家需要额外安装的 Mod。客户端共享代码在打包时确定性重定位到 `...client.internal.*`，避免与服务端 jar 形成 split package。

每个产物必须验证：

1. 双端构建与启动。
2. channel/payload 注册。
3. metadata 与包内容完整。
4. 客户端字节码引用已随重定位改写。
5. 不包含密钥、玩家数据或加载器/Minecraft 类。
