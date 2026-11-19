# Vigil（守夜）：独立反作弊系统双仓与多平台架构设计

日期：2026-11-19  
状态：架构基线 / 仓库初始化  
账号：`Kansamu216`（个人账号，不使用组织）  
主语言：**Java**

## 0. 本轮结论

- 项目命名 **Vigil（守夜）**。公开仓 `vigil-oss`，私有主线仓 `vigil-private`。
- 私有仓是唯一 canonical source；公开仓是按**白名单导出的稳定功能子集**，不是第二个长期开发分支。
- 新系统不依赖 `voidkit_core`，也不依赖 Voidkit 的其他插件。旧 `voidkit-guard` 仅保留维护与 bug 修复。
- 当前只实现 **1.20.x / 1.21.x 的 Forge、NeoForge、Fabric 单端版本**。1.12.2 / 1.16.5 Bukkit+Forge 混合端仅作为未来扩展方向，本轮不创建其实现模块。
- 公共核心使用 Java 8 API/字节码，避免未来旧版本迁移时重写核心；当前平台按各自要求使用 Java 17 或 21。
- 不把全部平台放进一个巨型 Gradle 构建。公共核心是一套构建；每个平台线是独立 Gradle 构建，工具链可独立升级、失败和回滚。
- 服务端与客户端分别出包；客户端不包含服务端判定、封禁策略、密钥管理或管理 API。

## 1. 当前实现范围与支持矩阵

| 平台构建 | Minecraft | Java | 当前状态 | 说明 |
|---|---:|---:|---|---|
| `platforms/forge-1.20.1` | 1.20.1 | 17 | 本轮目标 | 参考现有 Voidkit 已支持的 1.20.1 Forge 线 |
| `platforms/forge-1.21.1` | 1.21.1 | 21 | 本轮目标 | 参考现有 Voidkit 的 1.21.1 Forge 线 |
| `platforms/neoforge-1.20.4` | 1.20.4 | 17 | 本轮目标 | NeoForge 官方 1.20.2+ 文档线；不额外维护 1.20.1 NeoForge |
| `platforms/neoforge-1.21.1` | 1.21.1 | 21 | 本轮目标 | 与现有 Voidkit NeoForge 21.1.x 基线一致 |
| `platforms/fabric-1.20.1` | 1.20.1 | 17 | 本轮目标 | 独立 Fabric adapter，不模拟 Forge API |
| `platforms/fabric-1.21.1` | 1.21.1 | 21 | 本轮目标 | 独立 Fabric adapter |

范围说明：

- 六个平台线代表六个**可落地的适配目标**，不是承诺同一代码包通吃所有小版本。
- 同一加载器的其他小版本只有在通过 contract tests 和启动 smoke test 后才加入支持矩阵。
- 当前没有混合端模块、没有 Bukkit 插件入口、没有 CatServer/NMS/CraftBukkit 依赖。
- 未来混合端通过新增 `platforms/hybrid-*` 和 `bukkit-api` 可选桥接实现；公共核心不需要改写。

## 2. 现有项目评估与继承原则

### 保留

1. 客户端只上报观测事实，服务端形成判定。
2. 协议、加密、模型、业务与加载器适配分层。
3. 签名挑战、会话密钥、nonce 防重放、保活与随机复检。
4. 统计检测只告警不自动封禁，样本不足不判定。
5. 配置 normalize、异步审计、后台采集、证据与处置解耦。
6. 公开/私有分离交付，客户端 jar 不包含服务端策略。

### 纠正

1. 删除对 `voidkit_core` 的 Feature、VEvent、Config、Command、Permission、ApiRouter 等依赖。
2. 不再用同一 `common` 源码目录复制到两个 jar 后直接发布；打包必须处理 JPMS 与包冲突。
3. 公开/私有边界从“替换源文件 + 人工约定”升级为“机器可检查的导出白名单”。
4. 每个平台拥有独立构建和依赖锁，避免某个旧 Forge 工具链阻塞全部版本。
5. 隐私清单成为代码、配置和文档的共同事实源；公开版与私有版采集边界可自动审计。
6. 协议、数据 Schema、产品版本独立演进，并补协议/状态机/平台契约测试。

## 3. 总体架构

```text
                    ┌──────────── vigil-private ────────────┐
                    │                                       │
                    │  Java 8 shared core                   │
                    │  protocol / crypto / model / APIs     │
                    │                                       │
                    │  server runtime → session → decision  │
                    │                       │               │
                    │                 storage / evidence    │
                    │                                       │
                    │  public features + private extensions │
                    └───────────────┬───────────────────────┘
                                    │ versioned core artifacts
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
       Forge adapters        NeoForge adapters       Fabric adapters
       1.20.1 / 1.21.1       1.20.4 / 1.21.1        1.20.1 / 1.21.1
              └─────────────────────┼─────────────────────┘
                                    ▼
                         server / client dist jars

              private --allowlist export--> vigil-oss
```

运行时不变式：

1. 客户端只返回事实，不返回“作弊/清白”的最终结论。
2. 判定只在服务端 `decision` 形成；客户端信号只作为加权证据。
3. 取证失败不得成为踢人理由；处置必须有独立证据门槛。
4. 平台不支持的能力必须显式降级并记录，不得静默 fail-open。
5. 公开与私有使用同一密码学和协议安全基线，私有优势来自额外传感器和策略，不来自秘密加密算法。

## 4. 模块划分与依赖关系

### 4.1 公共核心

| 模块 | 职责 | 禁止依赖 |
|---|---|---|
| `modules/shared/protocol` | 消息 ID、信封、编解码、版本和长度限制 | MC、任何加载器、业务 Feature |
| `modules/shared/crypto` | RSA/AEAD/签名、随机数、秘密擦除 | MC、业务策略 |
| `modules/shared/model` | ModEntry、Observation、Fingerprint、Verdict、策略模型 | 网络实现、MC |
| `modules/shared/platform-api` | 玩家、服务端、通道、调度、权限、日志、生命周期 SPI | Forge/NeoForge/Fabric/Bukkit |
| `modules/shared/detection-api` | 传感器、检查、证据、评分和处置 SPI | 具体检测实现 |

这些模块统一 Java 8，不引用 `List.of`、records、var、`Path.of` 等新 API。构建用 `options.release=8` 约束 API 表面。

### 4.2 单端业务模块

| 模块 | 职责 |
|---|---|
| `server/runtime` | 自有配置、生命周期、调度、命令、权限、日志和装配 |
| `server/session` | 挑战、握手、保活、任务超时、速率限制 |
| `server/storage` | SQLite、Schema 迁移、Repository、备份/恢复 |
| `server/decision` | 证据分级、评分、处置门槛、灰度状态 |
| `server/feature-integrity` | modid/version/SHA-256 白名单与审批 |
| `server/feature-ban` | UUID/IP 封禁、解封、到期与审计 |
| `server/feature-audit` | 本地审计、保留期、导出 |
| `server/feature-screenshot` | 请求、分片重组、冷却和证据元数据 |
| `server/feature-behavior` | 统计告警，默认保守，不直接自动封禁 |
| `client/agent` | 客户端消息分发、后台采集、线程边界 |
| `client/mod-sensor` | 加载器无关的 Mod 清单模型和流式哈希 |
| `client/self-check` | 自身 jar 基线与启动环境观测 |
| `client/window-capture` | 仅游戏窗口截图 |

### 4.3 私有扩展

| 模块 | 能力 |
|---|---|
| `private/server/advanced-session` | 随机复检、蜜罐、实验性挑战策略 |
| `private/server/identity` | 机器码/MAC/QQ/微信哈希、同源账号和联防 |
| `private/server/deepscan` | 进程、窗口、目录、IP、RTT 综合扫描 |
| `private/server/admin-api` | 独立管理 API、面板适配和细粒度权限 |
| `private/client/full-capture` | 全桌面、多显示器、失败连拍等完整取证 |
| `private/client/env-sensors` | 硬件和软件环境传感器 |
| `private/client/injection-sensors` | 注入、类空间和运行时篡改实验性检测 |

### 4.4 依赖规则

```text
platform adapter → runtime/features → shared APIs
client agent → sensors → shared APIs
private extension → public stable APIs
distribution → selected modules
```

禁止：

- shared 依赖 server/client/private。
- 公共模块 import `private.*`。
- client import server。
- Feature 之间直接 import；通过 SPI、事件或 runtime 注册表协作。
- Forge/NeoForge/Fabric 类型进入 shared 或纯业务模块。
- 公共核心依赖 Bukkit；未来混合端只能作为新的 adapter。

后续通过 ArchUnit/Gradle 依赖门禁和公开导出扫描把这些规则变成 CI 检查。

## 5. 平台抽象层与版本适配层

### 5.1 两层而不是一层

**Loader SPI（加载器抽象）**：处理 Forge/NeoForge/Fabric 的 Mod 入口、网络注册、事件总线、命令、权限、调度、Mod 清单和资源包枚举。

**Game Adapter（版本适配）**：处理 1.20.x 与 1.21.x 的 payload/channel 注册、网络线程语义、玩家 UUID/连接、截图上下文、方块/攻击事件和映射差异。

一个平台构建同时包含两者，但放在不同包中：

```text
io.github.kansamu216.vigil.platform.forge       // loader 共性
io.github.kansamu216.vigil.platform.forge1201   // 1.20.1 适配
io.github.kansamu216.vigil.platform.forge1211   // 1.21.1 适配
io.github.kansamu216.vigil.platform.neoforge1204
io.github.kansamu216.vigil.platform.neoforge1211
io.github.kansamu216.vigil.platform.fabric1201
io.github.kansamu216.vigil.platform.fabric1211
```

### 5.2 最小 SPI

首版只抽象 Vigil 真正使用的接口：

- `GuardChannel`：注册、发送、接收、协议版本和大小限制。
- `GuardScheduler`：主线程执行、延迟、周期、后台执行。
- `GuardEvents`：join/quit/attack/blockBreak/serverStart/serverStop。
- `GuardCommands`：命令注册、权限检查、反馈。
- `ModListProvider`：id、version、文件路径、加载状态。
- `PlayerView` / `ServerContext`：UUID、名字、IP、在线状态、数据目录。
- `CaptureContext`：窗口尺寸、帧缓冲或平台允许的抓屏路径。
- `CapabilitySet`：该平台实际支持的能力集合。

不要抽象完整 Minecraft API。不同平台难以统一的功能通过 `CapabilitySet` 降级，而不是制造大量空实现。

### 5.3 平台契约测试

`test-support/platform-contract` 为所有 adapter 提供同一组测试：

- join/quit 顺序和线程语义。
- channel 收发、最大包、未知消息和断线。
- 调度延迟、周期任务、关服取消。
- mod 清单一致性和文件哈希。
- 权限不足时拒绝危险命令。
- 能力不支持时的显式降级。

每个平台必须运行：共享单测 → 契约测试 → 开发端启动 smoke test → 与参考端协议互通测试。

## 6. 独立平台构建与产物

每个 `platforms/<line>/` 都是独立 Gradle 构建，拥有自己的 settings、构建脚本、依赖锁和工具链说明。

公共核心先构建并以版本化坐标发布到 Maven Local/内部仓库；平台构建只消费明确的 core 版本，不引用本机绝对路径。CI 统一按以下顺序：

```text
core test → core publish → platform contract tests
→ dist packaging → startup smoke → protocol compatibility
```

工具链隔离意味着：

- Forge 1.20.1 可以使用 legacy Forge 工具链，不阻塞 1.21.1。
- NeoForge 1.20.4 与 1.21.1 可分别选择匹配文档线的 NeoGradle/ModDevGradle。
- Fabric 使用 Fabric Loom，不模拟 Forge 的 payload API。
- 某个平台依赖源故障时，其他平台仍可构建和发布。

产物按 loader + MC + side + channel 命名，例如：

```text
vigil-server-forge-1.20.1-<version>-private.jar
vigil-client-forge-1.20.1-<version>-private.jar
vigil-server-neoforge-1.21.1-<version>-oss.jar
vigil-client-fabric-1.21.1-<version>-oss.jar
```

公共核心不单独作为玩家需要安装的 Mod。客户端打包时对共享代码做确定性重定位到 `...client.internal.*`，避免与 server jar 产生 split package。空 Mod 启动验证是 M0 的强制门槛。

## 7. 公开/私有边界

| 能力 | 公开仓 | 私有仓 |
|---|---|---|
| 签名挑战、AES-GCM、nonce、防重放 | 完整保留 | 相同实现 |
| 会话保活、超时、速率限制 | 保留 | 相同实现 |
| Mod 清单、SHA-256、白名单审批 | 保留 | 相同实现 |
| 自身完整性与基础启动环境检测 | 保留 | 扩展 |
| UUID/IP 封禁、基础审计 | 保留 | 扩展 |
| 游戏窗口截图 | 管理员主动触发、告知、短期留存 | 保留 |
| 全桌面/多屏截图、失败连拍 | 不包含代码路径 | 完整保留，显式启用 |
| MAC/QQ/微信、同源账号关联 | 不包含代码路径 | 完整保留，独立隐私开关 |
| 进程/窗口/目录深度扫描 | 不包含代码路径 | 完整保留 |
| 随机复检、蜜罐、实验性注入检测 | 稳定后再评估 | 私有主线验证 |
| 管理面板 API | 本地命令与文件导出 | 独立 API |
| 跨服身份/封禁联防 | 不包含 | 保留 |

公开导出采用 allowlist，不做敏感词删除式“脱敏”。公开仓不携带私有提交历史、私有部署地址、密钥、实验性蜜罐参数或内部 issue。

## 8. 依赖治理

1. 公共依赖在 `gradle/libs.versions.toml`；私有额外依赖在 `private.versions.toml`。
2. 每个平台构建独立锁版本，禁止一个平台的升级被动升级其他平台。
3. 新依赖记录用途、许可证、分发端、shade/jarJar 方式、JPMS 状态和升级策略。
4. 客户端默认只使用 JDK；第三方库需要单独 ADR 和包体审计。
5. SQLite 只在 server/storage；私有归属地库只在私有服务端。
6. 禁止 `systemPath`、本地 jar、绝对路径、本机 `includeBuild` 和隐式 Maven Local 发布版本。
7. 后续启用 dependency locking、SBOM、漏洞扫描、依赖来源白名单和 reproducible build。
8. 公开构建不能访问私有仓库、私有 Maven 或私有 CI secrets。

## 9. 版本同步与发布策略

- 产品版本使用 SemVer；协议 major/minor 独立；数据库 Schema 独立迁移。
- 私有仓 `main` 是唯一开发主线，`private-vX.Y.Z` 发布。
- 公开仓 `main` 只接受导出快照与公开文档/构建修正，`oss-vX.Y.Z` 发布。
- `vigil-oss/sync/export-state.json` 记录来源 revision。
- 共享代码只在私有仓修改；公开 PR 先回port 私有仓，再重新导出。
- 私有 preview 先观察误报和性能，稳定能力才导出；安全补丁不受该节奏限制。
- 同协议 major 才允许自动处置；跨 minor 进入兼容/降级模式。
- 六平台按支持矩阵分别发布，某个平台未通过测试不阻塞其他平台，但 release notes 必须明确标注。

## 10. 迁移路径

1. 以现有 Voidkit 的 1.20.1 Forge、1.21.1 Forge/NeoForge 基线为参考，先完成 NeoForge 1.21.1 空 Mod 和核心协议。
2. 建立独立 runtime，替换 core 的 Feature/VEvent/Config/Command/Permission/API。
3. 移植基础完整性、审计、封禁、窗口截图，形成公开 MVP。
4. 将同一 SPI 落到 Forge 1.20.1/1.21.1、NeoForge 1.20.4、Fabric 1.20.1/1.21.1。
5. 私有仓再迁移全屏取证、深度扫描、身份关联、复检/蜜罐。
6. 旧 `guard.db` 只读 dry-run 导入；UUID/IP 可迁移，硬件哈希仅在盐和算法一致时迁移，白名单全部先进入待审。
7. 新旧系统使用不同 channel/Mod ID，不同时启用两套自动踢封逻辑。
8. 旧 Voidkit guard 只保留安全修复和阻断性 bug 修复；新功能不再回port，只有 TaskTracker、协议测试等小型自包含工具可选择回port。

未来混合端迁移点：新增 Bukkit 事件/命令/权限 adapter 和 Forge hybrid bridge，复用现有 protocol、session、decision 和 storage；本轮不预留任何 Bukkit 类型或兼容分支。

## 11. 主要风险与控制

| 风险 | 控制 |
|---|---|
| 六平台维护成本失控 | 最小 SPI、共享契约测试、代表版本支持、平台独立发布 |
| 平台构建相互阻塞 | 独立 Gradle 构建与依赖锁，单平台故障隔离 |
| Forge/Fabric API 被过度统一 | CapabilitySet 降级，不制造空实现 |
| 私有代码泄漏 | allowlist 导出、squash 快照、路径/包名/secret 扫描 |
| 双仓代码漂移 | 私有 canonical、共享文件哈希 parity、公开修改先回port |
| JPMS/split package | 独立包名、客户端重定位、空 Mod smoke test |
| 全桌面取证越界 | 私有专用、显式启用、告知/同意、短期留存和访问审计 |
| 协议升级导致客户端失效 | major/minor 协商、升级提示、灰度与回滚窗口 |
| 旧数据迁移误封 | 只读 dry-run、备份、盐校验、白名单待审 |
| 公开版被误认为完整防护 | 公开文档明确能力边界与客户端方案限制 |

## 12. 当前仓库结构

```text
vigil-private/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradle/{libs.versions.toml,private.versions.toml,wrapper/}
├── modules/
│   ├── shared/{protocol,crypto,model,platform-api,detection-api}
│   ├── server/{runtime,session,storage,decision,feature-*}
│   └── client/{agent,mod-sensor,self-check,window-capture}
├── private/
│   ├── server/{advanced-session,identity,deepscan,admin-api}
│   └── client/{full-capture,env-sensors,injection-sensors}
├── platforms/
│   ├── forge-1.20.1/       # 独立构建，Java 17
│   ├── forge-1.21.1/       # 独立构建，Java 21
│   ├── neoforge-1.20.4/    # 独立构建，Java 17
│   ├── neoforge-1.21.1/    # 独立构建，Java 21
│   ├── fabric-1.20.1/      # 独立构建，Java 17
│   └── fabric-1.21.1/      # 独立构建，Java 21
├── distributions/          # 产物矩阵与打包规则
├── test-support/{codec,session,platform-contract}
├── sync/public-surface.yml
├── docs/
└── tools/

vigil-oss/
├── 相同公共构建基线和公共模块子集
├── platforms/ 六个公共平台构建骨架
├── test-support/{codec,session,platform-contract}
├── sync/export-state.json
├── docs/ 公开范围、隐私与限制
└── 无 private/ 目录、无 private.versions.toml
```

## 13. 下一步实施计划

### M0：可运行骨架验证

1. 公共核心 Java 构建与测试通过。
2. 选择 NeoForge 1.21.1 做空 Mod，验证 server/client 双包、channel 注册和启动。
3. 将同一 SPI 空实现推广到其余五个平台构建。
4. CI 加入核心测试、平台依赖检查、公开导出 dry-run。

退出标准：六平台空 Mod 可启动；无私有路径依赖；无 split package。

### M1：协议与独立 runtime

1. Java 实现 Wire/Protocol/GCrypto、AAD v2 和 fuzz。
2. runtime 配置、调度、权限、生命周期、日志。
3. SQLite Schema/migrations、会话状态机与模拟平台测试。

退出标准：无 MC 环境可跑核心测试，无 voidkit_core。

### M2：公开 MVP

1. 挑战/握手/保活/超时。
2. Mod 清单与人工白名单。
3. UUID/IP 封禁、基础审计。
4. 仅游戏窗口截图与隐私清单。

退出标准：公开仓独立部署，全部采集项可审计，异常路径有回归测试。

### M3：私有能力迁移与公开发布

1. 全屏取证、深度扫描、身份关联、复检/蜜罐、独立管理 API。
2. 实现 export/check 工具，完成公开泄漏审查。
3. 私有 preview → 公开 beta → 稳定发布。

## 14. 当前待确认

- 最终 Mod ID 是否采用 `vigil` / `vigil_client`；仓库与 Java 包根已按 Vigil 初始化。
- 私有全桌面取证的默认策略与告知/同意文本。
- 公开版是否保留行为统计模块，或推迟到首个稳定版本之后。
- 1.20.x 的其他小版本是否确有用户需求；未验证前不扩大支持矩阵。
