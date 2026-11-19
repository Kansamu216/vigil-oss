# Vigil 小团队简单开发与交付流程

版本：v1.0  
定位：个人或极小工作室；后续开发和维护主要由 AI agent 执行。  
替代关系：日常开发以本文为准；`development-and-testing.md` 仅在需要更完整测试矩阵时作为参考。

## 1. 核心原则

1. **一条主线**：所有代码只在 `vigil-private/main` 开发，不为客户建立长期源码分支。
2. **一个交付命令**：构建、注入公钥指纹、生成清单、计算哈希和基础验收都由同一个脚本完成。
3. **每个交付对象独立密钥**：不同客户/服务器使用不同 RSA 密钥对和客户端信任锚；单个交付泄露只需轮换该交付。
4. **同一代码基线**：不同交付只允许密钥、配置、公开/私有能力包和构建标识不同，禁止随意维护客户专用代码。
5. **AI 做开发，人管秘密和发布**：AI 可以写代码、测试和文档；私钥访问、最终发布和泄露处置由人或受保护的 CI 执行。
6. **不写自己的加密算法**：使用 JDK 的 RSA-OAEP、AES-GCM、SHA256withRSA；不用自研 class 加密器或“万能 license 系统”。

## 2. 能做到和不能保证的边界

本流程保证的是：

- 交付 A 的客户端不接受交付 B 的服务端签名挑战。
- 交付 B 的私钥无法解开交付 A 的 RSA-OAEP 会话密钥包。
- 一个交付的私钥泄露时，只需重新签发该交付，不影响其他交付的信任关系。
- 每个交付都能追溯到源码 commit、平台、协议版本、密钥代次和 artifact 哈希。

不能保证的是：

- 一个 jar 被逆向后，其中的通用检测思路永远不会被其他版本的攻击者借鉴。
- 混淆能防止破解；它只能增加成本。
- 每次交付使用不同密钥，就能防止客户自己绕过自己那份客户端。

因此目标是**密钥隔离、影响范围可控、泄露可定位和可单独轮换**，不是“不可破解”。

## 3. 最小整体流程

```text
AI 读取 START_HERE
  → 完成一个小任务
  → 运行相关测试
  → 更新 START_HERE / CHANGELOG
  → 合并到私有 main
  → 人选择需要交付的对象
  → 执行 delivery create
  → 自动构建、注入信任锚、生成清单和验收
  → 人检查清单并交付
  → 记录 delivery manifest
```

每次迭代只回答四个问题：

1. 改了什么？
2. 哪些测试证明没有破坏？
3. 哪些交付对象受影响？
4. 是否需要重新构建或轮换某个交付？

没有新增协议、公共核心或目标平台变化的文档/小修复，不需要跑完整六平台测试，也不需要重新交付所有版本。

## 4. 交付版本与加密隔离策略

### 4.1 三种标识

| 标识 | 含义 | 生命周期 |
|---|---|---|
| `deliveryId` | 一个客户/服务器安装实例，如 `acme-main` | 长期稳定 |
| `buildId` | 一次具体构建，如 `0.1.0-b7` | 每次构建递增 |
| `keyId` | 该交付的第几代密钥，如 `k3` | 泄露或主动轮换时递增 |

普通升级：同一个 `deliveryId` 保留 `keyId`，避免已有客户端全部失效。  
泄露处置：递增 `keyId`，重新生成服务端密钥对和客户端信任锚，只要求该交付更新客户端。

### 4.2 每个交付对象的密钥

- 服务端独立 RSA-2048 密钥对。
- 客户端只嵌入 `deliveryId`、`keyId` 和服务端公钥 SHA-256 指纹。
- 每次连接仍生成独立 AES-256 会话密钥；会话密钥不落盘。
- 私钥只存在于服务端或受保护的秘密存储中，绝不进入客户端 jar、公开仓、manifest 或日志。

建议交付物分两份：

```text
acme-main-0.1.0-b7/
├── server.jar
├── client.jar
├── delivery-manifest.yml
├── SHA256SUMS
└── acceptance.txt

acme-main-secrets-k3/       # 通过独立受控渠道交付，不打进公开压缩包
└── server-private.pem
```

服务端 jar 不必内置私钥；安装时将私钥放到服务端受控目录。这样构建产物本身不会因打包方式额外扩大私钥暴露面。

### 4.3 交付隔离测试

每次新交付至少自动验证：

1. 同一交付的 server/client 能完成握手。
2. 客户端 A 拒绝服务端 B 的签名挑战。
3. 服务端 B 的私钥不能解开服务端 A 的 wrapped session key。
4. 不同交付的公钥指纹不同；重复指纹直接让构建失败。
5. 客户端 jar 中找不到私钥、PEM 内容或其他交付的秘密。

### 4.4 混淆和水印只作辅助

可以为每次构建使用不同混淆 mapping，并写入不可变 `deliveryId/buildId` 水印，帮助识别泄露来源。但不要：

- 为每个客户改变协议、Mod ID 或核心算法。
- 把共同秘密藏进不同位置，误以为这样就安全。
- 为混淆引入自研 ClassLoader 或平台特定破解点。

密钥隔离由密码学完成，混淆不承担安全保证。

## 5. 交付命令设计

以下是**建议实现的命令接口**，当前骨架尚未实现这些脚本：

```bash
# 新交付：生成新密钥对并构建
python tools/delivery.py create \
  --delivery-id acme-main \
  --platform neoforge-1.21.1 \
  --channel private

# 普通升级：沿用该交付现有 keyId
python tools/delivery.py create \
  --delivery-id acme-main \
  --platform neoforge-1.21.1 \
  --channel private \
  --reuse-key

# 泄露后轮换：只影响这个交付
python tools/delivery.py rotate-key --delivery-id acme-main

# 独立验证交付物
python tools/delivery.py verify --delivery-id acme-main
```

`create` 必须自动执行：

1. 检查 git 工作区干净并记录 source commit。
2. 运行必要测试；失败立即停止。
3. 创建或读取该交付的密钥记录。
4. 构建 server/client，客户端注入公钥指纹。
5. 生成 manifest、SHA-256 和验收结果。
6. 检查无私钥泄漏、无重复指纹、无未声明文件。

脚本必须幂等：重复执行不会悄悄更换密钥，也不会覆盖旧交付目录。

## 6. AI agent 所需的最小文档结构

只保留以下长期入口，其他历史报告移入 `docs/archive/`，AI 默认不读：

```text
START_HERE.md       # 当前状态、下一步、构建/测试命令、禁止事项
ARCHITECTURE.md     # 一页模块图、平台范围、不可破坏的约束
DELIVERY.md         # 本文的简化版：如何构建、交付、轮换和验证
DECISIONS.md        # 简短决策日志：决定、原因、日期，不写长文
CHANGELOG.md        # 每次改动及受影响交付
deliveries/         # 每个交付的 manifest，无秘密
secrets/            # 不进入 git；只放受保护的密钥材料
```

### START_HERE.md 固定格式

```markdown
# 当前状态
- 当前版本：
- 可构建平台：
- 最近交付：

# 下一步
- [ ] 唯一优先任务：

# 常用命令
- 核心测试：
- 平台构建：
- 交付验证：

# 禁止事项
- 不修改公开/私有边界。
- 不创建客户专用源码分支。
- 不把密钥写入仓库、日志或客户端 jar。
- 未运行相关测试不得交付。
```

AI 开始工作前读 `START_HERE → ARCHITECTURE → DELIVERY`；结束前更新 `START_HERE` 和 `CHANGELOG`，并报告：修改文件、运行测试、受影响交付、下一步。

## 7. 版本追踪

### 7.1 命名

```text
产品版本：0.1.0
协议版本：2
交付：acme-main
构建：0.1.0-b7
密钥代次：k3
平台：neoforge-1.21.1
```

无需为每个客户建立 Git tag。源码使用正常产品 tag，具体交付由 manifest 追踪。

### 7.2 delivery-manifest.yml 示例

```yaml
schema: 1
deliveryId: acme-main
buildId: 0.1.0-b7
sourceCommit: 7504246
productVersion: 0.1.0
protocolVersion: 2
platform: neoforge-1.21.1
channel: private
keyId: k3
serverPublicKeySha256: <public-fingerprint>
status: active
artifacts:
  server.jar: <sha256>
  client.jar: <sha256>
tests:
  core: pass
  platform: pass
  handshake: pass
  deliveryIsolation: pass
  secretScan: pass
supersedes: 0.1.0-b6
notes: ""
```

manifest 可以进入私有仓；私钥、会话密钥、客户私有配置不能进入 manifest。

建议另维护一个 append-only `deliveries/index.jsonl`，只记录交付、构建、keyId、状态和文件位置，方便 AI 快速查询。

## 8. 轻量测试与验收清单

### 每次代码改动

- [ ] 相关核心单元测试通过。
- [ ] 受影响平台构建通过；未受影响平台不强制全跑。
- [ ] 协议变化增加 golden vector 和基础重放/篡改测试。
- [ ] 无新私密日志、无本机路径、无未登记采集项。
- [ ] `START_HERE` 和 `CHANGELOG` 已更新。

### 每次交付

- [ ] source commit 明确，构建时工作区干净。
- [ ] server/client jar 均能生成，manifest 和 SHA256 完整。
- [ ] 目标平台至少完成一次启动和握手 smoke test。
- [ ] 同交付握手通过，跨交付拒绝通过。
- [ ] 错误 nonce、错误 GCM tag、重复 sequence 均被拒绝。
- [ ] 客户端 jar、manifest、日志和压缩包无私钥或玩家数据。
- [ ] 新交付公钥指纹唯一；普通升级未意外更换 keyId。
- [ ] 公开版不含 `private/`、私有依赖或私有策略。
- [ ] 交付包包含安装、升级和回退说明。
- [ ] 人能根据 manifest 在另一台机器执行 `verify` 并得到相同结果。

协议变更时增加至少 10,000 个畸形/变异输入的快速 fuzz；不需要为小团队设置形式化覆盖率 KPI，关键路径必须有断言和异常测试。

## 9. 泄露或破解后的最小处置

1. 将对应 manifest 标记为 `compromised`，停止该交付继续分发。
2. 只对该 `deliveryId` 执行 `rotate-key`，递增 keyId。
3. 重新构建 server/client，旧客户端会因信任锚不匹配而失效。
4. 验证其他交付的 keyId 和 artifact 均未变化。
5. 记录泄露来源、影响范围、是否需要修改共享检测逻辑。
6. 给受影响客户提供升级包和简短说明；其他客户不打扰。

若泄露的是共享代码而非私钥，评估是否需要修检测逻辑或调整公开/私有边界；不要把“换密钥”当成代码泄露的完整补救。

## 10. 常见风险与简化原则

| 风险 | 简化控制 |
|---|---|
| 多客户共用 master key | 构建时强制唯一公钥指纹，重复即失败 |
| 私钥进入客户端或 Git | 秘密目录隔离、扫描、manifest 不含秘密 |
| 每个客户一套代码 | 禁止长期客户分支，只保留配置和能力包差异 |
| 每次升级都换密钥 | 普通升级复用 keyId，只有泄露才 rotate |
| AI 自行发明新流程/新框架 | 只读最小文档；新流程必须替换旧流程而非叠加 |
| 测试过重导致不执行 | 按影响范围测试，交付固定执行握手、隔离和 secret scan |
| 交付无法追溯 | 强制 sourceCommit + buildId + manifest + artifact hash |
| 密钥丢失 | 受控备份；丢失时只重签该交付 |
| 混淆被误认为安全 | 文档明确：隔离靠密钥，混淆只增成本 |
| 公开版泄露私有实现 | allowlist 导出和独立公开构建 |

最终只保留四个习惯：**小步修改、相关测试、统一交付、留下 manifest**。其它流程只有在能解决实际问题时才增加。
