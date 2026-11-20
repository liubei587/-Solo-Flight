# Pull Request

## 改动说明

<!-- 这个 PR 做了什么？为什么需要？关联的 issue：Fixes #123 -->

## 改动类型

- [ ] 新功能（feature）
- [ ] 问题修复（fix）
- [ ] 重构 / 性能（refactor / perf）
- [ ] 文档 / 工程（docs / chore）
- [ ] 其他：

## 自检清单

- [ ] 本地 `./gradlew :app:testDebugUnitTest` 通过
- [ ] 本地 `./gradlew :app:lintDebug` 没有新增 error
- [ ] `./gradlew :app:assembleDebug` 可以成功构建
- [ ] 涉及统计口径 / 卡片排版 / 导入解析的改动，补了对应的 JVM 测试
- [ ] 没有提交 `keystore.properties`、`local.properties`、`*.jks` 或任何真实用户数据
- [ ] 用户可见文案保持中文（README 同时保留英文说明）
- [ ] 如涉及版本发布，已在 `CHANGELOG.md` 记录并同步版本号

## 截图 / 录屏（可选）

<!-- 界面上有变化的 PR 建议附上前后对比 -->
