-- =============================================================================
-- user-service 示例数据
--
-- 幂等性：用 INSERT IGNORE + 显式主键，重复执行不会插入重复行，也不会报主键冲突。
--
-- 第 09 周：password_hash 是 BCrypt 哈希（成本因子 10），不是明文。
-- 对应明文口令见 README 的"测试账号与角色"表；这里只放哈希，
-- 拿到仓库的人无法从脚本里直接读出可用口令。
--
-- 三个账号覆盖三种角色，正好用来验证"登录 → 验签 → 角色授权"整条链路：
--   demo-user  USER          普通用户，访问管理接口应 403
--   alice      NUTRITIONIST  营养师
--   bob        ADMIN         管理员，访问管理接口应 200
-- =============================================================================

INSERT IGNORE INTO app_user (id, username, display_name, daily_target_kcal, password_hash, role, created_at) VALUES
    (1, 'demo-user', '演示用户', 2000,
     '$2a$10$mDTfXICj7NQd.iGTMjE0rOxmzyDixp/bvRlPvHlKoUfD.DpTT8.qC', 'USER',         '2026-09-28 07:00:00'),
    (2, 'alice',     '爱丽丝',   1800,
     '$2a$10$5jW8olHnwChqtyUpaXz2BOBuh4vGb3qAi1NbI1Vmsoevw/uaRw4Zm', 'NUTRITIONIST', '2026-09-28 07:10:00'),
    (3, 'bob',       '鲍勃',     2200,
     '$2a$10$WZFzsayyQd5MvtfONIOE0OfByE6R0FX0KudAySA5OLW7orPt654x2', 'ADMIN',        '2026-09-28 07:20:00');
