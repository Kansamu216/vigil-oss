# Vigil OSS 当前交接页

## 当前状态

- 版本：`0.1.0-dev.0`
- 阶段：公开稳定子集骨架，未实现反作弊业务逻辑。
- 公开范围见 `docs/public-scope.md`，隐私约束见 `docs/privacy.md`。
- 最近公开交付：无。

## 下一步

- [ ] 配合主线完成 M0 空 Mod 与公开构建验证，不提前加入私有采集能力。

## 常用命令

```bash
./gradlew build
./gradlew -p platforms/neoforge-1.21.1 build
./gradlew -p platforms/forge-1.20.1 build
./gradlew -p platforms/fabric-1.21.1 build
```

## 禁止事项

- 不加入 `private/`、私有依赖、全桌面截图、硬件账号关联或深度扫描。
- 不提交密钥、玩家数据、数据库、截图或本机路径。
- 共享代码修改先回port 私有主线，不在公开仓长期独立演进。
- 未运行相关测试不得交付。

## 接手顺序

阅读 `START_HERE.md` → `DEVELOPMENT.md` → `docs/simple-delivery.md`。完成后更新本页和 `CHANGELOG.md`。
