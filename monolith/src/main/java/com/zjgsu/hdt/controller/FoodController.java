package com.zjgsu.hdt.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.dto.FoodResponse;
import com.zjgsu.hdt.service.FoodService;

/**
 * 食物库接口。
 *
 * <p>职责边界：只做 HTTP 输入输出（解析查询参数、返回 JSON），
 * 查询逻辑与过滤规则全部在 {@link FoodService} 中。
 */
@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    /**
     * GET /api/foods —— 查询食物库。
     *
     * <p>两个查询参数都可省略；同时给出时以 {@code keyword} 优先。
     *
     * @param keyword  名称关键字（忽略大小写）
     * @param category 分类名
     * @return 匹配的食物列表
     */
    @GetMapping
    public List<FoodResponse> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category) {
        return foodService.search(keyword, category);
    }

    /**
     * GET /api/foods/categories —— 列出全部食物分类。
     *
     * @return 分类名列表（去重、按字典序）
     */
    @GetMapping("/categories")
    public List<String> categories() {
        return foodService.listCategories();
    }
}
