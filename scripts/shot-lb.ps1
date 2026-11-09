# =============================================================================
# 专门用来截「负载均衡分发证据」这张图
#
# 一条命令跑完，直接把结果排版好打印出来，你只需要 Ctrl+滚轮放大后截屏。
#
# 用法（仓库根目录）：
#     powershell -ExecutionPolicy Bypass -File scripts\shot-lb.ps1
#
# 它会打印两段证据：
#   ① GET /api/meals/instances  —— 两个实例各自收到多少次请求
#   ② docker ps                 —— 2 号实例只有容器内端口，没有占宿主端口
# 最后给出一行 PASS/FAIL 结论，截图里带上这行最直观。
# =============================================================================

[CmdletBinding()]
param(
    # 下单次数：次数越多，两个实例都被打到的概率越高
    [int]$Rounds = 8,
    [int]$UserId = 1
)

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8083'

function Write-Head($text) {
    Write-Host ""
    Write-Host ("=" * 66) -ForegroundColor DarkGray
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ("=" * 66) -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- 0. 前置
Write-Head "0. 前置检查"

$health = $null
try {
    $health = Invoke-RestMethod "$base/actuator/health" -TimeoutSec 5
} catch {
    Write-Host "  meal-service 不可达：先执行 docker compose up -d，等 40~60 秒" -ForegroundColor Red
    exit 2
}
Write-Host "  meal-service 状态：$($health.status)" -ForegroundColor Green

# ---------------------------------------------------------------- 1. 清空统计
Write-Head "1. 清空旧的命中统计"

Invoke-RestMethod -Uri "$base/api/meals/instances" -Method Delete -TimeoutSec 10 | Out-Null
Write-Host "  DELETE /api/meals/instances -> 204（已清空）" -ForegroundColor Green

# ---------------------------------------------------------------- 2. 打 N 轮
Write-Head "2. 连续下单 $Rounds 次（每次都跨服务校验用户与两种食物）"

$ok = 0
$fail = 0
for ($i = 1; $i -le $Rounds; $i++) {
    # 用餐时间每次差 1 分钟，避免撞上"同人同餐次同时间不可重复提交"的规则
    $eatenAt = ([datetime]'2026-11-20T12:00:00').AddMinutes($i).ToString('yyyy-MM-ddTHH:mm:ss')
    $body = @{
        userId   = $UserId
        mealType = 'LUNCH'
        eatenAt  = $eatenAt
        note     = "LB shot #$i"
        items    = @(
            @{ foodId = 1; portionGrams = 100 },   # 米饭
            @{ foodId = 3; portionGrams = 100 }    # 鸡胸肉
        )
    } | ConvertTo-Json -Depth 5 -Compress

    try {
        $null = Invoke-RestMethod -Uri "$base/api/meals" -Method Post `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
        $ok++
    } catch {
        $fail++
        Write-Host "  第 $i 次失败：$($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host "  成功 $ok 次，失败 $fail 次" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

# ---------------------------------------------------------------- 3. 分发统计
Write-Head "3. 证据一：GET /api/meals/instances（真实业务调用的分发情况）"

$hits = Invoke-RestMethod "$base/api/meals/instances" -TimeoutSec 10
$json = $hits | ConvertTo-Json -Depth 5
Write-Host ""
Write-Host $json -ForegroundColor White
Write-Host ""

$userCount = 0
$foodCount = 0
if ($json -match '"user-service"\s*:\s*\{([^}]*)\}') {
    $userCount = ([regex]::Matches($Matches[1], ':\s*\d+')).Count
}
if ($json -match '"food-service"\s*:\s*\{([^}]*)\}') {
    $foodCount = ([regex]::Matches($Matches[1], ':\s*\d+')).Count
}

# ---------------------------------------------------------------- 4. 实例端口
Write-Head "4. 证据二：docker ps（2 号实例只 expose，不占宿主端口）"

$lines = docker ps --format "{{.Names}}  {{.Status}}  {{.Ports}}" |
    Where-Object { $_ -match 'user-service|food-service' }
Write-Host ""
foreach ($ln in $lines) {
    Write-Host "  $ln" -ForegroundColor White
}
Write-Host ""
Write-Host "  注：hdt-user-service-2 / hdt-food-service-2 的端口列只有 8081/tcp、8082/tcp，" -ForegroundColor DarkGray
Write-Host "      没有 0.0.0.0:8081->8081 这种映射 —— 两个实例不会抢同一个宿主端口。" -ForegroundColor DarkGray

# ---------------------------------------------------------------- 5. 结论
Write-Head "5. 结论"

$pass = ($userCount -ge 2) -and ($foodCount -ge 2) -and ($fail -eq 0)
Write-Host ""
Write-Host ("  user-service  落到 {0} 个不同实例" -f $userCount) -ForegroundColor $(if ($userCount -ge 2) { 'Green' } else { 'Red' })
Write-Host ("  food-service  落到 {0} 个不同实例" -f $foodCount) -ForegroundColor $(if ($foodCount -ge 2) { 'Green' } else { 'Red' })
Write-Host ""
if ($pass) {
    Write-Host "  [PASS] 请求确实被分发到两个实例上（不是只看注册列表）" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] 只落到一个实例：可能另一台没注册上，或下单次数太少" -ForegroundColor Red
    Write-Host "         排查：打开 http://localhost:18080/next/ ，分组选 PERSONAL_GROUP，" -ForegroundColor Yellow
    Write-Host "               确认 user-service / food-service 各有 2 个实例" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  ↑ 现在按 Win+Shift+S 截这一屏（把上面标题和这行结论都框进去）" -ForegroundColor Magenta
Write-Host ""

if ($pass) { exit 0 } else { exit 1 }
