package com.zjgsu.hdt.meal.controller;

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

import com.zjgsu.hdt.meal.dto.MealRequest;
import com.zjgsu.hdt.meal.dto.MealResponse;
import com.zjgsu.hdt.meal.service.MealRecordService;

/**
 * 饮食记录 REST 接口。
 *
 * <p>{@code POST /api/meals} 是本项目的跨服务业务流程入口：
 * 它会在内部调用 user-service 与 food-service，任一校验失败都不会写入本地。
 */
@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealRecordService mealRecordService;

    public MealController(MealRecordService mealRecordService) {
        this.mealRecordService = mealRecordService;
    }

    /** 按用户查询其全部饮食记录（按用餐时间倒序）。 */
    @GetMapping
    public List<MealResponse> listByUser(@RequestParam(name = "userId") Long userId) {
        return mealRecordService.findByUserId(userId);
    }

    /** 按 ID 查询单条记录。 */
    @GetMapping("/{id}")
    public MealResponse getById(@PathVariable Long id) {
        return mealRecordService.findById(id);
    }

    /**
     * 记录一餐。
     *
     * <p>可能返回：201 成功 / 400 参数或引用资源非法 / 422 业务规则 / 503 依赖服务不可用。
     */
    @PostMapping
    public ResponseEntity<MealResponse> create(@Valid @RequestBody MealRequest request) {
        MealResponse created = mealRecordService.create(request);
        return ResponseEntity.created(URI.create("/api/meals/" + created.id())).body(created);
    }
}
