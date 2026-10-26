#!/bin/bash
# =============================================================================
# MySQL 首次初始化脚本
#
# 放在 /docker-entrypoint-initdb.d/ 下，官方镜像在【数据目录为空】时才会执行。
# 也就是说：如果 mysql-data 卷里已经有数据，本脚本不会运行。
# 因此从第 05 周（单体）切换到第 06 周（微服务）时，需要先执行一次：
#     docker compose down -v      # 删掉旧数据卷
#     docker compose up -d --build
#
# 为什么用 .sh 而不是 .sql：
#   每个服务的数据库口令必须来自 .env（不能硬编码进仓库），
#   而 .sql 文件无法读取环境变量；.sh 由 entrypoint 直接 source，可以拿到环境变量。
#
# 本脚本做三件事：
#   1. 建 3 个独立数据库（每个服务一个）
#   2. 建 3 个独立账号（每个账号只被授权访问自己的库）
#   3. FLUSH PRIVILEGES 让授权立即生效
#
# 授权隔离是本设计的核心：svc_meal 无法读 health_diet_user 或 health_diet_food，
# 想拿用户/食物数据只能通过 HTTP 调用对应服务 —— 这才叫"服务拥有自己的数据"。
# =============================================================================

set -e

echo "[db-init] 开始创建微服务用的独立数据库与独立账号..."

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
-- ---- 1) 三个独立数据库 ----
CREATE DATABASE IF NOT EXISTS health_diet_user
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS health_diet_food
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS health_diet_meal
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---- 2) 三个独立账号，各自只授权自己的库 ----
CREATE USER IF NOT EXISTS 'svc_user'@'%' IDENTIFIED BY '${USER_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON health_diet_user.* TO 'svc_user'@'%';

CREATE USER IF NOT EXISTS 'svc_food'@'%' IDENTIFIED BY '${FOOD_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON health_diet_food.* TO 'svc_food'@'%';

CREATE USER IF NOT EXISTS 'svc_meal'@'%' IDENTIFIED BY '${MEAL_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON health_diet_meal.* TO 'svc_meal'@'%';

-- ---- 3) 让授权立即生效 ----
FLUSH PRIVILEGES;
EOSQL

echo "[db-init] 完成。已创建：health_diet_user / health_diet_food / health_diet_meal"
echo "[db-init] 账号：svc_user / svc_food / svc_meal（各自只能访问自己的库）"
