#!/usr/bin/env bash
# =============================================================================
# API 网关 + JWT 认证 自动验证脚本（第 09 周）
#
# 用法（在仓库根目录执行，先 docker compose up -d --build）：
#     bash scripts/auth-test.sh
#     BASE_URL=http://localhost:8080 bash scripts/auth-test.sh
#
# 覆盖作业要求的四个场景：
#   1. 未登录访问受保护接口 → 401
#   2. 登录获取 Token → 200
#   3. 携带 Token 访问受保护接口 → 200
#   4. 低权限 Token 访问管理接口 → 403
# 额外覆盖：
#   5. 管理员 Token 访问管理接口 → 200（证明角色校验不是一律拒绝）
#   6. 客户端伪造 X-User-Role: ADMIN（无 Token / 低权限 Token）→ 401 / 403
#   7. Token 被篡改 → 401
#   8. 网关健康入口 /api/health → 200
#
# 退出码：0 全部通过；1 有失败项；2 前置条件不满足
# =============================================================================

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
DEMO_USER="${DEMO_USER:-demo-user}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@1234}"
ADMIN_USER="${ADMIN_USER:-bob}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@1234}"

PASS=0
FAIL=0

c_ok()   { printf '  \033[32m[PASS]\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
c_bad()  { printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
c_info() { printf '  \033[36m[INFO]\033[0m %s\n' "$1"; }
hdr()    { printf '\n\033[1m%s\033[0m\n' "$1"; }

# ------------------------------------------------------------------ 工具函数

# 取 HTTP 状态码：http_code METHOD PATH [TOKEN] [EXTRA_HEADER]
http_code() {
    local method="$1" path="$2" token="${3:-}" extra="${4:-}"
    local args=(-sS -o /dev/null -w '%{http_code}' -X "$method")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer ${token}")
    [ -n "$extra" ] && args+=(-H "$extra")
    curl "${args[@]}" "${BASE_URL}${path}" 2>/dev/null
}

# 登录并取原始响应：login BODY_USER BODY_PASS
login() {
    curl -sS -X POST -H 'Content-Type: application/json' \
        -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
        "${BASE_URL}/api/auth/login" 2>/dev/null
}

# 从登录响应里抽 token（不依赖 jq）
extract_token() {
    printf '%s' "$1" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

# 断言状态码：expect_status 期望值 实际值 描述
expect_status() {
    if [ "$1" = "$2" ]; then
        c_ok "$3（HTTP $2）"
    else
        c_bad "$3：期望 HTTP $1，实际 HTTP ${2:-无响应}"
    fi
}

wait_ready() {
    local i=0
    while [ "$i" -lt 60 ]; do
        if [ "$(http_code GET '/api/health')" = "200" ]; then
            return 0
        fi
        i=$((i + 1))
        sleep 2
    done
    return 1
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

c_info "统一入口：${BASE_URL}（应是 gateway-service）"
if ! wait_ready; then
    printf '\033[31m%s 在 120 秒内没有就绪，请先 docker compose up -d --build\033[0m\n' "$BASE_URL"
    exit 2
fi
c_ok "网关 /api/health 可访问"

# =============================================================================
# 1. 未登录访问受保护接口 → 401
# =============================================================================
hdr "1. 未登录访问受保护接口（应 401）"

expect_status 401 "$(http_code GET '/api/users/1')" "无 Token 访问 /api/users/1"

# 关键：伪造身份头不等于登录，网关会先清洗再判断
expect_status 401 "$(http_code GET '/api/users/1' '' 'X-User-Role: ADMIN')" \
    "仅伪造 X-User-Role: ADMIN 访问（无 Token）"

# =============================================================================
# 2. 登录获取 Token → 200
# =============================================================================
hdr "2. 登录获取 JWT"

LOGIN_BODY="$(login "$DEMO_USER" "$DEMO_PASSWORD")"
USER_TOKEN="$(extract_token "$LOGIN_BODY")"

if [ -n "$USER_TOKEN" ]; then
    c_ok "普通用户 ${DEMO_USER} 登录成功，拿到 Token（$(printf '%s' "$USER_TOKEN" | awk -F. '{print NF}') 段）"
else
    c_bad "登录失败，响应：${LOGIN_BODY}"
fi

WRONG_BODY="$(login "$DEMO_USER" 'wrong-password')"
WRONG_CODE="$(printf '%s' "$WRONG_BODY" | sed -n 's/.*"status":\([0-9]*\).*/\1/p')"
[ "$WRONG_CODE" = "401" ] && c_ok "错误口令被拒绝（401，且响应里没有 Token）" \
    || c_bad "错误口令未被正确拒绝：${WRONG_BODY}"

# =============================================================================
# 3. 携带 Token 访问受保护接口 → 200
# =============================================================================
hdr "3. 携带 Token 访问受保护接口（应 200）"

expect_status 200 "$(http_code GET '/api/users/1' "$USER_TOKEN")" "Bearer Token 访问 /api/users/1"

# =============================================================================
# 4. 低权限 Token 访问管理接口 → 403
# =============================================================================
hdr "4. 角色校验"

expect_status 403 "$(http_code GET '/api/admin/users' "$USER_TOKEN")" \
    "USER 角色访问 /api/admin/users（越权）"

# 伪造角色头也无效：网关用 Token 里的 role 覆盖客户端带来的同名头
expect_status 403 "$(http_code GET '/api/admin/users' "$USER_TOKEN" 'X-User-Role: ADMIN')" \
    "USER Token + 伪造 X-User-Role: ADMIN"

ADMIN_BODY="$(login "$ADMIN_USER" "$ADMIN_PASSWORD")"
ADMIN_TOKEN="$(extract_token "$ADMIN_BODY")"
expect_status 200 "$(http_code GET '/api/admin/users' "$ADMIN_TOKEN")" \
    "ADMIN 角色访问 /api/admin/users"

# =============================================================================
# 5. 篡改 Token → 401
# =============================================================================
hdr "5. 篡改 Token（应 401）"

TAMPERED="${USER_TOKEN}x"
expect_status 401 "$(http_code GET '/api/users/1' "$TAMPERED")" "签名被破坏的 Token"

# 把 payload 里的角色改成 ADMIN 而不重新签名 —— 典型越权尝试
FORGED_PAYLOAD="$(printf '%s' "$USER_TOKEN" | awk -F. '{print $1"."$2".x"}')"
expect_status 401 "$(http_code GET '/api/admin/users' "$FORGED_PAYLOAD")" \
    "手工篡改 payload 的 Token"

# =============================================================================
# 汇总
# =============================================================================
hdr "汇总"
printf '  通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"

if [ "$FAIL" -eq 0 ]; then
    printf '\033[32m全部通过：未授权被拦截、登录可用、角色越权被阻止、伪造头无效\033[0m\n'
    exit 0
fi
printf '\033[31m存在失败项，请对照上面的输出排查\033[0m\n'
exit 1
