# 飞了吗 · 真机冒烟脚本
# 兼容 Windows PowerShell 5.1 与 PowerShell 7；不需要管理员权限。
<#
  用法：
    powershell -ExecutionPolicy Bypass -File tools\smoke.ps1
    powershell -ExecutionPolicy Bypass -File tools\smoke.ps1 -Apk app\build\outputs\apk\release\app-release.apk
    pwsh tools/smoke.ps1                  # PowerShell 7
    pwsh tools/smoke.ps1 -Apk app\build\outputs\apk\release\app-release.apk

  做四件事：安装 APK → 启动 MainActivity → 等 3 秒查崩溃日志 → 截屏存到 build\smoke\。
  退出码：0 通过；2 没有设备/找不到 APK；3 安装失败；4 启动或崩溃检查失败。
  注意：脚本只验证“能装、能启动、不崩”，记录/统计/通知/小组件/导出仍需人工点一遍。
#>
param(
    [string]$Apk,
    [string]$Adb
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$sdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    ""
}

if (-not $Adb -and $sdk) {
    $candidate = Join-Path $sdk "platform-tools\adb.exe"
    if (Test-Path $candidate) { $Adb = $candidate }
}
if (-not $Adb) {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { $Adb = $command.Source }
}
if (-not $Adb -or -not (Test-Path $Adb)) {
    Write-Host "[fail] 找不到 adb，请设置 ANDROID_HOME / ANDROID_SDK_ROOT，或用 -Adb 指定完整路径。"
    exit 2
}

if (-not $Apk) {
    $debug = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
    $release = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $debug) {
        $Apk = $debug
    } elseif (Test-Path $release) {
        $Apk = $release
    }
}
if (-not $Apk -or -not (Test-Path $Apk)) {
    Write-Host "[fail] 找不到 APK，请先构建，或用 -Apk 指定路径。"
    exit 2
}
$Apk = (Resolve-Path $Apk).Path

$devices = @(& $Adb devices | Select-String -Pattern "^\S+\s+device$")
if ($devices.Count -eq 0) {
    Write-Host "[skip] 没有已授权的 Android 设备。"
    Write-Host "       步骤：手机开「开发者选项 → USB 调试」→ 数据线连电脑 → 手机上点「允许调试」。"
    & $Adb devices
    exit 2
}
Write-Host "[1/6] 设备：$($devices -join ' | ')"

Write-Host "[2/6] 安装 $Apk"
$install = & $Adb install -r $Apk 2>&1
$install | ForEach-Object { Write-Host "  $_" }
if (-not ($install -match "Success")) {
    Write-Host "[fail] 安装失败。"
    exit 3
}

& $Adb logcat -c 2>$null | Out-Null

Write-Host "[3/6] 启动 com.feilema.app/.MainActivity"
$start = & $Adb shell am start -W -n com.feilema.app/.MainActivity 2>&1
$start | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) {
    Write-Host "[fail] am start 命令失败。"
    exit 4
}
Start-Sleep -Seconds 2

$pidText = (& $Adb shell pidof com.feilema.app 2>$null | Out-String).Trim()
if (-not $pidText) {
    Write-Host "[fail] 应用进程没有起来。"
    exit 4
}
Write-Host "[4/6] 进程 pid=$pidText"

Start-Sleep -Seconds 3
$crash = & $Adb logcat -d -s AndroidRuntime:E 2>$null | Select-String -Pattern "FATAL EXCEPTION"
if ($crash) {
    Write-Host "[fail] 检测到崩溃："
    $crash | ForEach-Object { Write-Host "  $($_.Line)" }
    exit 4
}

$resumed = & $Adb shell dumpsys activity activities 2>$null |
    Select-String -Pattern "mResumedActivity.*com.feilema.app"
if ($resumed) {
    Write-Host "[5/6] 前台：$($resumed.Line.Trim())"
} else {
    Write-Host "[warn] 没看到前台 Activity，请人工确认。"
}

$shotDir = Join-Path $root "build\smoke"
New-Item -ItemType Directory -Force -Path $shotDir | Out-Null
$shot = Join-Path $shotDir ("home-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".png")
try {
    $process = Start-Process -FilePath $Adb `
        -ArgumentList @("exec-out", "screencap", "-p") `
        -RedirectStandardOutput $shot -NoNewWindow -PassThru -Wait
    if ($process.ExitCode -ne 0 -or -not (Test-Path $shot) -or (Get-Item $shot).Length -lt 1000) {
        throw "截图输出异常（exit=$($process.ExitCode)）"
    }
    Write-Host "[6/6] 截图：$shot"
} catch {
    Write-Host "[warn] 截图失败（不影响结论）：$_"
}

Write-Host ""
Write-Host "冒烟通过：安装成功、启动成功、无崩溃。"
Write-Host "人工验收清单：记录一次 → 撤销 → 历史补录/改时间/删除 → 统计图表 → 设置导出 CSV/JSON → 加桌面小组件 → 提醒开关。"
exit 0
