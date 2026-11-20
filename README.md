# Vigil（守夜）

Minecraft 服务端会话校验与观测组件。

## 构建

需要 JDK 21。

```bash
./gradlew build
./gradlew publishModules
./gradlew -p platforms/neoforge-1.21.1 build
./gradlew -p platforms/neoforge-1.21.1 obfuscateJars
```

发布产物位于 `platforms/neoforge-1.21.1/build/obfuscated/`。

## 配置

- 客户端：`config/vigil/client.properties`
- 服务端：`config/vigil/server.properties`
- 服务端白名单：`config/vigil/mods.txt`（每行 `modId [version] [sha256]`）

玩家数据和部署方告知要求见 [数据处理](docs/privacy.md)。

## 管理命令

```text
/vigil status
/vigil ban <player> [reason]
/vigil unban <uuid>
```

需要权限等级 3。

## 许可

MIT（见 `LICENSE`）。
