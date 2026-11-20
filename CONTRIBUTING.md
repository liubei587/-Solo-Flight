# 参与贡献 / Contributing

感谢你对「飞了吗」感兴趣！这是一个纯本地、单人维护的 Android 小项目，欢迎 issue、讨论和 PR。

## 行为准则

- 尊重他人，就事论事；不讨论与项目无关的私人话题。
- 本项目记录的内容属于用户隐私，请勿在公开渠道分享他人的记录、截图或备份文件。

## 开发环境

| 依赖 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 34 |
| Android Build Tools | 34.0.0 |
| Gradle | 8.9（使用仓库自带 wrapper） |

本地只需要 `local.properties` 指向 SDK，**不需要**签名 keystore 即可构建 debug 版本：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 常用命令

```bash
# 单元测试（JVM，含 Robolectric 冒烟）
./gradlew :app:testDebugUnitTest

# 静态检查
./gradlew :app:lintDebug

# 构建 debug APK
./gradlew :app:assembleDebug

# 构建 release APK（需要本地 keystore.properties）
./gradlew :app:assembleRelease
```

Windows 下把 `./gradlew` 换成 `gradlew.bat` 即可。

## 代码结构

```
app/src/main/java/com/feilema/app/
├── core/          # 主题、通用 UI 组件、导航
├── data/          # Room、DataStore/SharedPreferences、仓库实现、提醒、图标
├── domain/        # 纯 Kotlin 模型、仓库接口、统计/导出/导入用例
└── feature/       # 按功能划分的页面（home/history/stats/settings/widget）
```

约定：

- UI 只渲染 `UiState`、只向外发事件；不在 Composable 里做业务计算。
- ViewModel 不持有 `Context`；Android 相关能力通过仓库/注入的类提供。
- Repository 接口在 `domain`，实现放 `data`；后续接入远端同步时只新增 RemoteDataSource。
- 统计口径集中在 `domain/usecase/stats/StatsCalculator.kt`，写成纯函数，便于固定 `Clock` 单测。
- 新增用户可见文案使用中文；不要新增网络权限（项目坚持 local-first）。
- 数据层只存「事件」，不存每日次数等派生数据。

## 测试要求

- 新增/修改统计计算、卡片布局、导入解析、裁剪几何等纯逻辑时，必须在 `src/test` 里补 JVM 单测。
- 单测不要依赖系统当前时间：通过构造 `Instant`/`Clock` 固定时间。
- 涉及 Compose 页面或 Room 的改动，可补充 `src/androidTest` 的 instrumented 测试（需要设备或模拟器）。
- 提 PR 前请确认以下命令通过：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 提交与 PR

- 使用 Conventional Commits 风格：`feat(stats): …`、`fix(home): …`、`docs: …`、`chore(ci): …`。
- 一个 PR 只做一件事；大改动请先开 issue 讨论。
- 提交信息与 PR 描述使用中文或英文均可，但需说清动机与行为变化。
- PR 模板中列出的自检项请逐条确认；CI 未通过不会合并。

## 报告问题

- 使用 issue 模板（Bug 报告 / 功能建议），提供版本、设备、复现步骤。
- **不要在 issue 中粘贴密码、keystore、备份文件内容或任何真实记录。**
- 安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要公开开 issue。

## 发布

版本发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 许可证

贡献的代码默认以 [GPL-3.0](../LICENSE) 授权。
