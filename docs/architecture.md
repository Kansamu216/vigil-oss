# Vigil 架构

日期：2026-11-20  
状态：独立公开产品基线  
主语言：Java

## 1. 目标与边界

Vigil 将客户端观测与服务端判定分离：客户端只提交事实，服务端验证并形成判定。核心不依赖 Minecraft 或任何加载器类型，平台差异由独立适配层处理。

客户端可被完整分析，因此安全不依赖代码保密。协议和密码学可以公开，密钥、玩家数据和内部配置必须保持在受控环境。

## 2. 模块

```text
modules/shared/protocol      信封、消息、负载编解码与硬上限
modules/shared/crypto        AES-GCM、RSA-OAEP、签名、指纹与秘密擦除
modules/shared/model         Mod、观测、报告、挑战与判定模型
modules/shared/platform-api  玩家、通道、调度与能力 SPI
modules/server/session       挑战、握手、保活、限流与超时
modules/server/decision      服务端证据分级、评分与处置门槛
modules/server/storage       SQLite migration、封禁与审计 Repository
modules/server/runtime       配置与业务链路装配
platforms/neoforge-1.21.1    NeoForge 1.21.1 独立适配构建（client/server 源集分离）
```

依赖方向：`platform → runtime → session/decision/storage → shared`。`shared` 不依赖服务端或客户端业务模块。

## 3. 信任模型

- 客户端报告是不可信输入，最多形成观测级证据。
- `ServerEvidence` 只能由服务端在独立验证后创建；自动处置只使用服务端评分。
- 统计信号只告警；采集失败、平台能力缺失和内部故障不是作弊证据。
- 封禁需要确证级证据并显式开启自动处置，默认配置只告警。

## 4. 协议与密钥

- 信封包含版本、消息 id、方向、单调序号和有界负载。
- AAD 绑定版本、会话 id、消息 id、方向和序号。
- 挑战使用一次性 nonce、有效期和 SHA256withRSA 签名；挑战携带服务器公钥，客户端先按预置指纹校验公钥，再验证签名。未配置指纹时拒绝握手，不做首次信任自动接受。
- 每次连接生成独立 AES-256 会话密钥，使用服务端 RSA-2048 公钥 OAEP 包裹。
- 私钥只在服务端受控目录，不进入客户端产物、日志或仓库。

## 5. 线程与资源

- 会话状态按会话串行推进；同一认证帧的并发重放只能成功一次。
- 哈希、数据库写入和截图必须进入受控后台，不在网络或主线程执行重活。
- 会话表、限流表、队列和分片缓存必须有上限，关服时取消任务并释放资源。

## 6. 打包与发布

- 核心按版本发布，平台构建消费显式版本，不复制源码或使用本机绝对路径。
- 服务端与客户端分别出包；客户端共享代码确定性重定位，避免 split package。
- `verifySideJars` 校验 metadata、重定位后的字节码引用、包隔离和打包内容。
- 发布必须提供测试记录、artifact SHA-256、配置变更和回退版本。

## 7. 尚未实现

客户端采集 Agent、真实玩家事件接入、跨端握手实测、其余平台适配与公开发布流水线尚未完成。文档不把规划能力描述为已可用。
