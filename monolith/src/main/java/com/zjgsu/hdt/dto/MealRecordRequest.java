package com.zjgsu.hdt.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.zjgsu.hdt.model.MealType;

/**
 * 创建饮食记录的请求体（DTO）。
 *
 * <p>用独立的 DTO 而不是直接接收实体，原因有三个：
 * <ol>
 *   <li>实体主键由数据库生成，客户端不应也无法指定，避免越权修改他人记录；</li>
 *   <li>实体的字段与数据库列一一对应，直接暴露会让表结构变更直接冲击接口契约；</li>
 *   <li>校验注解可以就近声明，让非法请求在进入 Service 之前就被挡住。</li>
 * </ol>
 *
 * <p>示例请求体：
 * <pre>{@code
 * {
 *   "eater": "demo-user",
 *   "mealType": "BREAKFAST",
 *   "eatenAt": "2026-09-29T08:00:00",
 *   "note": "标准早餐",
 *   "items": [
 *     { "foodId": 2, "portionGrams": 80 },
 *     { "foodId": 4, "portionGrams": 50 }
 *   ]
 * }
 * }</pre>
 *
 * @param eater    用餐人标识
 * @param mealType 餐次
 * @param eatenAt  用餐时间，ISO-8601 本地时间（例如 {@code 2026-09-29T08:00:00}）
 * @param note     备注，可空
 * @param items    食物与份量列表，至少一项
 */
public record MealRecordRequest(
        @NotBlank(message = "用餐人不能为空")
        @Size(max = 64, message = "用餐人长度不能超过 64")
        String eater,

        @NotNull(message = "餐次不能为空（BREAKFAST / LUNCH / DINNER / SNACK）")
        MealType mealType,

        @NotNull(message = "用餐时间不能为空，格式示例 2026-09-29T08:00:00")
        java.time.LocalDateTime eatenAt,

        @Size(max = 255, message = "备注长度不能超过 255")
        String note,

        @NotEmpty(message = "至少需要包含一种食物")
        @Valid
        List<MealItemRequest> items
) {
}
