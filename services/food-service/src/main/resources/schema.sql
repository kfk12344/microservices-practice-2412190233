-- =============================================================================
-- food-service 建表脚本
--
-- 只建本服务拥有的 food 一张表。用户表在 user-service，
-- 饮食记录表在 meal-service —— 本服务不建、不读、不写它们。
--
-- 幂等性：只用 CREATE TABLE IF NOT EXISTS。
-- （MySQL 不支持 CREATE INDEX IF NOT EXISTS，若有索引需求请内联进建表语句。）
-- =============================================================================

CREATE TABLE IF NOT EXISTS food (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    name               VARCHAR(64) NOT NULL,
    category           VARCHAR(32) NOT NULL,
    calories_per_100g  DECIMAL(8, 2) NOT NULL,
    protein_per_100g   DECIMAL(8, 2) NOT NULL,
    fat_per_100g       DECIMAL(8, 2) NOT NULL,
    carbs_per_100g     DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_food_name UNIQUE (name),
    -- 营养值不允许为负，数据库层兜底
    CONSTRAINT ck_food_nutrients_non_negative
        CHECK (calories_per_100g >= 0 AND protein_per_100g >= 0
               AND fat_per_100g >= 0 AND carbs_per_100g >= 0)
);
