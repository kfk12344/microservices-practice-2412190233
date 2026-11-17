-- =============================================================================
-- 第 09 周迁移：给第 08 周遗留的 app_user 表补上认证字段
--
-- 适用场景：数据库卷是第 08 周创建的（app_user 表已存在，但没有 password_hash / role）。
-- 全新环境不需要执行本文件 —— schema.sql 会直接建出完整结构。
--
-- 执行方式（仓库根目录，Git Bash）：
--   docker compose exec -T mysql sh -c \
--     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot health_diet_user' \
--     < db/migrations/09-auth.sql
--
-- 注意：ADD COLUMN / ADD CONSTRAINT 不支持 IF NOT EXISTS，本文件只能对旧库执行一次。
-- =============================================================================

ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER daily_target_kcal,
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER' AFTER password_hash;

-- 种子账号补上可登录的口令哈希（与 data.sql 一致）
UPDATE app_user SET password_hash = '$2a$10$mDTfXICj7NQd.iGTMjE0rOxmzyDixp/bvRlPvHlKoUfD.DpTT8.qC', role = 'USER'
WHERE username = 'demo-user';

UPDATE app_user SET password_hash = '$2a$10$5jW8olHnwChqtyUpaXz2BOBuh4vGb3qAi1NbI1Vmsoevw/uaRw4Zm', role = 'NUTRITIONIST'
WHERE username = 'alice';

UPDATE app_user SET password_hash = '$2a$10$WZFzsayyQd5MvtfONIOE0OfByE6R0FX0KudAySA5OLW7orPt654x2', role = 'ADMIN'
WHERE username = 'bob';

-- 其它历史用户：写入一个无人知晓的随机口令哈希，避免留空
UPDATE app_user SET password_hash = '$2a$10$bKIIRn6mhk9toAIyMQ.Oy.k9bwD6.kyEmT9iWJRTkaQdZuBIBB0sO'
WHERE password_hash IS NULL OR password_hash = '';

ALTER TABLE app_user MODIFY COLUMN password_hash VARCHAR(100) NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'NUTRITIONIST', 'ADMIN'));
