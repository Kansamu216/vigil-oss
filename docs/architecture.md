# Vigil 架构

## 目标

Vigil 将客户端观测与服务端判定分离。核心不依赖 Minecraft 或加载器类型，平台差异由独立适配层处理；协议和密码学实现可以公开查阅，密钥与玩家数据由部署方在受控环境中保管。

## 模块

```text
modules/shared/protocol      信封、消息与负载编解码
modules/shared/crypto        AES-GCM、RSA-OAEP、签名与指纹
modules/shared/model         挑战、报告、观测与判定模型
modules/shared/platform-api  通道、调度与能力接口
modules/server/session       挑战、握手、保活、限流与超时
modules/server/decision      判定与处置门槛
modules/server/storage       SQLite 迁移、封禁与审计
modules/server/runtime       配置与业务链路装配
platforms/neoforge-1.21.1    NeoForge 1.21.1 独立构建
```

依赖方向：`platform → runtime → session/decision/storage → shared`。`shared` 不反向依赖业务模块。

## 数据流

1. 玩家进服后，服务端签发一次性挑战。
2. 客户端按预置指纹核对服务器公钥，验证挑战并建立会话。
3. 客户端提交有限字段的观测报告。
4. 服务端完成验证、形成判定、写入审计，并执行配置的处置动作。

客户端数据只作为观测事实；自动处置依据服务端验证结果与显式配置的门槛。默认配置只记录告警。

## 协议

- 信封包含版本、消息类型、方向、单调序号和有界负载。
- AEAD 附加数据绑定版本、会话、消息类型、方向与序号。
- 挑战使用一次性 nonce、有效期和 SHA256withRSA 签名，携带服务器公钥。
- 每次连接生成独立 AES-256 会话密钥，由服务端 RSA-2048 公钥包裹。
- 客户端未配置服务器指纹时不建立会话。

## 资源约束

- 会话状态按会话串行推进。
- 文件摘要与数据库写入在受控后台执行。
- 会话表、限流表、队列与缓存均有上限；关服时取消任务并释放资源。

## 产物

核心按版本发布，平台构建消费显式版本。服务端与客户端分别出包，客户端共享代码在打包时重定位到内部命名空间；构建校验元数据、包内容与字节码引用。
