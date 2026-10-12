-- =============================================================================
-- 个人健康饮食记录管理系统 —— 业务表结构
--
-- 说明：
--   * 本文件既用于 MySQL（本地运行），也用于测试环境的 H2（MySQL 兼容模式），
--     因此只使用两者都支持的标准 DDL 与 IF NOT EXISTS 语法。
--   * 表结构在 JPA 侧设为 ddl-auto=none，由本文件唯一负责，避免两套 DDL 不一致。
--   * 关系与约束的说明文字见 docs/database.md。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 表 1：food —— 食物库（基准营养数据，一律按每 100 克记录）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS food (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)  NOT NULL,
    category            VARCHAR(32)  NOT NULL,
    calories_per_100g   DECIMAL(8,2) NOT NULL,
    protein_per_100g    DECIMAL(8,2) NOT NULL,
    fat_per_100g        DECIMAL(8,2) NOT NULL,
    carbs_per_100g      DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    -- 食物名唯一：避免同一食物出现多条口径不一的记录
    CONSTRAINT uk_food_name UNIQUE (name),
    -- 营养值不允许为负
    CONSTRAINT ck_food_nutrients_non_negative
        CHECK (calories_per_100g >= 0 AND protein_per_100g >= 0
               AND fat_per_100g >= 0 AND carbs_per_100g >= 0)
);

-- -----------------------------------------------------------------------------
-- 表 2：meal_record —— 饮食记录（一次用餐）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS meal_record (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    eater      VARCHAR(64)  NOT NULL,
    meal_type  VARCHAR(16)  NOT NULL,
    eaten_at   TIMESTAMP    NOT NULL,
    note       VARCHAR(255),
    PRIMARY KEY (id),
    -- 餐次只能是四个枚举值，在数据库层兜底，防止绕过应用直接写脏数据
    CONSTRAINT ck_meal_record_type
        CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'))
);

-- 按"用餐人 + 时间"查询是日用汇总的主要访问路径，建复合索引
CREATE INDEX IF NOT EXISTS idx_meal_record_eater_time ON meal_record (eater, eaten_at);

-- -----------------------------------------------------------------------------
-- 表 3：meal_item —— 记录与食物的关联表，份量存在这里
--
-- 之所以单独建这张中间表：同一种食物在不同记录里份量不同，
-- 份量属于"记录 × 食物"的组合，而不是食物本身的属性。
--
-- 主键用自增代理键，而不是 (meal_record_id, food_id) 复合主键：
-- 业务约束"一次用餐里同一种食物只能有一行"由下面的唯一约束保证，
-- 而代理键让 JPA 映射简单、并在"记录尚未持久化"的时序下不出现问题
-- （复合主键配 @MapsId 时 Hibernate 7 会抛 PropertyAccessException，已实测）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS meal_item (
    id             BIGINT  NOT NULL AUTO_INCREMENT,
    meal_record_id BIGINT  NOT NULL,
    food_id        BIGINT  NOT NULL,
    portion_grams  INTEGER NOT NULL,
    PRIMARY KEY (id),
    -- 业务约束：同一次用餐里同一种食物只能出现一行
    CONSTRAINT uk_meal_item_record_food UNIQUE (meal_record_id, food_id),
    -- 记录被删除时明细一并删除
    CONSTRAINT fk_meal_item_record FOREIGN KEY (meal_record_id) REFERENCES meal_record (id) ON DELETE CASCADE,
    -- 食物库条目被引用时不允许删除，防止历史记录丢失营养依据
    CONSTRAINT fk_meal_item_food FOREIGN KEY (food_id) REFERENCES food (id),
    -- 份量必须为正数
    CONSTRAINT ck_meal_item_portion_positive CHECK (portion_grams > 0)
);
