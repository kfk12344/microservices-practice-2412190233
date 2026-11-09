package com.zjgsu.hdt.meal.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.meal.client.InstanceHitRecorder;
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
    private final InstanceHitRecorder instanceHitRecorder;

    public MealController(MealRecordService mealRecordService,
                          InstanceHitRecorder instanceHitRecorder) {
        this.mealRecordService = mealRecordService;
        this.instanceHitRecorder = instanceHitRecorder;
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
     * 依赖服务实例命中统计（第 08 周新增，<strong>仅用于验证负载均衡</strong>）。
     *
     * <p>返回形如 {@code {"user-service":{"user-service-1":5,"user-service-2":4}}}。
     * 两个实例都出现，说明调用确实被分发到了两台实例上；
     * 只有一台出现，说明负载均衡没生效（或者另一台不健康）。
     *
     * <p>统计的是**真实业务调用**（记录一餐时对 user/food 的校验），
     * 不是单独的健康探针 —— 这正是作业要求的"可核验的实例标识"。
     *
     * <p>注意：它和 {@code /api/meals/{id}} 的路径变量会冲突，
     * Spring 会优先匹配更具体的字面量路径，所以 {@code instances} 不会被当成 id 解析。
     */
    @GetMapping("/instances")
    public Map<String, Map<String, Integer>> instanceHits() {
        return instanceHitRecorder.snapshot();
    }

    /** 清空命中统计，便于重新做一轮可重复的验证。 */
    @DeleteMapping("/instances")
    public ResponseEntity<Void> resetInstanceHits() {
        instanceHitRecorder.reset();
        return ResponseEntity.noContent().build();
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
