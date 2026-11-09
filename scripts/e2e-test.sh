#!/usr/bin/env bash
# =============================================================================
# 跨服务流程 + 负载均衡 端到端验证（第 08 周）
#
# 用法（在仓库根目录执行）：
#     bash scripts/e2e-test.sh
#     bash scripts/e2e-test.sh --build          # 先重建镜像再验证（改了代码后用）
#     BASE_URL=http://meal-service:8083 SKIP_DOCKER=1 bash scripts/e2e-test.sh
#                                               # 在容器内只跑 HTTP 部分
#
# 设计说明：
#   1. 全程只用 curl 调 HTTP 接口，不直接查数据库 —— 验证的是"对外可见的行为"。
#   2. 负载均衡的证据来自 GET /api/meals/instances：
#      它返回 meal-service 实际调用过的 user/food 实例标识与次数。
#      只展示注册列表不算完成分发验证，所以这里统计的是**真实业务调用**。
#   3. 故障注入用 docker compose stop/start。没有 docker 的环境（如容器内）
#      可以用 SKIP_DOCKER=1 跳过，脚本会在汇总里标注"跳过"而不是假装通过。
#
# 退出码：0 全部通过；1 有失败项；2 前置条件不满足（例如服务没起来）
# =============================================================================

set -u

# ---------------------------------------------------------------- 可调参数
BASE_URL="${BASE_URL:-http://localhost:8083}"
USER_ID="${USER_ID:-1}"
FOOD_ID_A="${FOOD_ID_A:-1}"        # 米饭
FOOD_ID_B="${FOOD_ID_B:-3}"        # 鸡胸肉
ROUNDS="${ROUNDS:-8}"              # 正常路径下单次数（次数要够多，才可能落到两台实例）
SKIP_DOCKER="${SKIP_DOCKER:-0}"

PASS=0
FAIL=0
SKIPPED=0

# ---------------------------------------------------------------- 输出helpers
c_ok()   { printf '  \033[32m[PASS]\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
c_bad()  { printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
c_skip() { printf '  \033[33m[SKIP]\033[0m %s\n' "$1"; SKIPPED=$((SKIPPED + 1)); }
c_info() { printf '  \033[36m[INFO]\033[0m %s\n' "$1"; }
hdr()    { printf '\n\033[1m%s\033[0m\n' "$1"; }

# ---------------------------------------------------------------- 工具函数

# 取 HTTP 状态码：status_of METHOD PATH [BODY]
status_of() {
    local method="$1" path="$2" body="${3:-}"
    if [ -n "$body" ]; then
        curl -sS -o /dev/null -w '%{http_code}' -X "$method" \
            -H 'Content-Type: application/json' -d "$body" "${BASE_URL}${path}" 2>/dev/null
    else
        curl -sS -o /dev/null -w '%{http_code}' -X "$method" "${BASE_URL}${path}" 2>/dev/null
    fi
}

# 取响应体
body_of() {
    local method="$1" path="$2" body="${3:-}"
    if [ -n "$body" ]; then
        curl -sS -X "$method" -H 'Content-Type: application/json' -d "$body" "${BASE_URL}${path}" 2>/dev/null
    else
        curl -sS -X "$method" "${BASE_URL}${path}" 2>/dev/null
    fi
}

# 组装一份合法的下单请求（两条明细，跨两次 food 调用 + 一次 user 调用）
meal_payload() {
    local user_id="$1"
    printf '{"userId":%s,"mealType":"LUNCH","eatenAt":"2026-11-20T12:30:00","note":"e2e-test","items":[{"foodId":%s,"portionGrams":100},{"foodId":%s,"portionGrams":100}]}' \
        "$user_id" "$FOOD_ID_A" "$FOOD_ID_B"
}

# 统计当前用户有多少条记录（响应是 JSON 数组，数 "id": 出现次数即可，不依赖 jq）
record_count() {
    body_of GET "/api/meals?userId=${USER_ID}" | grep -o '"id":' | wc -l | tr -d ' '
}

# 某个服务在统计里出现了几个不同实例（去重计数）
distinct_instances() {
    local service="$1"
    # 响应形如 {"user-service":{"user-service-1":4,"user-service-2":4}}
    body_of GET '/api/meals/instances' \
        | tr ',' '\n' \
        | grep -o "\"${service}-[0-9]*\"" \
        | sort -u \
        | wc -l | tr -d ' '
}

wait_ready() {
    local i=0
    while [ "$i" -lt 60 ]; do
        if [ "$(status_of GET '/actuator/health')" = "200" ]; then
            return 0
        fi
        i=$((i + 1))
        sleep 2
    done
    return 1
}

docker_available() {
    [ "$SKIP_DOCKER" = "1" ] && return 1
    command -v docker >/dev/null 2>&1 || return 1
    docker info >/dev/null 2>&1
}

# =============================================================================
# 0. 前置检查
# =============================================================================
hdr "0. 前置检查"

if ! command -v curl >/dev/null 2>&1; then
    printf '\033[31m缺少 curl，无法继续\033[0m\n'
    exit 2
fi
c_ok "curl 可用"

c_info "目标服务：${BASE_URL}"
if ! wait_ready; then
    printf '\033[31m%s 在 120 秒内没有就绪，请先 docker compose up -d\033[0m\n' "$BASE_URL"
    exit 2
fi
c_ok "meal-service 健康检查通过"

if docker_available; then
    c_ok "docker 可用，将做故障注入"
else
    c_skip "docker 不可用或已 SKIP_DOCKER=1，跳过故障注入"
fi

# =============================================================================
# 1. 跨服务正常路径
# =============================================================================
hdr "1. 跨服务正常路径（POST /api/meals）"

# 清空实例命中统计，保证后面的分发数据是这一轮产生的
if [ "$(status_of DELETE '/api/meals/instances')" = "204" ]; then
    c_ok "已清空实例命中统计"
else
    c_bad "清空实例命中统计失败"
fi

before="$(record_count)"
c_info "当前记录数：${before}"

ok_created=0
for i in $(seq 1 "$ROUNDS"); do
    code="$(status_of POST '/api/meals' "$(meal_payload "$USER_ID")")"
    if [ "$code" = "201" ]; then
        ok_created=$((ok_created + 1))
    else
        c_bad "第 ${i} 次下单期望 201，实际 ${code}"
        break
    fi
done
if [ "$ok_created" -eq "$ROUNDS" ]; then
    c_ok "连续 ${ROUNDS} 次下单全部 201（每次都会跨服务校验用户与两种食物）"
fi

after="$(record_count)"
if [ "$after" -eq $((before + ROUNDS)) ]; then
    c_ok "记录数从 ${before} 增加到 ${after}，与下单次数一致"
else
    c_bad "记录数应为 $((before + ROUNDS))，实际 ${after}"
fi

# =============================================================================
# 2. 负载均衡：证明请求真的分发到了多个实例
# =============================================================================
hdr "2. 负载均衡验证（GET /api/meals/instances）"

hits="$(body_of GET '/api/meals/instances')"
c_info "命中统计：${hits}"

user_inst="$(distinct_instances 'user-service')"
food_inst="$(distinct_instances 'food-service')"

if [ "$user_inst" -ge 2 ]; then
    c_ok "user-service 的调用落到了 ${user_inst} 个不同实例"
else
    c_bad "user-service 只落到 ${user_inst} 个实例（负载均衡没生效或另一台不健康）"
fi

if [ "$food_inst" -ge 2 ]; then
    c_ok "food-service 的调用落到了 ${food_inst} 个不同实例"
else
    c_bad "food-service 只落到 ${food_inst} 个实例（负载均衡没生效或另一台不健康）"
fi

# =============================================================================
# 3. 故障注入（需要 docker）
# =============================================================================
hdr "3. 故障注入（依赖服务不可用）"

if ! docker_available; then
    c_skip "跳过：需要 docker 才能停/启容器"
else
    # ---- 3.1 停掉一个实例：服务仍可用（负载均衡自动避开）----
    if docker stop hdt-user-service-2 >/dev/null 2>&1; then
        c_info "已停止 hdt-user-service-2，等待 Nacos 摘除实例（约 45 秒）"
        sleep 45
        code="$(status_of POST '/api/meals' "$(meal_payload "$USER_ID")")"
        if [ "$code" = "201" ]; then
            c_ok "少一台实例时仍返回 201（负载均衡把请求打到了剩下的实例）"
        else
            c_bad "少一台实例时应仍可用，实际 ${code}"
        fi
        docker start hdt-user-service-2 >/dev/null 2>&1
        c_info "已重新启动 hdt-user-service-2，等待重新注册"
        sleep 25
    else
        c_skip "找不到 hdt-user-service-2 容器，跳过"
    fi

    # ---- 3.2 停掉全部 user 实例：必须 503，且不能写脏数据 ----
    if docker stop hdt-user-service hdt-user-service-2 >/dev/null 2>&1; then
        c_info "已停止全部 user-service 实例，等待 Nacos 摘除（约 45 秒）"
        sleep 45
        before_fail="$(record_count)"
        code="$(status_of POST '/api/meals' "$(meal_payload "$USER_ID")")"
        if [ "$code" = "503" ]; then
            c_ok "依赖不可用时返回 503"
        else
            c_bad "期望 503，实际 ${code}"
        fi
        if body_of POST '/api/meals' "$(meal_payload "$USER_ID")" | grep -q 'DEPENDENCY_UNAVAILABLE'; then
            c_ok "错误码为 DEPENDENCY_UNAVAILABLE"
        else
            c_bad "响应里没有 DEPENDENCY_UNAVAILABLE"
        fi
        after_fail="$(record_count)"
        if [ "$after_fail" -eq "$before_fail" ]; then
            c_ok "记录数保持 ${before_fail} 不变（校验失败不写库）"
        else
            c_bad "记录数从 ${before_fail} 变成 ${after_fail}，说明写了脏数据"
        fi

        docker start hdt-user-service hdt-user-service-2 >/dev/null 2>&1
        c_info "已恢复 user-service 实例，等待重新注册"
        sleep 30
        code="$(status_of POST '/api/meals' "$(meal_payload "$USER_ID")")"
        if [ "$code" = "201" ]; then
            c_ok "恢复后再次下单返回 201"
        else
            c_bad "恢复后期望 201，实际 ${code}"
        fi
    else
        c_skip "停止 user-service 失败，跳过该场景"
    fi
fi

# =============================================================================
# 汇总
# =============================================================================
hdr "汇总"
printf '  通过 %d　失败 %d　跳过 %d\n' "$PASS" "$FAIL" "$SKIPPED"

if [ "$FAIL" -gt 0 ]; then
    printf '\033[31m端到端验证未通过\033[0m\n'
    exit 1
fi
printf '\033[32m端到端验证通过\033[0m\n'
exit 0
