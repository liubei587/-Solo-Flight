# 发布流程 / Releasing

本文说明如何发布一个「飞了吗」新版本。发布有两种方式：**自动（推荐）** 和手动兜底。

## 0. 一次性准备：签名密钥与仓库 Secrets

签名密钥和密码**永远不要提交到仓库**。GitHub Actions 通过 Repository Secrets 获取：

仓库 → Settings → Secrets and variables → Actions → New repository secret，添加：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `release-keystore.jks` 的 base64（单行） |
| `KEYSTORE_STORE_PASSWORD` | keystore 的 storePassword |
| `KEYSTORE_KEY_ALIAS` | key alias（本仓库目前用 `feilema`） |
| `KEYSTORE_KEY_PASSWORD` | key 的 keyPassword |

生成 base64：

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks")) | Set-Clipboard
```

```bash
# Linux / macOS
base64 -w0 release-keystore.jks
```

> 注意：粘贴到 GitHub Secrets 时只保留一行，不要带换行。

## 1. 升级版本号

编辑 `app/build.gradle.kts`：

```kotlin
versionCode = 24          // 每次发布 +1（内部整数）
versionName = "0.7.4"     // 展示给用户的语义化版本
```

## 2. 更新 CHANGELOG

把 `CHANGELOG.md` 顶部的 `[Unreleased]` 改为本次版本号与日期，并补一条 compare 链接：

```markdown
## [0.7.4] - 2026-11-22
### 新增
- ...
```

## 3. 本地验证

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug :app:assembleRelease
```

- 单测应全部通过（当前 49 个）；
- lint 不应新增 error；
- release APK 生成在 `app/build/outputs/apk/release/app-release.apk`；
- 可选：`apksigner verify --print-certs` 检查签名，`sha256sum` 记录哈希。

## 4. 提交并打 tag

```bash
git add -A
git commit -m "chore(release): bump version to 0.7.4"
git tag -a v0.7.4 -m "飞了吗 v0.7.4"
git push origin main
git push origin v0.7.4
```

推送 tag 会自动触发 `.github/workflows/release.yml`：

1. 从 Secrets 还原 keystore 与 `keystore.properties`；
2. `assembleRelease`，用 apksigner 校验；
3. 生成 `feilema-<version>.apk` 与 `.apk.sha256`；
4. 创建 GitHub Release（自动生成 release notes，附 APK 与校验文件）。

在 Actions 页面可以看到运行结果；Release 页面确认资产已上传。

## 5. 手动兜底发布（Actions 不可用时）

1. 本地构建 release APK（需要 `keystore.properties` + `release-keystore.jks`）；
2. 计算哈希：
   ```bash
   sha256sum app/build/outputs/apk/release/app-release.apk > feilema-0.7.4.apk.sha256
   ```
3. 打开仓库 Releases → Draft a new release，选择/创建 tag `v0.7.4`，上传 APK 与 `.sha256`，填写更新说明并发布；
4. 校验下载链接可访问。

## 6. Release 之后

- 检查 Release 资产的 SHA-256 与本地构建一致；
- README 的版本徽章/下载段落无需改动（指向 latest release）；
- 若本次包含数据库迁移或统计口径变化，在 Release 说明里提示「建议导出备份后再升级」。

## 7. 密钥轮换（仅在泄露/丢失时）

Android 不允许用不同密钥覆盖安装同一包名，所以轮换会带来升级问题：

1. 用 `keytool` 生成新 keystore（RSA 2048，长期有效）；
2. 更新本地 `keystore.properties` 与 GitHub Secrets 的四个值；
3. 提升 `versionCode`，重新构建并发布；
4. 在 Release 说明中明确告知用户：**需要卸载旧版本后安装新 APK**（数据可通过导出的 JSON/CSV 备份再导入）；
5. 轮换后旧 APK 不再能自动升级，请保留旧 Release 供老用户导出数据。

## 8. 常见问题

- **Actions 报缺少签名 Secrets**：按第 0 步补齐；`KEYSTORE_BASE64` 为空时 workflow 会直接失败并提示。
- **base64 解码失败**：检查 secret 里是否混入换行/空格。
- **push 被代理阻断**：本地 git 已配置 `http.proxy`/`https.proxy`；CI 在 GitHub 上运行不需要代理。
- **APK 安装提示签名冲突**：说明手机上装的是旧密钥版本，先卸载（数据先导出备份）再安装。
