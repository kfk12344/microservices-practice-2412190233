﻿# =============================================================================
# auth-test.sh 的 PowerShell 启动器（Windows）
#
# 与 e2e-test.ps1 同样的目的：Windows 上 `bash` 可能指向 WSL 转发器，
# 这里自动找 Git for Windows 自带的 bash，再把参数转发过去。
#
# 用法（仓库根目录）：
#     powershell -ExecutionPolicy Bypass -File scripts\auth-test.ps1
#     powershell -ExecutionPolicy Bypass -File scripts\auth-test.ps1 -Build
# =============================================================================

[CmdletBinding()]
param(
    [switch]$Build
)

$ErrorActionPreference = 'Continue'

function Find-Bash {
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
    Write-Host "  1) 安装 Git for Windows（自带 bash）：https://git-scm.com/download/win" -ForegroundColor White
    Write-Host '  2) 或手动运行：D:\Git\bin\bash.exe scripts/auth-test.sh' -ForegroundColor White
    Write-Host ""
    exit 2
}

Write-Host "使用 bash: $bash" -ForegroundColor DarkGray

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'auth-test.sh'
if (-not (Test-Path -LiteralPath $scriptPath)) {
    Write-Host "找不到 $scriptPath" -ForegroundColor Red
    exit 2
}

Push-Location $repoRoot
try {
    if ($Build) {
        Write-Host ""
        Write-Host "== docker compose up -d --build ==" -ForegroundColor Cyan
        docker compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            Write-Host "镜像构建或启动失败" -ForegroundColor Red
            exit 2
        }
    }

    Write-Host ""
    & $bash 'scripts/auth-test.sh'
    $code = $LASTEXITCODE
    Write-Host ""
    if ($code -eq 0) {
        Write-Host "auth-test.sh 全部通过（退出码 0）" -ForegroundColor Green
    } else {
        Write-Host "auth-test.sh 未通过（退出码 $code）" -ForegroundColor Red
    }
    exit $code
}
finally {
    Pop-Location
}
