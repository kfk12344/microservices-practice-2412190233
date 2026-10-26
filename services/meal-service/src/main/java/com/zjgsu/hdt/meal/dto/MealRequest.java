package com.zjgsu.hdt.meal.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 记录一餐的请求体。
 *
 * <p>{@code userId} 与各明细的 {@code foodId} 都是"跨服务引用"：
 * 本服务本地没有对应的表，必须在处理过程中调用其他服务校验。
 *
 * @param userId   用户 ID（会在 user-service 校验存在性）
 * @param mealType BREAKFAST / LUNCH / DINNER / SNACK
 * @param eatenAt  用餐时间
 * @param note     备注
 * @param items    明细，至少一条
 */
public record MealRequest(
        @NotNull(message = "userId 不能为空")
        Long userId,

        @NotBlank(message = "餐次不能为空")
        String mealType,

        @NotNull(message = "用餐时间不能为空")
        LocalDateTime eatenAt,

        @Size(max = 255, message = "备注不能超过 255 字")
        String note,

        @NotEmpty(message = "至少要有一种食物")
        @Size(max = 20, message = "一次最多 20 种食物")
        List<@Valid MealItemRequest> items
) {
}
