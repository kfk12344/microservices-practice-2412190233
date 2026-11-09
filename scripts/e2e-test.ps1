# =============================================================================
# e2e-test.sh 的 PowerShell 启动器（Windows）
#
# 为什么需要它：
#   Windows 上 `bash` 可能指向 WSL 的转发器（C:\Windows\System32\bash.exe），
#   如果没装 WSL 发行版，直接 `bash xxx.sh` 会报：
#       <3>WSL (xxxxx - Relay) ERROR: CreateProcessCommon:818:
#       execvpe(/bin/bash) failed: No such file or directory
#   本脚本自动去找 Git for Windows 自带的 bash 并把参数原样转发过去。
#
# 用法（仓库根目录）：
#     powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
#     powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1 -Build
#     $env:SKIP_DOCKER=1; powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
#
# 参数：
#     -Build    先 docker compose up -d --build 再验证（改了代码后用）
#
# 退出码与 e2e-test.sh 一致：0 通过 / 1 有失败 / 2 前置不满足
# =============================================================================

[CmdletBinding()]
param(
    [switch]$Build
)

$ErrorActionPreference = 'Continue'

# ---------------------------------------------------------------- 找 bash
function Find-Bash {
    # 1) 优先 Git for Windows（本机实测在 D:\Git）
    $candidates = @()
    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCmd) {
        $gitRoot = Split-Path -Parent (Split-Path -Parent $gitCmd.Source)
        $candidates += (Join-Path $gitRoot 'bin\bash.exe')
        $candidates += (Join-Path $gitRoot 'usr\bin\bash.exe')
    }
    foreach ($p in @(
            'C:\Program Files\Git\bin\bash.exe',
            'C:\Program Files (x86)\Git\bin\bash.exe',
            'D:\Git\bin\bash.exe')) {
        $candidates += $p
    }

    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) {
            # 试探一下：Git Bash 能跑 `echo`，WSL 转发器不能
            $probe = & $c -c 'echo ok' 2>&1
            if ("$probe".Trim() -eq 'ok') {
                return $c
            }
        }
    }
    return $null
}

$bash = Find-Bash
if (-not $bash) {
    Write-Host ""
    Write-Host "找不到可用的 bash。" -ForegroundColor Red
    Write-Host "  说明：Windows 自带的 C:\Windows\System32\bash.exe 是 WSL 转发器，" -ForegroundColor Gray
    Write-Host "        没装 WSL 发行版时跑不了 .sh 脚本。" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  解决办法二选一：" -ForegroundColor Yellow
    Write-Host "    1) 安装 Git for Windows（自带 bash）：https://git-scm.com/download/win" -ForegroundColor White
    Write-Host "    2) 如果已经装了 Git，手动指定路径运行：" -ForegroundColor White
    Write-Host '       D:\Git\bin\bash.exe scripts/e2e-test.sh' -ForegroundColor White
    Write-Host ""
    exit 2
}

Write-Host "使用 bash: $bash" -ForegroundColor DarkGray

# ---------------------------------------------------------------- 定位仓库根目录
$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'e2e-test.sh'
if (-not (Test-Path -LiteralPath $scriptPath)) {
    Write-Host "找不到 $scriptPath" -ForegroundColor Red
    exit 2
}

Push-Location $repoRoot
try {
    # ------------------------------------------------------------ 可选：先重建镜像
    if ($Build) {
        Write-Host ""
        Write-Host "== docker compose up -d --build ==" -ForegroundColor Cyan
        docker compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            Write-Host "镜像构建或启动失败" -ForegroundColor Red
            exit 2
        }
    }

    # ------------------------------------------------------------ 跑 e2e
    Write-Host ""
    & $bash 'scripts/e2e-test.sh'
    $code = $LASTEXITCODE
    Write-Host ""
    if ($code -eq 0) {
        Write-Host "e2e-test.sh 全部通过（退出码 0）" -ForegroundColor Green
    } else {
        Write-Host "e2e-test.sh 未通过（退出码 $code）" -ForegroundColor Red
    }
    exit $code
}
finally {
    Pop-Location
}
