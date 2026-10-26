-- =============================================================================
-- user-service 建表脚本
--
-- 只建本服务拥有的 app_user 一张表。食物的表在 food-service，
-- 饮食记录的表在 meal-service —— 本服务不建、不读、不写它们。
--
-- 幂等性：应用每次启动都会执行本脚本，因此只用 CREATE TABLE IF NOT EXISTS。
-- （注意：不要用 CREATE INDEX IF NOT EXISTS，MySQL 不支持该语法。）
-- =============================================================================

CREATE TABLE IF NOT EXISTS app_user (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    username          VARCHAR(64) NOT NULL,
    display_name      VARCHAR(64) NOT NULL,
    daily_target_kcal INT         NOT NULL,
    created_at        TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    -- 登录名唯一，用数据库唯一约束兜底，不只靠应用层检查
    CONSTRAINT uk_app_user_username UNIQUE (username),
    -- 目标热量必须为正，防止脏数据
    CONSTRAINT ck_app_user_target CHECK (daily_target_kcal > 0)
);
