-- =============================================================================
-- meal-service 建表脚本
--
-- 只建本服务拥有的 meal_record 与 meal_item 两张表。
--
-- 注意两个"故意不加外键"的列：
--   meal_record.user_id  → 用户在 user-service 的库里，物理上无法建外键
--   meal_item.food_id    → 食物在 food-service 的库里，同理
-- 这两列只保存对方的 ID，存在性由应用层调用对方接口校验（见 MealRecordService）。
-- 真正的外键只有 meal_item.meal_record_id → 本服务的 meal_record（同库同服务）。
--
-- 幂等性：只用 CREATE TABLE IF NOT EXISTS。
-- （MySQL 不支持 CREATE INDEX IF NOT EXISTS，索引一律内联在建表语句里。）
-- =============================================================================

CREATE TABLE IF NOT EXISTS meal_record (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    -- 指向另一个服务的用户，只存 ID
    user_id    BIGINT      NOT NULL,
    meal_type  VARCHAR(16) NOT NULL,
    eaten_at   TIMESTAMP   NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_meal_record_type
        CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    -- 按"用户 + 时间"查询是本服务最主要的访问路径
    INDEX idx_meal_record_user_time (user_id, eaten_at)
);

CREATE TABLE IF NOT EXISTS meal_item (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    -- 指向本服务的 meal_record，这是唯一真正的跨表外键
    meal_record_id BIGINT        NOT NULL,
    -- 指向另一个服务的食物，只存 ID
    food_id        BIGINT        NOT NULL,
    -- 食物名称与营养值的"快照"：避免查询时逐条调用 food-service
    food_name      VARCHAR(64)   NOT NULL,
    portion_grams  INT           NOT NULL,
    calories       DECIMAL(10, 2) NOT NULL,
    protein        DECIMAL(10, 2) NOT NULL,
    fat            DECIMAL(10, 2) NOT NULL,
    carbs          DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meal_item_record
        FOREIGN KEY (meal_record_id) REFERENCES meal_record (id) ON DELETE CASCADE,
    -- 同一条记录里同一种食物只应出现一次
    CONSTRAINT uk_meal_item_record_food UNIQUE (meal_record_id, food_id),
    CONSTRAINT ck_meal_item_portion_positive CHECK (portion_grams > 0)
);
