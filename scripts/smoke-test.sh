#!/usr/bin/env bash
# =============================================================================
# smoke-test.sh —— 微服务冒烟测试（第 06 周起，第 07 周加入服务发现）
#
# 必须能从【仓库根目录】直接运行：
#     bash scripts/smoke-test.sh
#
# 覆盖内容：
#   0. Nacos 控制台可访问（第 07 周新增，用 3.x 的控制台端口，不是 8848/nacos）
#   1. 三个服务各自的健康接口
#   2. 一次完整的跨服务业务流程（meal-service 通过服务发现调 user + food）
#   3. 依赖资源不存在（用户/食物不存在）→ 400，且不写本地库
#   4. 违反业务规则（单餐热量超限）→ 422，且不写本地库
#   5. 依赖服务不可用（停掉 user-service）→ 503，且不写本地库，随后恢复
#      —— 停掉后 Nacos 会摘除该实例，meal-service 再查就是空实例列表，
#         这正是"服务发现 + 空实例处理"要验证的场景
#
# 退出码：全部通过 0，有失败 1（任何一项失败都会返回非零，不会忽略错误报成功）
# =============================================================================

set -uo pipefail

# 服务地址可用环境变量覆盖：
#   宿主机直接跑 —— 默认 localhost 即可；
#   在容器内跑（--network hdt-net）—— 覆盖成服务名，例如
#     USER_BASE=http://user-service:8081 FOOD_BASE=http://food-service:8082 \
#     MEAL_BASE=http://meal-service:8083 bash scripts/smoke-test.sh
USER_BASE="${USER_BASE:-http://localhost:8081}"
FOOD_BASE="${FOOD_BASE:-http://localhost:8082}"
MEAL_BASE="${MEAL_BASE:-http://localhost:8083}"
# Nacos 控制台地址：3.x 起控制台与主端口分离，控制台在 8080，
# 本项目在 compose 里映射到宿主机 18080（不是旧版的 http://localhost:8848/nacos）
NACOS_CONSOLE="${NACOS_CONSOLE:-http://localhost:18080}"

PASS=0
FAIL=0

# ---- 输出辅助 ---------------------------------------------------------------
section() {
    echo ""
    echo "=============================================================="
    echo "  $1"
    echo "=============================================================="
}

ok() {
    PASS=$((PASS + 1))
    echo "  [PASS] $1"
}

bad() {
    FAIL=$((FAIL + 1))
    echo "  [FAIL] $1"
    if [ $# -ge 2 ]; then
        echo "         $2"
    fi
}

# 发请求，把响应体放在全局 BODY，状态码放在全局 CODE
BODY=""
CODE=""
request() {
    local method="$1" url="$2" data="${3:-}"
    local tmp
    tmp="$(mktemp)"
    if [ -n "$data" ]; then
        CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" \
            -H 'Content-Type: application/json' -d "$data" 2>/dev/null)"
    else
        CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" 2>/dev/null)"
    fi
    # curl 连不上时可能什么都没输出（或只输出 000），统一补成 000，
    # 注意不要写成 `curl ... || echo '000'` —— 那样会把两段输出拼成 000000
    if [ -z "$CODE" ]; then
        CODE="000"
    fi
    BODY="$(cat "$tmp")"
    rm -f "$tmp"
}

# 断言状态码
expect_code() {
    local label="$1" expected="$2"
    if [ "$CODE" = "$expected" ]; then
        ok "$label（HTTP $CODE）"
    else
        bad "$label" "期望 HTTP $expected，实际 HTTP $CODE；响应：$(echo "$BODY" | head -c 300)"
    fi
}

# 断言响应体包含某段文字
expect_body_contains() {
    local label="$1" needle="$2"
    if echo "$BODY" | grep -q "$needle"; then
        ok "$label"
    else
        bad "$label" "响应里没有找到 \"$needle\"；响应：$(echo "$BODY" | head -c 300)"
    fi
}

# 取 meal-service 里某个用户的记录条数（用于证明"没有写本地库"）
meal_count() {
    local user_id="$1"
    request GET "$MEAL_BASE/api/meals?userId=$user_id"
    # 用 grep -o 数一下 "id": 出现次数，避免依赖 jq
    echo "$BODY" | grep -o '"id":' | wc -l | tr -d ' '
}

# =============================================================================
section "开始前检查 1/2：Nacos 控制台可访问"
# =============================================================================
# 注意用的是 Nacos 3.x 的【控制台端口】（默认 8080，本项目映射到宿主机 18080），
# 不是旧版 2.x 的 http://localhost:8848/nacos 控制台地址 —— 3.0 起控制台与主端口已分离。
#
# 另外 3.x 控制台的页面入口是 /next/（访问 / 会 302 跳到这里），
# 直接请求 /index.html 会返回 500。这里跟随跳转并接受 200。
NACOS_HTTP="$(
    curl -sSL -o /dev/null -w '%{http_code}' --max-time 15 "$NACOS_CONSOLE/" 2>/dev/null
)"
if [ "$NACOS_HTTP" = "200" ]; then
    ok "Nacos 控制台可访问（$NACOS_CONSOLE/ → /next/）"
else
    bad "Nacos 控制台不可访问" "HTTP $NACOS_HTTP（$NACOS_CONSOLE/）；请确认 nacos 容器已启动、18080 端口已映射"
fi

# =============================================================================
section "开始前检查 2/2：三个服务的健康接口"
# =============================================================================
# 注意分隔符用 "|" 而不是 ":" —— 地址本身含冒号（http://host:port），
# 用冒号做分隔会被 ${var##*:} 截到端口号，导致请求发到 "8081/actuator/health"。
for entry in "user-service|$USER_BASE" "food-service|$FOOD_BASE" "meal-service|$MEAL_BASE"; do
    name="${entry%%|*}"
    base="${entry#*|}"
    request GET "$base/actuator/health"
    if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"status":"UP"'; then
        ok "$name 健康检查 UP（$base）"
    else
        bad "$name 健康检查失败" "HTTP $CODE（$base）；响应：$(echo "$BODY" | head -c 200)"
    fi
done

if [ "$FAIL" -gt 0 ]; then
    echo ""
    echo "服务没全部就绪，后续用例不再执行。请先 docker compose ps 确认 nacos 与三个服务都正常。"
    exit 1
fi

# =============================================================================
section "用例 1：跨服务业务流程 —— 记录一餐（会调用 user + food 两个服务）"
# =============================================================================
BEFORE_COUNT="$(meal_count 1)"
echo "  记录前 userId=1 的记录数：$BEFORE_COUNT"

request POST "$MEAL_BASE/api/meals" \
    '{"userId":1,"mealType":"LUNCH","eatenAt":"2026-10-25T12:30:00","note":"smoke-test 跨服务","items":[{"foodId":1,"portionGrams":200},{"foodId":3,"portionGrams":150}]}'
expect_code "POST /api/meals 创建成功" "201"
expect_body_contains "响应含合计热量 431.50（米饭 232 + 鸡胸肉 199.5）" '"totalCalories":431.50'
expect_body_contains "明细里带食物名称快照" '"foodName":"米饭"'

AFTER_COUNT="$(meal_count 1)"
if [ "$AFTER_COUNT" -gt "$BEFORE_COUNT" ]; then
    ok "本地库记录数增加（$BEFORE_COUNT → $AFTER_COUNT）"
else
    bad "本地库记录数未增加" "创建成功但记录数没变：$BEFORE_COUNT → $AFTER_COUNT"
fi

# =============================================================================
section "用例 2：依赖资源不存在 —— 用户不存在（应 400，且不写本地库）"
# =============================================================================
BEFORE_COUNT="$(meal_count 9999)"
request POST "$MEAL_BASE/api/meals" \
    '{"userId":9999,"mealType":"LUNCH","eatenAt":"2026-10-26T12:30:00","note":"不存在的用户","items":[{"foodId":1,"portionGrams":200}]}'
expect_code "userId=9999 被拒绝" "400"
expect_body_contains "错误码为 USER_NOT_FOUND" '"code":"USER_NOT_FOUND"'
AFTER_COUNT="$(meal_count 9999)"
if [ "$AFTER_COUNT" = "$BEFORE_COUNT" ]; then
    ok "未写本地库（userId=9999 的记录数为 $AFTER_COUNT）"
else
    bad "居然写了本地库" "记录数 $BEFORE_COUNT → $AFTER_COUNT"
fi

# =============================================================================
section "用例 3：依赖资源不存在 —— 食物不存在（应 400，且不写本地库）"
# =============================================================================
BEFORE_COUNT="$(meal_count 1)"
request POST "$MEAL_BASE/api/meals" \
    '{"userId":1,"mealType":"DINNER","eatenAt":"2026-10-26T19:00:00","note":"不存在的食物","items":[{"foodId":9999,"portionGrams":100}]}'
expect_code "foodId=9999 被拒绝" "400"
expect_body_contains "错误码为 FOOD_NOT_FOUND" '"code":"FOOD_NOT_FOUND"'
AFTER_COUNT="$(meal_count 1)"
if [ "$AFTER_COUNT" = "$BEFORE_COUNT" ]; then
    ok "未写本地库（记录数保持 $AFTER_COUNT）"
else
    bad "居然写了本地库" "记录数 $BEFORE_COUNT → $AFTER_COUNT"
fi

# =============================================================================
section "用例 4：业务规则 —— 单餐热量超过 1500 kcal（应 422，且不写本地库）"
# =============================================================================
BEFORE_COUNT="$(meal_count 1)"
request POST "$MEAL_BASE/api/meals" \
    '{"userId":1,"mealType":"LUNCH","eatenAt":"2026-10-27T12:30:00","note":"超量","items":[{"foodId":1,"portionGrams":2000}]}'
expect_code "2000g 米饭（2320 kcal）被拒绝" "422"
expect_body_contains "错误码为 BUSINESS_RULE_VIOLATION" '"code":"BUSINESS_RULE_VIOLATION"'
AFTER_COUNT="$(meal_count 1)"
if [ "$AFTER_COUNT" = "$BEFORE_COUNT" ]; then
    ok "未写本地库（记录数保持 $AFTER_COUNT）"
else
    bad "居然写了本地库" "记录数 $BEFORE_COUNT → $AFTER_COUNT"
fi

# =============================================================================
section "用例 5：依赖服务不可用 —— 停掉 user-service（应 503，且不写本地库）"
# =============================================================================
# 本用例需要 docker CLI 去停服务。在容器内运行脚本时（没有 docker 命令）会跳过，
# 并提示在宿主机单独执行，避免把"环境限制"误报成"测试失败"。
if ! command -v docker >/dev/null 2>&1; then
    echo "  [SKIP] 当前环境没有 docker 命令（例如在容器内运行脚本）"
    echo "         请在宿主机执行下面三步来验证本用例："
    echo "           docker compose stop user-service"
    echo "           curl -i -X POST http://localhost:8083/api/meals -H 'Content-Type: application/json' \\"
    echo "                -d '{\"userId\":1,\"mealType\":\"BREAKFAST\",\"eatenAt\":\"2026-10-28T08:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":100}]}'"
    echo "           docker compose start user-service"
elif docker compose stop user-service >/dev/null 2>&1; then
    ok "user-service 已停止"

    # 给它一点时间真的不可达
    sleep 3

    BEFORE_COUNT="$(meal_count 1)"
    request POST "$MEAL_BASE/api/meals" \
        '{"userId":1,"mealType":"BREAKFAST","eatenAt":"2026-10-28T08:00:00","note":"依赖不可用","items":[{"foodId":1,"portionGrams":100}]}'
    expect_code "user-service 不可用时被拒绝" "503"
    expect_body_contains "错误码为 DEPENDENCY_UNAVAILABLE" '"code":"DEPENDENCY_UNAVAILABLE"'
    expect_body_contains "响应里说明了 user-service" 'user-service'
    AFTER_COUNT="$(meal_count 1)"
    if [ "$AFTER_COUNT" = "$BEFORE_COUNT" ]; then
        ok "未写本地库（记录数保持 $AFTER_COUNT）—— 没有跳过校验硬写"
    else
        bad "居然写了本地库" "记录数 $BEFORE_COUNT → $AFTER_COUNT"
    fi

    echo ""
    echo "  正在恢复 user-service ..."
    if docker compose start user-service >/dev/null 2>&1; then
        ok "user-service 已重新启动"
        echo "  等待它恢复到 healthy ..."
        for _ in $(seq 1 20); do
            sleep 3
            request GET "$USER_BASE/actuator/health"
            if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"status":"UP"'; then
                ok "user-service 已恢复 UP"
                break
            fi
        done
    else
        bad "无法重新启动 user-service" "请手动执行 docker compose start user-service"
    fi
else
    bad "无法停止 user-service" "请确认在仓库根目录、且 Docker 可用（本用例需要 docker compose stop 权限）"
fi

# =============================================================================
section "结果汇总"
# =============================================================================
echo "  通过：$PASS"
echo "  失败：$FAIL"
echo ""
if [ "$FAIL" -eq 0 ]; then
    echo "  全部通过 ✅"
    exit 0
else
    echo "  存在失败 ❌"
    exit 1
fi
