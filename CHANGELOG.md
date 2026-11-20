# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的结构，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 计划中

- 统计卡片更多可选指标（如记录率趋势、按月对比）。
- 数据完整性校验（导入后提示预览与冲突数量）。
- 桌面小组件更多尺寸。

## [0.7.3] - 2026-11-21

### 修复

- 导入备份时文件选择器把导出的 JSON/CSV 识别为未知类型而置灰：改为接受所有文件，并在提示中说明可切换到「全部文件」。
- 时段分布/周内分布柱状图缺少横轴标签：现在始终显示「周X」「HH时」标签，并按宽度自动抽稀。

## [0.7.2] - 2026-11-20

### 新增

- 设置页「导入备份（JSON / CSV）」：支持本应用导出的两种格式，自动跳过重复记录，并统计无效条目。
- 统计卡片：周内分布、时段分布图表补齐次数纵轴刻度与网格。
- 趋势图：增加日期横轴与次数纵轴。

### 修复

- 柱状图入场动画在数据点较多时几乎不可见；改为错峰生长动画。
- 箱线图统计窗口改为最近半年，避免按天统计被极端值支配。

## [0.7.1] - 2026-11-20

### 修复

- 首页日历：今天不再用实心圆强调，改为「有记录就圈出」，并在日期右上角显示当日次数。
- 趋势图坐标轴、柱状图坐标轴细节修正。

## [0.7.0] - 2026-11-20

### 新增

- 统计卡片编辑模式：长按进入、拖动排序（带实时落位预览与其他卡片避让）、滚轮选择尺寸（1×1 / 2×1 / 2×2）、右上角删除、底部增加卡片、完成/退出确认。
- 首页月历：翻月查看，记录日以主题色圈出。
- 卡片网格自动补位与防重叠逻辑。

### 移除

- 移除「高的卡片」（1×2）尺寸。

## [0.6.9] - 2026-11-17

### 安全

- 轮换签名密钥并重新签名 APK；旧仓库中泄露的 `keystore.properties` / `local.properties` / `release-keystore.jks` 已通过重建干净历史移除。

### 新增

- GitHub Release 分发：APK 与 SHA-256 校验文件，README 增加下载段落与 Release 徽章。

## [0.6.0] - 2026-11-14

### 新增

- 首页「本周已飞」大卡替换旧版单日卡。
- 统计页卡片编辑（拖拽、尺寸、增删、保存确认）。
- 主题色扩展、自定义背景图。

## [0.5.0] - 2026-11-14

### 新增

- 暗色模式切换与主题色引擎（对比度安全配色）。
- 统计卡片磨砂玻璃样式（后续版本移除）。
- 首页与统计页改用主题色渐变。

### 修复

- 底部导航首次点击不切换、暗色模式部分文字仍为黑色的问题。

## [0.4.0] - 2026-11-13

### 新增

- 统计卡片自定义：图表/指标可增删排序，尺寸可选 1 列 / 2 列。
- 主题色预设与自定义取色（HSV 轮盘）、自定义背景、桌面图标随主题色变化。

### 修复

- 底部导航首次点击无响应。
- 统计指标卡片在窄屏上内容截断。

## [0.3.0] - 2026-11-13

### 新增

- 暗色模式。
- 模糊与动效 UI：首页渐变 Hero 卡、数字滚动、按钮按压反馈、页面过渡。
- 统计时间段切换（图表范围 / 指标范围分开），环比指标。

## [0.2.0] - 2026-11-13

### 新增

- 设置页：数据概况、导出 CSV/JSON、每日提醒、隐私说明。
- 每日提醒（通知渠道 + AlarmManager + 开机重排）。
- release 签名构建。

### 修复

- 统计口径与图表细节。

## [0.2.1] - 2026-11-13

### 新增

- 自适应启动图标。
- 桌面小组件在应用内记录后立即刷新。

## [0.2.2] - 2026-11-13

### 新增

- 真机 instrumented 测试（Room DAO、无状态 Compose 页面、MainActivity 冒烟）。
- `tools/smoke.ps1` 装机冒烟脚本。

## [0.1.0] - 2026-11-13

### 新增

- 首个可安装版本：首页一键记录/撤销、历史时间线（补录、改时间、删除）、Room 本地存储、统计基础指标。

[Unreleased]: https://github.com/liubei587/-Solo-Flight/compare/v0.7.3...HEAD
[0.7.3]: https://github.com/liubei587/-Solo-Flight/compare/v0.7.2...v0.7.3
[0.7.2]: https://github.com/liubei587/-Solo-Flight/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/liubei587/-Solo-Flight/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.6.9...v0.7.0
[0.6.9]: https://github.com/liubei587/-Solo-Flight/compare/v0.6.0...v0.6.9
[0.6.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.2.2...v0.3.0
[0.2.2]: https://github.com/liubei587/-Solo-Flight/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/liubei587/-Solo-Flight/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/liubei587/-Solo-Flight/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/liubei587/-Solo-Flight/releases/tag/v0.7.3
