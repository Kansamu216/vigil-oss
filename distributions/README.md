# 分发产物

每个平台分别构建服务端与客户端产物：

`vigil-<side>-<loader>-<minecraft>-<version>.jar`

- `side`：`server` / `client`
- `loader`：当前为 `neoforge`
- `minecraft`：当前为 `1.21.1`

核心模块不作为玩家需要额外安装的 Mod。客户端共享代码在打包时重定位到内部命名空间。

每个产物须通过以下检查：

1. 双端构建与启动。
2. 通道与负载注册。
3. 元数据与包内容完整。
4. 客户端字节码引用随重定位更新。
5. 不包含密钥、玩家数据或加载器与 Minecraft 类。
