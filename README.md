# Vigil（守夜）

Minecraft 服务端会话校验与观测组件。

## 构建

需要 JDK 21。

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
```

## 配置

- 客户端：`config/vigil/client.properties`
- 服务端：`config/vigil/server.properties`

## 许可

MIT（见 `LICENSE`）。
