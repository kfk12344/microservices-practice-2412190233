# =============================================================================
# config-test.ps1 - PowerShell launcher for config-test.sh (Week 10)
#
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File scripts\config-test.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\config-test.ps1 -Build
#   powershell -ExecutionPolicy Bypass -File scripts\config-test.ps1 -Build -Test
# =============================================================================

[CmdletBinding()]
param(
    [switch]$Build,
    [switch]$Test
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
    Write-Host "No usable bash found." -ForegroundColor Red
    Write-Host "  Install Git for Windows: https://git-scm.com/download/win" -ForegroundColor White
    exit 2
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'config-test.sh'
if (-not (Test-Path -LiteralPath $scriptPath)) {
    Write-Host "Missing $scriptPath" -ForegroundColor Red
    exit 2
}

Push-Location $repoRoot
try {
    if ($Test) {
        $env:APP_ENV = 'test'
        $env:APP_ENV_OVERRIDE = 'test'
        Write-Host "Target environment: test" -ForegroundColor Yellow
    } else {
        $env:APP_ENV_OVERRIDE = 'dev'
        Write-Host "Target environment: dev" -ForegroundColor Yellow
    }

    if ($Build) {
        Write-Host ""
        Write-Host "== docker compose up -d --build ==" -ForegroundColor Cyan
        docker compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build/start failed." -ForegroundColor Red
            exit 2
        }
    }

    Write-Host ""
    & $bash 'scripts/config-test.sh'
    $code = $LASTEXITCODE
    Write-Host ""
    if ($code -eq 0) {
        Write-Host "config-test.sh passed (exit 0)" -ForegroundColor Green
    } else {
        Write-Host "config-test.sh failed (exit $code)" -ForegroundColor Red
    }
    exit $code
}
finally {
    Pop-Location
}
