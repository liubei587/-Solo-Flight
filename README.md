# 飞了吗 · FeiLeMa

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local-first](https://img.shields.io/badge/Data-Local--only-critical)

> 纯本地记录并统计某种「独自起飞」的小习惯。A local-first tracker for a certain solo habit — no cloud, no accounts, just your own rhythm.

「飞了吗」是一个只给自己看的记录 + 统计小工具：什么时候飞的、飞了几次、最近节奏怎么样。
所有数据默认只保存在手机本地，不申请网络权限，也没有账号和云同步。

FeiLeMa ("Did You Fly?") is a tiny Android app for logging and reflecting on a certain private solo habit — when it happened, how often, and what your rhythm looks like. Local-first by design: no network permission, no accounts, no analytics.

> 名字是个心照不宣的暗号，点到为止。

## 功能特性

### 记录
- 首页一键记录，显示「本周已飞」和「今天 N 次」，支持撤销上一次
- 历史时间线按天分组：补录、修改发生时间、删除，空白日一眼可见
- 每次记录都保存真实发生时间（`occurredAt`），补录不会污染历史统计

### 统计
- 今日 / 本周 / 总次数、日均、活跃日均、连续天数、平均间隔、间隔中位数
- 环比环形图、近 7 / 30 / 90 天或全部的趋势折线（含均值线）
- 时段分布、周内分布，以及按「每周次数」统计的箱线图
- 图表入场与切换动效；图表时段与指标口径可以分别切换

### 自定义
- 统计卡片编辑模式：长按进入，拖拽排序（实时落位预览、其他卡片避让、松手弹簧落位、拖到列表边缘自动滚动）
- 卡片尺寸 1×1 / 2×1 / 1×2 / 2×2，滚轮选择；可删除与重新添加卡片，布局持久化
- 外观：跟随系统 / 浅色 / 深色；7 套预设主题色 + HSV 自定义取色；桌面图标跟随主题色
- 自定义背景图，选择后可裁剪出想要的区域

### 其他
- 每日提醒（AlarmManager + 通知，重启后自动恢复）
- CSV / JSON 导出（系统文件选择器，写到用户自己选的位置）
- 桌面小组件：显示今日次数，点一下直接记录
- 无网络权限、无账号、无统计埋点

### Features (English)
- One-tap logging, weekly hero card, undo, and a day-grouped timeline with backfill / edit / delete
- Stats: daily & active averages, streaks, average/median interval, change ring, trend line, hour-of-day and weekday distributions, weekly box plot
- Fully editable stats dashboard: long-press to edit, drag to reorder with live preview, resize cards (1×1 / 2×1 / 1×2 / 2×2), add/remove cards, persisted layout
- Appearance: light/dark/system, seven preset palettes plus a custom HSV color wheel, launcher icon follows the theme, custom background with cropping
- Daily reminder, CSV/JSON export, Glance home-screen widget
- Local-first: no network permission, no accounts, no analytics

## 技术栈

- Kotlin 1.9.24 + Jetpack Compose（Material 3）
- AGP 8.5.2 · Gradle 8.9 · compileSdk 34 · targetSdk 34 · minSdk 26
- Hilt（依赖注入）、Room（事件流数据库）、Coroutines + Flow
- Navigation Compose、Jetpack Glance（桌面小组件）
- Gradle KTS + Version Catalog
- 测试：JUnit 4、Robolectric、Compose UI Test

## 架构

单 module、feature-first 分包、MVVM + 单向数据流：UI 只读取 UiState、向上发事件，ViewModel 不持有 Android Context；Repository 接口在 `domain`，Room 实现在 `data`；统计口径是纯 Kotlin（固定 `Clock`，可直接 JVM 测试）。

```
app/src/main/java/com/feilema/app/
├── core/        # 主题与配色、导航、通用组件（背景、滚动数字、取色器）
├── data/        # Room 实体/DAO/数据库、Repository 实现、偏好设置、提醒、桌面图标切换
├── domain/      # 模型、Repository 接口、统计 / 导出 / 提醒的纯计算逻辑
├── feature/     # home / history / stats / settings / widget
└── di/          # Hilt 模块
```

底部 4 个页面：今日（记录）、历史（时间线）、统计（图表与编辑模式）、设置（外观 / 数据 / 提醒）。

## 数据模型

存事件，不存次数：

```
behavior_events
├── id           PK
├── typeId       预留多行为（当前固定为 1）
├── occurredAt   行为真实发生时间（允许补录）
├── createdAt    写入数据库时间
└── note         备注（可选）
```

每一行代表「发生了一次」。今日 / 本周 / 趋势 / 分布等全部由 SQL 按时间范围实时聚合，不落统计表；后续数据量很大时再考虑按天/周的聚合缓存。

## 统计口径

- 今日 / 本周：按本地时区自然日，周首为周一
- 日均：总次数 ÷ 首条记录至今天数（含没有记录的天）
- 活跃日均：总次数 ÷ 有记录的天数
- 连续天数：从今天（今天没记则从昨天）向前回溯
- 平均间隔 / 间隔中位数：相邻两次记录的时间差
- 环比：与上一个等长周期的次数比较，环形图显示变化幅度
- 趋势：近 7 / 30 / 90 天或全部，跨度较长时按周 / 月聚合
- 分布：按小时、按星期（周一至周日）
- 箱线图：以「每周次数」为一个数据点，避免一天记录多次造成的长须与离群点

## 构建

需要 JDK 17 和 Android SDK 34（命令行 SDK 即可，不强制 Android Studio）。

```bash
# 调试包
./gradlew :app:assembleDebug
# Windows
gradlew.bat :app:assembleDebug

# JVM 单元测试
./gradlew :app:testDebugUnitTest

# 签名 release 包（可选，见下）
./gradlew :app:assembleRelease
```

SDK 路径通过环境变量 `ANDROID_HOME` 指定，或在仓库根目录创建 `local.properties`（已被 `.gitignore` 忽略）：

```properties
sdk.dir=/path/to/Android/Sdk
```

release 签名是可选步骤。在仓库根目录放一个 `keystore.properties`（同样已被 `.gitignore` 忽略，不要提交）：

```properties
storeFile=your-release-keystore.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

## 测试

JVM 测试（不需要设备，39 个）：

```bash
./gradlew :app:testDebugUnitTest
# 报告：app/build/reports/tests/testDebugUnitTest/index.html
```

覆盖范围：

- 统计口径 12 个（固定 Clock 的日均、连续天数、间隔、趋势、时段/周内分布、按周箱线图、时间段切分、环比）
- CSV / JSON 导出格式 7 个
- 提醒时间计算 4 个
- 统计卡片网格位置与改尺寸补位 11 个
- 背景裁剪几何 4 个
- Robolectric 启动真实 `MainActivity` 的导航冒烟 1 个（Hilt + Room + Compose 全链路，验证打开不崩、四个 tab 可切换）

真机 instrumented 测试（需要设备，3 个文件）：

```bash
./gradlew :app:connectedDebugAndroidTest
```

- `BehaviorEventDaoTest`：内存 Room，验证统计半开区间、排序、修改/删除后聚合
- `HomeScreenTest`：无状态首页的展示与回调
- `MainActivitySmokeTest`：真实 Application 启动 + 四个 tab 切换

只验证「能装、能启动、不崩」也可以跑装机冒烟脚本：

```powershell
powershell -ExecutionPolicy Bypass -File tools\smoke.ps1
```

脚本会安装、启动、等几秒扫描 `AndroidRuntime` 崩溃日志并把截图存到 `build/smoke/`；需要 `adb`（在 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 下，或用 `-Adb` 指定）。

## 隐私

- 默认纯本地存储，不申请 `INTERNET` 权限
- 没有账号、没有云同步、没有统计埋点
- 导出 CSV / JSON 由用户主动触发，文件写到用户自己选择的位置
- 卸载或清除应用数据会删除全部记录（重要数据请自行导出备份）

## 已知限制

- 目前只支持记录一种行为（`typeId` 已预留，多行为尚未开放）
- 最低 Android 8.0（API 26）
- 数据未加密，也没有自动云备份；换机前请先导出
- 本期不做应用锁
- 早期版本的「液态玻璃」卡片外观因为观感问题已移除，卡片统一为简洁磨砂渐变

## 路线图

- 多行为管理、备注结构化（地点 / 时长 / 单位）
- 一键备份与恢复（本地文件）
- 更多桌面小组件尺寸与快捷方式
- 可选的应用锁

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 许可，详见仓库根目录的 `LICENSE` 文件。

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). See the `LICENSE` file for details.

## 免责声明

本项目是个人向的小工具，不提供任何医疗或健康建议；名称只是一个心照不宣的暗号，请根据自己的使用场景自行判断。数据默认只在你自己的设备上，请自行做好备份。
