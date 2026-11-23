# =============================================================================
# 一次跑完三个服务的单元测试，只输出一张紧凑汇总（适合直接截屏）
#
# 用法（仓库根目录）：
#     powershell -ExecutionPolicy Bypass -File scripts\test-all.ps1
#
# 为什么不直接跑三次 mvn：
#   Maven 每个服务会刷几十行 INFO 日志，三次下来窗口要滚三屏，
#   截图时还得拼。本脚本把日志收进变量，只提取真正的
#   「Tests run: ...」和「BUILD SUCCESS / FAILURE」，一屏放得下。
#
# 退出码：0 全部通过 / 1 有失败 / 2 环境问题
# =============================================================================

[CmdletBinding()]
param(
    # 只跑指定服务，例如 -Only meal-service
    [string[]]$Only
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo

function Find-Maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @(
            'D:\dev\apache-maven-3.9.16\bin\mvn.cmd',
            'C:\Program Files\Apache\maven\bin\mvn.cmd',
            "$env:USERPROFILE\apache-maven\bin\mvn.cmd")) {
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }
    $candidates = @()
    if (Test-Path 'C:\Program Files\Java') {
        $candidates = Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath "$c\bin\java.exe") { return $c }
    }
    return $null
}

$mvn = Find-Maven
if (-not $mvn) {
    Write-Host "找不到 mvn。请把 Maven 的 bin 目录加进 PATH，或修改本脚本里的路径。" -ForegroundColor Red
    exit 2
}
$javaHome = Find-JavaHome
if ($javaHome) { $env:JAVA_HOME = $javaHome }

$services = if ($Only) { $Only } else { @('user-service', 'food-service', 'meal-service') }

Write-Host ""
Write-Host ("=" * 76) -ForegroundColor DarkGray
Write-Host "  三个服务的单元测试（不依赖 Nacos / MySQL / 网络）" -ForegroundColor Cyan
Write-Host ("=" * 76) -ForegroundColor DarkGray
Write-Host ("  Java: {0}" -f $(if ($javaHome) { $javaHome } else { '(未找到 JAVA_HOME)' })) -ForegroundColor DarkGray
Write-Host ("  Maven: {0}" -f $mvn) -ForegroundColor DarkGray
Write-Host ""

$rows = @()
$allOk = $true

foreach ($svc in $services) {
    $pom = "services/$svc/pom.xml"
    if (-not (Test-Path -LiteralPath $pom)) {
        Write-Host ("  {0,-14} 跳过：找不到 {1}" -f $svc, $pom) -ForegroundColor Yellow
        continue
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $raw = & $mvn -B -f $pom test 2>&1
    $code = $LASTEXITCODE
    $sw.Stop()

    # 去掉 ANSI 颜色码再解析
    $lines = $raw | ForEach-Object { ("$_" -replace '\x1b\[[0-9;]*m', '') }

    $summary = $lines | Select-String -Pattern 'Tests run:\s*\d+,\s*Failures:\s*\d+,\s*Errors:\s*\d+,\s*Skipped:\s*\d+' |
        Select-Object -Last 1
    if (-not $summary) { $summary = $lines | Select-String -Pattern 'Tests run:' | Select-Object -Last 1 }

    $tests = 0; $fail = 0; $err = 0; $skip = 0
    if ($summary -and $summary.Line -match 'Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)') {
        $tests = [int]$Matches[1]; $fail = [int]$Matches[2]
        $err = [int]$Matches[3]; $skip = [int]$Matches[4]
    }

    $ok = ($code -eq 0) -and ($fail -eq 0) -and ($err -eq 0)
    if (-not $ok) { $allOk = $false }

    $rows += [pscustomobject]@{
        Service = $svc
        Tests   = $tests
        Fail    = $fail
        Errors  = $err
        Skipped = $skip
        Ok      = $ok
        Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        Line    = if ($summary) { $summary.Line.Trim() } else { '(未解析到 Tests run)' }
    }

    Write-Host ("  {0,-14}" -f $svc) -NoNewline
    if ($ok) {
        Write-Host ("  Tests run: {0}, Failures: {1}, Errors: {2}, Skipped: {3}    BUILD SUCCESS  ({4}s)" -f `
                $tests, $fail, $err, $skip, $rows[-1].Seconds) -ForegroundColor Green
    } else {
        Write-Host ("  失败（退出码 {0}）" -f $code) -ForegroundColor Red
    }
}

# ---------------------------------------------------------------- 汇总
$totalTests = ($rows | Measure-Object -Property Tests -Sum).Sum
$totalFail = ($rows | Measure-Object -Property Fail -Sum).Sum
$totalErr = ($rows | Measure-Object -Property Errors -Sum).Sum
$totalSec = ($rows | Measure-Object -Property Seconds -Sum).Sum

Write-Host ""
Write-Host ("=" * 76) -ForegroundColor DarkGray
Write-Host "  汇总" -ForegroundColor Cyan
Write-Host ("=" * 76) -ForegroundColor DarkGray
Write-Host ("  {0,-14} {1,6} {2,6} {3,6} {4,8}   {5}" -f `
        "服务", "测试", "失败", "错误", "耗时(s)", "结果") -ForegroundColor DarkGray
Write-Host ("  " + ("-" * 72)) -ForegroundColor DarkGray
foreach ($r in $rows) {
    $mark = if ($r.Ok) { "[OK]  " } else { "[FAIL]" }
    $color = if ($r.Ok) { 'Green' } else { 'Red' }
    Write-Host ("  {0,-14} {1,6} {2,6} {3,6} {4,8}   {5}" -f `
            $r.Service, $r.Tests, $r.Fail, $r.Errors, $r.Seconds, ($mark + " " + $(if ($r.Ok) { "BUILD SUCCESS" } else { "BUILD FAILURE" }))) -ForegroundColor $color
}
Write-Host ("  " + ("-" * 72)) -ForegroundColor DarkGray
$verdict = if ($allOk) { "全部通过" } else { "有失败" }
$vcolor = if ($allOk) { 'Green' } else { 'Red' }
Write-Host ("  {0,-14} {1,6} {2,6} {3,6} {4,8}   {5}" -f `
        "合计", $totalTests, $totalFail, $totalErr, $totalSec, $verdict) -ForegroundColor $vcolor
Write-Host ""
Write-Host ("  ↑ 这一屏就是截图 ⑥：Tests run 三个数字 + BUILD SUCCESS + 合计 {0} 个" -f $totalTests) -ForegroundColor Magenta
Write-Host ""

Pop-Location
if ($allOk) { exit 0 } else { exit 1 }
