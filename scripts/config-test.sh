#!/usr/bin/env bash
# config-test.sh —— 第 10 周「Nacos Config 配置中心」自动验证脚本
#
# 必须在【仓库根目录】运行：
#     bash scripts/config-test.sh
# Windows 也可：powershell -File scripts/config-test.ps1
#
# 它做四件事：
#   1. 把 dev/test 两套非敏感配置发布到 Nacos（Data ID：<服务名>-<env>.yaml）
#   2. 验证 4 个服务都能从 Nacos 读到自己的配置（configSource=nacos）
#   3. 验证多环境隔离（APP_ENV 决定 Data ID）
#   4. 修改 meal-service 的单餐热量上限，证明 @RefreshScope 动态刷新生效，然后恢复
#
# 依赖：docker compose 已启动、Nacos 管理员密码已填进 .env。
# 可用环境变量覆盖：NACOS_API / NACOS_CONSOLE / NACOS_USERNAME / NACOS_PASSWORD /
#                   NACOS_GROUP / APP_ENV / SKIP_PUBLISH=1 / SKIP_REFRESH=1
set -uo pipefail

# ---- 从 .env 读取口令（文件已被 .gitignore 排除，不会进仓库）----
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

NACOS_API="${NACOS_API:-http://localhost:8848}"
NACOS_CONSOLE="${NACOS_CONSOLE:-http://localhost:18080}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
GROUP="${NACOS_GROUP:-PERSONAL_GROUP}"
# APP_ENV_OVERRIDE 优先于 .env，方便临时切到 test 环境验证
APP_ENV="${APP_ENV_OVERRIDE:-${APP_ENV:-dev}}"
SKIP_PUBLISH="${SKIP_PUBLISH:-0}"
SKIP_REFRESH="${SKIP_REFRESH:-0}"

PASS=0
FAIL=0
SKIPPED=0
TOKEN=""

c_ok()   { printf '  \033[32m[PASS]\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
c_bad()  { printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
c_skip() { printf '  \033[33m[SKIP]\033[0m %s\n' "$1"; SKIPPED=$((SKIPPED + 1)); }
c_info() { printf '  \033[36m[INFO]\033[0m %s\n' "$1"; }
hdr()    { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

# 读取 JSON 里的字符串 / 数字字段，不依赖 jq
json_str() { printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"; }
json_num() { printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\(-*[0-9][0-9]*\).*/\1/p"; }

# ---------------------------------------------------------------------------
# Nacos Open API 辅助函数
# ---------------------------------------------------------------------------
nacos_login() {
  local resp token path
  for path in "/nacos/v1/auth/users/login" "/nacos/v1/auth/login"; do
    # Nacos 3.x 用 /v1/auth/users/login，2.x 兼容 /v1/auth/login
    resp=$(curl -sS -m 5 -X POST \
      --data-urlencode "username=${NACOS_USERNAME}" \
      --data-urlencode "password=${NACOS_PASSWORD}" \
      "${NACOS_API}${path}" 2>/dev/null || true)
    token=$(json_str "$resp" accessToken)
    if [ -n "$token" ]; then
      printf '%s' "$token"
      return 0
    fi
  done
  return 1
}

nacos_publish() {
  local data_id="$1" content="$2" code body
  # Nacos 3.x 的 OpenAPI 是 /nacos/v3/...，分组参数叫 groupName（不是 group）
  body=$(curl -sS -m 10 -X POST \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "groupName=${GROUP}" \
    --data-urlencode "content=${content}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "accessToken=${TOKEN}" \
    "${NACOS_API}/nacos/v3/admin/cs/config" 2>/dev/null || true)
  # 成功时 HTTP 200 且响应含 "code":0
  if printf '%s' "$body" | grep -q '"code":0'; then
    printf '200'
  else
    printf '%s' "$(printf '%s' "$body" | head -c 120)"
  fi
}

nacos_read() {
  # 返回 {... "data":{"content":"..."}}，后面用 json_content 提取
  curl -sS -m 5 -G \
    --data-urlencode "dataId=$1" \
    --data-urlencode "groupName=${GROUP}" \
    --data-urlencode "accessToken=${TOKEN}" \
    "${NACOS_API}/nacos/v3/client/cs/config" 2>/dev/null || true
}

# 从 v3 响应嵌套 JSON 中取出 content（把 \n 还原成真实换行）
json_content() {
  local body="$1" raw
  raw=$(printf '%s' "$body" | sed -n 's/.*"content"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  printf '%b' "$raw"
}

# 生成某个服务在某个环境下的配置内容（全部为非敏感参数）
config_content() {
  local service="$1" env="$2" kcal="$3"
  case "$service" in
    meal-service)
      cat <<EOF
# Nacos Config · ${service} · ${env}
# 只放非敏感业务参数；口令与密钥仍由 .env 注入
app:
  environment: ${env}
  config:
    source: nacos
  meal:
    max-kcal-per-meal: ${kcal}
    create-enabled: true
logging:
  level:
    com.zjgsu.hdt.meal: INFO
EOF
      ;;
    user-service)
      cat <<EOF
# Nacos Config · ${service} · ${env}
app:
  environment: ${env}
  config:
    source: nacos
  jwt:
    ttl-seconds: 3600
logging:
  level:
    com.zjgsu.hdt.user: INFO
EOF
      ;;
    food-service)
      cat <<EOF
# Nacos Config · ${service} · ${env}
app:
  environment: ${env}
  config:
    source: nacos
logging:
  level:
    com.zjgsu.hdt.food: INFO
EOF
      ;;
    gateway-service)
      cat <<EOF
# Nacos Config · ${service} · ${env}
app:
  environment: ${env}
  config:
    source: nacos
logging:
  level:
    com.zjgsu.hdt.gateway: INFO
EOF
      ;;
  esac
}

probe() {
  local base="$1"
  curl -sS -m 4 "${base}/api/config-probe" 2>/dev/null || true
}

wait_for_value() {
  # wait_for_value <url> <field> <expected> <seconds>
  local url="$1" field="$2" expected="$3" limit="$4" i=0 body value
  while [ "$i" -lt "$limit" ]; do
    body=$(curl -sS -m 4 "$url" 2>/dev/null || true)
    value=$(json_num "$body" "$field")
    [ "$value" = "$expected" ] && return 0
    i=$((i + 1))
    sleep 1
  done
  return 1
}

# ===========================================================================
hdr "1. Nacos 连通性与鉴权"
# ===========================================================================
code=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' "${NACOS_API}/nacos/v3/client/cs/config" 2>/dev/null || printf '000')
if [ "$code" = "000" ]; then
  c_bad "Nacos API 不可达：${NACOS_API}（先执行 docker compose up -d）"
  printf '\n结果：PASS=%d FAIL=%d SKIP=%d\n' "$PASS" "$FAIL" "$SKIPPED"
  exit 1
else
  c_ok "Nacos API 可达（HTTP ${code}）"
fi
c_info "控制台地址：${NACOS_CONSOLE}"

TOKEN=$(nacos_login || true)
if [ -n "$TOKEN" ]; then
  c_ok "Nacos 鉴权成功，已取得 accessToken"
else
  c_skip "Nacos 登录失败：检查 .env 的 NACOS_USERNAME / NACOS_PASSWORD；本次跳过配置发布与回读"
fi

# ===========================================================================
hdr "2. 发布 dev / test 两套配置（8 个 Data ID）"
# ===========================================================================
SERVICES="user-service food-service meal-service gateway-service"
if [ -z "$TOKEN" ]; then
  c_skip "未取得 accessToken，跳过配置发布（可先在 Nacos 控制台手动创建配置）"
elif [ "$SKIP_PUBLISH" = "1" ]; then
  c_skip "SKIP_PUBLISH=1，跳过发布（沿用 Nacos 中已有配置）"
else
  for env_name in dev test; do
    for service in $SERVICES; do
      kcal=1500
      [ "$env_name" = "test" ] && kcal=1200
      data_id="${service}-${env_name}.yaml"
      content=$(config_content "$service" "$env_name" "$kcal")
      code=$(nacos_publish "$data_id" "$content")
      if [ "$code" = "200" ]; then
        c_ok "发布配置 ${data_id} (group=${GROUP})"
      else
        c_bad "发布配置 ${data_id} 失败（HTTP ${code}）"
      fi
    done
  done
fi

if [ -z "$TOKEN" ]; then
  c_skip "未取得 accessToken，跳过配置回读"
else
  body=$(json_content "$(nacos_read "meal-service-${APP_ENV}.yaml")")
  if printf '%s' "$body" | grep -q 'source: nacos'; then
    c_ok "Nacos 可回读 meal-service-${APP_ENV}.yaml，且含非敏感配置"
  else
    c_bad "Nacos 回读失败或内容不符：$(printf '%s' "$body" | head -c 120)"
  fi
fi

# ===========================================================================
hdr "3. 多服务配置接入（当前 APP_ENV=${APP_ENV}）"
# ===========================================================================
declare -a BASES=("http://localhost:8081" "http://localhost:8082" "http://localhost:8083" "http://localhost:8080")
declare -a NAMES=("user-service" "food-service" "meal-service" "gateway-service")
for idx in 0 1 2 3; do
  base="${BASES[$idx]}"
  name="${NAMES[$idx]}"
  body=$(probe "$base")
  if [ -z "$body" ]; then
    c_bad "${name} 的 /api/config-probe 不可达（${base}，先启动容器）"
    continue
  fi
  source_value=$(json_str "$body" configSource)
  env_value=$(json_str "$body" environment)
  data_id=$(json_str "$body" dataId)
  expected_id="${name}-${APP_ENV}.yaml"
  if [ "$source_value" = "nacos" ] && [ "$env_value" = "$APP_ENV" ] && [ "$data_id" = "$expected_id" ]; then
    c_ok "${name} 读取 ${data_id}，source=${source_value}，env=${env_value}"
  else
    c_bad "${name} 配置证据不符：source=${source_value} env=${env_value} dataId=${data_id}（期望 ${expected_id}）"
  fi
done

# ===========================================================================
hdr "4. 动态配置验证（meal-service @RefreshScope）"
# ===========================================================================
MEAL_PROBE="http://localhost:8083/api/config-probe"
if [ -z "$TOKEN" ]; then
  c_skip "未取得 accessToken，跳过动态刷新验证（需脚本能修改 Nacos 配置）"
elif [ "$SKIP_REFRESH" = "1" ]; then
  c_skip "SKIP_REFRESH=1，跳过动态刷新验证"
else
  before=$(json_num "$(curl -sS -m 4 "$MEAL_PROBE" 2>/dev/null || true)" maxKcalPerMeal)
  if [ -z "$before" ]; then
    c_bad "无法读取 meal-service 当前阈值（服务未启动？）"
  else
    c_info "刷新前 maxKcalPerMeal=${before}"
    changed=1200
    [ "$before" = "1200" ] && changed=1500

    content=$(config_content "meal-service" "$APP_ENV" "$changed")
    code=$(nacos_publish "meal-service-${APP_ENV}.yaml" "$content")
    if [ "$code" != "200" ]; then
      c_bad "修改 Nacos 配置失败（HTTP ${code}）"
    elif wait_for_value "$MEAL_PROBE" maxKcalPerMeal "$changed" 20; then
      c_ok "未重启/未重新打包，阈值自动变为 ${changed}（动态刷新生效）"
    else
      c_bad "配置已修改，但 20 秒内服务阈值未变为 ${changed}"
    fi

    # 无论上面成败都恢复原值，避免留下脏配置
    restore=$(config_content "meal-service" "$APP_ENV" "$before")
    code=$(nacos_publish "meal-service-${APP_ENV}.yaml" "$restore")
    if [ "$code" = "200" ] && wait_for_value "$MEAL_PROBE" maxKcalPerMeal "$before" 20; then
      c_ok "已恢复原阈值 ${before}"
    else
      c_bad "恢复原配置失败，请手动把 max-kcal-per-meal 改回 ${before}"
    fi
  fi
fi

# ===========================================================================
printf '\n============================================================\n'
printf '验证结束：PASS=%d FAIL=%d SKIP=%d\n' "$PASS" "$FAIL" "$SKIPPED"
printf 'Nacos 控制台：%s\n' "$NACOS_CONSOLE"
printf 'Data ID 清单：docs/configuration.md\n'
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
