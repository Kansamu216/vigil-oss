# Vigil 开发交接文档

面向：后续接手的 Flash 模型、AI agent 及少量人工维护者。  
文档基线：2026-11-19。  
维护方式：代码结构、命令或交付规则变化时同步修改本文；不要另建重复入口。

## 1. 阅读约定与当前状态

### 1.1 信息标记

本文使用三类标记，后续更新必须保留这种区分：

| 标记 | 含义 |
|---|---|
| **现状** | 已存在于仓库、可核对的文件、配置或 CI 结果 |
| **设计** | 已确定方向但尚未实现；不能当作已有 API 调用 |
| **待补充** | 当前无法确认，需要代码、环境或官方资料进一步验证 |

### 1.2 当前真实状态

- **现状**：项目名称为 **Vigil（守夜）**，主语言为 Java。
- **现状**：私有主线和公开子集均已初始化并推送至个人账号 `Kansamu216`。
- **现状**：根构建和六个平台骨架构建曾通过 GitHub Actions；验证的是 Java 空骨架，不是 Minecraft 运行兼容性。
- **现状**：`src/main/java` 与 `src/test/java` 中目前只有 `.gitkeep`，没有业务类、测试类、Mod 入口或 metadata。
- **现状**：没有可安装的 Vigil Mod jar，没有握手、检测、封禁、截图或数据库实现。
- **现状**：没有交付版本、交付密钥或正式 delivery manifest；`vigil-oss/sync/export-state.json` 为 `scaffold-not-yet-exported`。
- **待补充**：各加载器的真实 Mod 插件、入口、网络注册、运行端 smoke test 和跨端协议互通。

接手时先读根目录 `START_HERE.md`，再读本文；交付时读 `docs/simple-delivery.md`。

## 2. 项目目标与适用范围

### 2.1 项目目标

Vigil 是一套 Minecraft 客户端 Agent + 服务端校验的反作弊系统，目标是：

1. 客户端只上报观测事实，服务端形成判定和处置。
2. 使用签名挑战、加密会话、nonce 和序号防止篡改与重放。
3. 共享核心跨加载器复用，平台差异限制在独立 adapter 中。
4. 私有主线保留完整能力，公开仓只导出稳定、低侵入子集。
5. 每个交付对象使用独立密钥和信任锚，单个交付泄露时可单独轮换。

### 2.2 当前开发目标

| 加载器 | 1.20.x | 1.21.x | 适配层 Java |
|---|---|---|---:|
| Forge | 1.20.1 | 1.21.1 | 17 / 21 |
| NeoForge | 1.20.4 | 1.21.1 | 17 / 21 |
| Fabric | 1.20.1 | 1.21.1 | 17 / 21 |

这些是当前适配目标，**不表示已经通过 Minecraft 实际运行验证**。其他小版本只有完成契约测试、启动测试和协议互通后才能加入支持矩阵。

### 2.3 当前不做

- 不实现 1.12.2 / 1.16.5 Bukkit+Forge 混合端。
- 不依赖 `voidkit_core` 或 Voidkit 其他插件。
- 不在公共核心引入 Bukkit、Forge、NeoForge 或 Fabric 类型。
- 不承诺客户端反作弊不可破解，不使用自研“不可逆向”加密方案。
- 不让统计行为信号单独触发永久封禁。
- 不为不同客户建立长期源码分支。

混合端仅作为未来新增 adapter 的方向，当前没有其实现模块。

## 3. 技术栈与依赖

### 3.1 当前实际配置

| 项 | 当前值 | 说明 |
|---|---|---|
| 主语言 | Java | 无 Kotlin 源码或 Kotlin 构建依赖 |
| 根构建 | Gradle 8.12.1 Wrapper | Wrapper 配置在 `gradle/wrapper/` |
| 产品版本 | `0.1.0-dev.0` | 骨架阶段 |
| 公共核心 API | Java 8 | `options.release=8`，防止使用新 API |
| 根测试依赖 | JUnit Jupiter 5.11.4 | 已声明，当前没有实际测试类 |
| SQLite 坐标 | `org.xerial:sqlite-jdbc:3.53.4.0` | 仅在 catalog 声明，未接入代码 |
| 私有 IP 库坐标 | `org.lionsoul:ip2region:2.7.0` | 仅在 `private.versions.toml` 声明，未接入代码 |
| 包根（规划） | `io.github.kansamu216.vigil` | 当前尚无 Java 类 |
| GitHub Actions | core + 六平台矩阵 | `actions/setup-java@v5` |

### 3.2 平台构建依赖状态

- **现状**：`platforms/*` 都是独立、普通的 Java Gradle 构建。
- **现状**：尚未加入 ForgeGradle、ModDevGradle、NeoGradle、Fabric Loom、Yarn、Minecraft 或加载器库依赖。
- **现状**：平台构建文件只设置 Java release、group、version 和对应 Maven 仓库。
- **待补充**：每条平台线的具体插件版本、 mappings、loader 版本和依赖锁定方式。

不得把根 `settings.gradle.kts` 改成包含所有平台子项目；平台保持独立构建，避免工具链互相阻塞。

### 3.3 依赖治理规则

- 公共依赖放在 `gradle/libs.versions.toml`；私有额外依赖放在 `gradle/private.versions.toml`。
- 禁止 `systemPath`、本地 jar、绝对路径和本机 `includeBuild`。
- 客户端默认只使用 JDK；新增第三方依赖必须说明用途、分发端、许可证和打包方式。
- SQLite 只能进入 `server/storage`；私有归属地库只能进入私有服务端。
- 公开构建不能依赖私有仓、私有 Maven 或私有 secrets。

## 4. 仓库与目录结构

### 4.1 两个仓库

| 仓库 | 可见性 | 职责 |
|---|---|---|
| `https://github.com/Kansamu216/vigil-private` | Private | 唯一开发主线，含全部共享代码和私有扩展 |
| `https://github.com/Kansamu216/vigil-oss` | Public / MIT | 由私有仓按白名单导出的稳定子集 |

共享代码只在私有仓修改；公开仓共享代码修改先回port 私有主线，再重新导出。

### 4.2 私有仓结构

```text
vigil-private/
├── START_HERE.md / DECISIONS.md / CHANGELOG.md
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   ├── private.versions.toml
│   └── wrapper/
├── modules/
│   ├── shared/{protocol,crypto,model,platform-api,detection-api}
│   ├── server/{runtime,session,storage,decision,feature-*}
│   └── client/{agent,mod-sensor,self-check,window-capture}
├── private/
│   ├── server/{advanced-session,identity,deepscan,admin-api}
│   └── client/{full-capture,env-sensors,injection-sensors}
├── platforms/                    # 六个独立构建
├── distributions/                # 产物命名与打包约束
├── test-support/{codec,session,platform-contract}
├── sync/public-surface.yml
├── deliveries/                   # 当前只有说明，无实际交付
├── docs/
└── tools/                        # 当前只有规划说明
```

公开仓没有 `private/` 和 `gradle/private.versions.toml`，公开导出状态在 `sync/export-state.json`。

## 5. 核心模块职责与依赖关系

### 5.1 公共模块

| 模块 | 设计职责 | 当前实现 |
|---|---|---|
| `shared/protocol` | 消息 ID、信封、编解码、版本、长度上限 | 空目录 |
| `shared/crypto` | RSA、AES-GCM、签名、随机数、秘密擦除 | 空目录 |
| `shared/model` | Mod、报告、观测、指纹、判定和策略模型 | 空目录 |
| `shared/platform-api` | 玩家、服务端、通道、调度、权限、日志 SPI | 空目录 |
| `shared/detection-api` | 传感器、检查、证据、评分和处置 SPI | 空目录 |
| `server/runtime` | 自有配置、生命周期、调度、命令、权限、日志 | 空目录 |
| `server/session` | 挑战、握手、保活、超时和速率限制 | 空目录 |
| `server/storage` | SQLite、migration、Repository、备份恢复 | 空目录 |
| `server/decision` | 证据分级、评分和处置门槛 | 空目录 |
| `server/feature-*` | 完整性、封禁、审计、截图、行为统计 | 空目录 |
| `client/*` | Agent、Mod 清单、自检、窗口截图 | 空目录 |
| `private/*` | 全屏取证、深度扫描、身份关联、管理 API 等 | 空目录 |

### 5.2 依赖方向

```text
platform adapter → runtime/features → shared APIs
client agent → sensors → shared APIs
private extension → public stable APIs
distribution → selected modules
```

禁止：

- shared 依赖 server/client/private。
- client import server。
- 公共模块 import `private.*`。
- Feature 之间直接 import 内部实现。
- 加载器类型进入 shared 或纯业务模块。

## 6. 关键流程与数据流

### 6.1 当前构建流程（现状）

```text
根构建：共享 Java 8 模块编译/测试
       ↓
六个平台独立构建：分别用 Java 17 或 21 编译空骨架
       ↓
GitHub Actions 汇总 core-build 与 platform-build 结果
```

当前还没有“core 发布 → 平台消费 → dist 打包”的实际依赖链。

### 6.2 计划中的校验数据流（设计，未实现）

```text
客户端传感器
  → 采集 Mod/自身/环境等事实
  → 后台生成 ClientReport
  → RSA-OAEP 包裹 AES 会话密钥，AES-GCM 加密报告
  → 服务端 channel
  → session 校验 nonce、身份、序号和报告
  → decision 形成 verdict
  → audit/storage 保存证据
  → alert / kick / restriction / ban（按证据级别）
```

核心不变式：客户端不返回最终作弊结论；取证失败不能成为踢人理由；内部故障不能自动等同于作弊。

### 6.3 计划中的公开导出流程（设计，工具未实现）

```text
私有 main
  → 读取 sync/public-surface.yml
  → 复制 allowlist 模块与公共构建基线
  → 生成公开 settings
  → 私有路径/包名/secret 扫描
  → 公开独立构建与测试
  → 记录 export-state.json 的来源 revision
```

当前公开仓是初始化时创建的公共子集，尚无正式自动导出器。

## 7. 接口与数据结构说明

以下接口和数据结构均为**设计基线，尚未实现**。后续实现若调整，应同步修改本节和 `docs/architecture.md`。

### 7.1 平台 SPI

| 接口（建议名） | 职责 | 关键约束 |
|---|---|---|
| `GuardChannel` | 注册、发送、接收、包大小限制 | 明确网络线程与业务线程切换 |
| `GuardScheduler` | 主线程/后台执行、延迟、周期、取消 | 关服必须取消并释放 |
| `GuardEvents` | join/quit/attack/blockBreak/start/stop | 事件顺序由各平台契约测试验证 |
| `GuardCommands` | 命令注册、权限检查、反馈 | 危险操作独立权限节点 |
| `ModListProvider` | modid、version、文件路径、加载状态 | 不让业务代码直接访问 ModList/FabricLoader |
| `PlayerView` | UUID、名字、IP、在线状态 | 不暴露平台玩家类型给核心 |
| `ServerContext` | 数据目录、日志、关服钩子 | 不依赖 voidkit_core |
| `CaptureContext` | 游戏窗口或帧缓冲信息 | 公开版仅游戏窗口 |
| `CapabilitySet` | 声明平台实际支持能力 | 不支持时显式降级，不空实现假装支持 |

### 7.2 基础数据模型

| 模型 | 主要字段（设计） | 不变式 |
|---|---|---|
| `ModEntry` | `id/version/file/size/sha256` | 哈希流式计算，文件变化需可识别 |
| `Observation` | `type/detail/severity` | 是事实，不是最终 verdict |
| `ClientReport` | `protocol/nonce/player/uuid/mods/fingerprint/signals/env` | 必须绑定当次挑战 |
| `Fingerprint` | 加盐哈希后的标识集合 | 原文不出客户端；公开版裁剪采集面 |
| `Verdict` | `level/score/reasons/action` | 只由服务端 decision 生成 |
| `Challenge` | `sessionId/nonce/serverPublicKey/issuedAt/expiresAt/signature` | 单次使用、到期失效 |
| `Auth` | `sessionId/nonce/wrappedKey/encryptedReport` | nonce 不匹配即拒绝 |
| `ScreenChunk` | `reqId/index/total/data` | 总数、单片、总大小均有上限 |
| `DeliveryManifest` | `deliveryId/buildId/keyId/sourceCommit/artifacts/tests` | 不含私钥或玩家数据 |

### 7.3 加密报文约定（设计）

```text
密文 = iv[12] || ciphertext || gcmTag[16]
AAD  = protocolVersion | sessionId | messageId | direction | sequence
IV   = sessionId[0..3] || direction[1] || sequence[8]
```

- RSA-2048 OAEP-SHA-256 用于包裹会话密钥。
- `SHA256withRSA` 用于挑战签名。
- AES-256-GCM 用于会话报文。
- 客户端必须使用预置或确认的公钥指纹验证信任锚，不能用包内自携公钥自证。
- 会话密钥只保存在内存；重连或复检时重新生成。

## 8. 本地运行与构建

### 8.1 环境要求

- 根构建建议使用 JDK 21。
- 1.20.x 平台构建使用 JDK 17；1.21.x 使用 JDK 21。
- **当前开发机状态**：本机未找到可用 Java Runtime，因此本机未能执行构建；此前验证通过 GitHub Actions 完成。
- **待补充**：在本机安装/配置 JDK 21 后，再验证以下本地命令。

### 8.2 克隆与构建

```bash
git clone https://github.com/Kansamu216/vigil-private.git
cd vigil-private

# 根构建：公共核心与测试任务
./gradlew build

# 六个独立平台骨架
./gradlew -p platforms/forge-1.20.1 build
./gradlew -p platforms/forge-1.21.1 build
./gradlew -p platforms/neoforge-1.20.4 build
./gradlew -p platforms/neoforge-1.21.1 build
./gradlew -p platforms/fabric-1.20.1 build
./gradlew -p platforms/fabric-1.21.1 build
```

公开仓将 clone 地址换成 `vigil-oss.git` 即可。

### 8.3 当前能期待的结果

- 可以验证 Gradle 配置、Java release、模块目录和空 jar 构建。
- 当前没有实际测试类，`test` 不会提供业务验证。
- 当前没有 `runClient`、`runServer`、reobf、jarJar 或 Mod 打包任务。
- **不要把当前空骨架 jar 当成可安装的 Minecraft Mod。**

## 9. 测试与部署说明

### 9.1 当前测试状态

- **现状**：没有 Java 测试类。
- **现状**：CI 曾通过根构建和六平台空骨架构建。
- **现状**：没有真实 MC 启动、协议互通、检测样本、性能或隐私动态测试。

### 9.2 后续最小测试要求

| 改动类型 | 必跑验证 |
|---|---|
| 公共核心 | 核心单测 + 六平台契约测试 |
| 单平台适配 | 对应平台构建、启动 smoke、契约测试 |
| 协议/加密 | golden vectors、篡改/重放、畸形输入 fuzz、六端互通 |
| 新检测 | 正常样本、作弊样本、误报样本、超时和降级 |
| 存储 | migration、备份恢复、锁定/磁盘异常 |
| 公开导出 | 私有路径/secret 扫描、公开独立构建 |
| 每次交付 | 启动握手、跨交付拒绝、artifact 哈希和 secret scan |

正常金样本的自动永久封禁数必须为 0。内部故障不得被当作作弊证据。

### 9.3 部署状态与后续产物

当前不可部署。后续每个平台分别生成 server/client 包，建议命名：

```text
vigil-<side>-<loader>-<minecraft>-<version>-<channel>.jar
```

部署前必须提供：安装说明、协议兼容矩阵、配置变更、artifact SHA-256、验收结果和回退版本。

## 10. 编码规范

### 10.1 Java 与模块

- shared 只用 Java 8 API；不使用 `var`、records、`List.of`、`Path.of`。
- 包名不重叠；公共核心不引用加载器类型。
- 公开模块不得引用 `private.*`；Feature 通过 SPI 协作。

### 10.2 并发与资源

- 明确主线程和后台线程边界；后台只返回不可变结果。
- 禁止无界线程、队列、map、分片缓存和主线程 IO 等待。
- 哈希、截图、压缩、进程枚举和数据库写入放受控后台。
- 间隔用 `System.nanoTime`；审计时间戳才用 wall clock。
- 关服和断线必须取消任务并释放连接、线程和文件句柄。

### 10.3 协议、配置与存储

- 所有长度、列表、字符串、分片数和总大小有硬上限。
- `Cipher` 不得全局共享；`SecureRandom` 初始化后复用。
- 配置加载后统一 normalize；reload 使用原子快照。
- SQL 参数化，migration 先备份且只向前。
- 私钥、会话密钥、原始硬件值和完整报告不进入日志。

### 10.4 隐私与公开边界

- 新采集项先登记 `PrivacyManifest`：目的、分级、开关、留存、公开/私有。
- 公开版不包含全桌面、MAC/QQ/微信、深度扫描等代码路径。
- 统计检测默认只告警；自动处置必须有确证级证据。
- 文档不得宣称代码尚未实现的能力已经可用。

## 11. 常见维护场景

| 场景 | 执行步骤 |
|---|---|
| 新增检测传感器 | 明确威胁与误报 → 登记隐私 → 定义 Observation → 客户端/服务端实现 → 平台能力声明 → 正常/作弊/异常样本 → alert-only 验证 |
| 修复误报 | 先加入正常复现样本 → 最小调整对应检查 → 跑历史回归 → 记录阈值依据 → 不顺便扩大自动处置 |
| 修改协议 | 升协议版本 → 更新 AAD/字段/golden vectors → 六端互通 → 明确旧客户端提示与回退 |
| 新增 MC 小版本 | 判断是否可复用最近 adapter → 独立构建验证 → 契约/启动/互通 → 再更新支持矩阵 |
| 新增公开能力 | 私有主线先稳定 → 加入 `public-surface.yml` → 泄漏扫描与公开构建 → 更新公开范围和隐私文档 |
| 修改数据库 | 新 migration → 备份 → dry-run → 异常中断测试 → 恢复验证 → 发布说明 |
| 交付泄露 | manifest 标记 compromised → 只轮换对应 deliveryId 的 keyId → 重新交付 → 验证其他交付未变化 |
| AI 接手任务 | 读 START_HERE 与本文 → 完成一个任务 → 跑相关测试 → 更新 START_HERE/CHANGELOG → 报告受影响交付 |

## 12. 扩展与排错指引

### 12.1 新增平台或加载器

1. 在 `platforms/` 新建独立构建，不改 shared。
2. 实现最小 SPI，不支持的接口通过 `CapabilitySet` 降级。
3. 复用 `test-support/platform-contract`，不要复制业务代码。
4. 增加构建、启动和协议互通验证后再发布。
5. 未来混合端也走这一路径，另增 Bukkit adapter，不侵入当前核心。

### 12.2 常见问题

| 现象 | 优先检查 |
|---|---|
| 本机提示找不到 Java Runtime | 安装 JDK 21 并配置 `JAVA_HOME`；当前可先查看 GitHub Actions |
| `UnsupportedClassVersionError` | 检查 1.20.x 是否 Java 17、1.21.x 是否 Java 21；shared 是否 release=8 |
| `Extension with name 'libs' does not exist` | 根脚本应通过 `VersionCatalogsExtension` 读取 `libs`，不要在 subprojects 闭包直接依赖生成访问器 |
| 某平台依赖源失败 | 只处理该平台构建，不要改其他平台仓库或统一升级 |
| 公共核心使用高版本 API | `options.release=8` 会在编译阶段拦截，修正 API 而非放开限制 |
| 公开仓找不到私有 catalog | 公开 settings 不应声明 `privateLibs`；检查导出是否误复制 |
| Mod 无法被 MC 加载 | 当前尚无入口和 metadata，属于未实现；先完成 M0，而不是改支持矩阵 |
| 出现 split package | 检查两 jar 是否导出相同包；客户端共享代码需确定性重定位 |
| 客户端拒绝服务端挑战 | 核对 deliveryId、keyId、公钥指纹和服务器私钥是否属于同一交付 |
| 交付后无法追溯 | 检查 manifest 的 sourceCommit、buildId、keyId 和 artifact SHA-256 |

## 13. 交付与发布规则

日常以 `docs/simple-delivery.md` 为准。核心规则：

- `deliveryId` 标识安装实例，`buildId` 标识构建，`keyId` 标识密钥代次。
- 普通升级复用 keyId；泄露后只轮换对应 deliveryId。
- 交付必须生成 manifest、artifact 哈希和验收结果。
- 建议的 `tools/delivery.py create/verify/rotate-key` 目前只是接口设计，**尚未实现**。
- 公开导出器、秘密存储和受保护发布 CI 也尚未实现，不能把手工复制视为正式发布。

## 14. 待补充事项

| 事项 | 当前缺口 | 下一步证据/动作 |
|---|---|---|
| 六平台真实工具链 | 插件、loader、mappings 未配置 | M0 分别选择并锁定官方兼容组合 |
| Mod 入口与 metadata | 当前没有任何入口文件 | 先完成一个空 Mod，再推广到其他平台 |
| core 到平台的依赖发布 | 当前平台没有 core 依赖 | 确定版本化 Maven 发布与 CI 顺序 |
| 协议与加密实现 | 无 Java 类 | 实现原语、AAD/IV、golden vectors 和攻击测试 |
| 信任锚分发 | 预置指纹/TOFU 策略未落地 | 确定专服嵌入和通用客户端确认流程 |
| 平台契约测试 | 只有目录 | 定义线程、事件、channel、调度和能力用例 |
| 真实 MC smoke test | 未运行任何 MC | 为六平台建立开发端启动测试 |
| 本地 JDK | 当前机器无 Java Runtime | 安装 JDK 21 后重新验证本地构建 |
| 交付脚本 | 仅有命令设计 | 实现 create/verify/rotate-key，禁止秘密入仓 |
| 公开导出器 | 只有 allowlist 和状态文件 | 实现导出、parity、secret 和公开构建检查 |
| PrivacyManifest | 只有规范约束 | 定义机器可读 Schema 并接入配置与文档 |
| 数据迁移 | 无数据库和 migration | 首版 Schema 设计后再制定旧 guard.db 导入 |
| 公开 release | 当前无正式导出和交付 | 公开 MVP 验收后执行 |
| 混合端 | 当前不在实现范围 | 未来有新需求时单独评审 adapter |

## 15. AI agent 工作与文档更新约定

每次任务按以下最小闭环执行：

1. 阅读 `START_HERE.md` 和本文；交付任务再读 `docs/simple-delivery.md`。
2. 只处理一个主要问题，不顺手扩大平台范围或重构无关模块。
3. 修改代码时同步修改对应测试和本文相关章节。
4. 报告：修改文件、执行命令及结果、受影响平台/交付、遗留风险、下一步。
5. 更新 `START_HERE.md` 和 `CHANGELOG.md`；重要决策追加 `DECISIONS.md`。
6. 无法确认的信息标为【待补充】，不要根据经验虚构类名、版本号、命令或测试结果。

文档更新对应关系：

| 变化 | 必须同步更新 |
|---|---|
| 模块/接口 | 本文第 4、5、7 节 + `docs/architecture.md` |
| 平台版本/构建命令 | 本文第 2、3、8 节 + `platforms/README.md` |
| 协议/加密 | 本文第 6、7、10 节 + golden vectors |
| 采集与隐私 | `docs/privacy.md`（公开仓）+ PrivacyManifest |
| 交付与密钥 | 本文第 13 节 + `docs/simple-delivery.md` + manifest |
| 测试与发布结果 | `CHANGELOG.md` + 验收记录 |
