package com.zjgsu.hdt.food.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.food.config.InstanceIdentity;
import com.zjgsu.hdt.food.dto.FoodRequest;
import com.zjgsu.hdt.food.dto.FoodResponse;
import com.zjgsu.hdt.food.service.FoodService;

/**
 * 食物服务 REST 接口。
 *
 * <p>{@code GET /api/foods/{id}} 的 200 / 404 语义是 meal-service 跨服务校验时依赖的契约。
 */
@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    /**
     * 本实例标识（第 08 周）。注入它是为了让 {@code GET /api/foods/{id}} 能告诉调用方
     * "这一次是哪台实例处理的"，从而验证负载均衡是否真的在多个实例之间分发。
     */
    private final InstanceIdentity instanceIdentity;

    public FoodController(FoodService foodService, InstanceIdentity instanceIdentity) {
        this.foodService = foodService;
        this.instanceIdentity = instanceIdentity;
    }

    /** 食物列表，可按分类过滤。 */
    @GetMapping
    public List<FoodResponse> list(@RequestParam(name = "category", required = false) String category) {
        return foodService.listAll(category);
    }

    /** 按 ID 查询单个食物；不存在返回 404。 */
    @GetMapping("/{id}")
    public FoodResponse getById(@PathVariable Long id) {
        // 带上实例标识：meal-service 用它统计请求实际落在哪个实例上
        return foodService.findById(id).servedBy(instanceIdentity.id());
    }

    /** 新增食物，成功返回 201 与 Location。 */
    @PostMapping
    public ResponseEntity<FoodResponse> create(@Valid @RequestBody FoodRequest request) {
        FoodResponse created = foodService.create(request);
        return ResponseEntity.created(URI.create("/api/foods/" + created.id())).body(created);
    }
}
