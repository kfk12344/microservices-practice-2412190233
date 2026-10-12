package com.zjgsu.hdt.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.dto.DailySummaryResponse;
import com.zjgsu.hdt.dto.MealRecordRequest;
import com.zjgsu.hdt.dto.MealRecordResponse;
import com.zjgsu.hdt.service.DailySummaryService;
import com.zjgsu.hdt.service.MealRecordService;

/**
 * 饮食记录接口 —— Week 04 的核心 REST 接口集合。
 *
 * <table>
 *   <caption>接口一览</caption>
 *   <tr><th>方法</th><th>路径</th><th>说明</th><th>成功状态码</th></tr>
 *   <tr><td>GET</td><td>/api/meals</td><td>查询某人的记录列表</td><td>200</td></tr>
 *   <tr><td>GET</td><td>/api/meals/{id}</td><td>查询单条记录</td><td>200 / 404</td></tr>
 *   <tr><td>POST</td><td>/api/meals</td><td>新增一条记录</td><td>201 / 400 / 422</td></tr>
 *   <tr><td>GET</td><td>/api/meals/daily-summary</td><td>某日热量汇总</td><td>200</td></tr>
 *   <tr><td>GET</td><td>/api/meals/rules</td><td>列出业务规则与餐次时间窗</td><td>200</td></tr>
 * </table>
 *
 * <p>Controller 不写任何数据访问或规则判断：只负责解析 HTTP 输入、调用 Service、决定状态码。
 */
@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealRecordService mealRecordService;
    private final DailySummaryService dailySummaryService;

    public MealController(MealRecordService mealRecordService, DailySummaryService dailySummaryService) {
        this.mealRecordService = mealRecordService;
        this.dailySummaryService = dailySummaryService;
    }

    /**
     * GET /api/meals —— 查询某人的饮食记录，最新的在前。
     *
     * @param eater 用餐人，默认 {@code demo-user}（与 data.sql 的示例数据一致）
     * @return 记录列表
     */
    @GetMapping
    public List<MealRecordResponse> list(
            @RequestParam(name = "eater", defaultValue = "demo-user") String eater) {
        return mealRecordService.findByEater(eater);
    }

    /**
     * GET /api/meals/{id} —— 查询单条记录。
     *
     * @param id 记录 ID
     * @return 记录；不存在时由全局异常处理返回 404
     */
    @GetMapping("/{id}")
    public MealRecordResponse getById(@PathVariable("id") Long id) {
        return mealRecordService.findById(id);
    }

    /**
     * POST /api/meals —— 新增一条饮食记录。
     *
     * <p>请求体校验分两层：字段级校验由 {@code @Valid} 完成（400），
     * 业务规则由 Service 完成（422）。
     *
     * @param request 创建请求
     * @return 201 Created，响应体为落库后的记录（含服务端算好的营养合计）
     */
    @PostMapping
    public ResponseEntity<MealRecordResponse> create(@Valid @RequestBody MealRecordRequest request) {
        MealRecordResponse created = mealRecordService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/meals/daily-summary —— 某人某日的摄入汇总。
     *
     * @param eater 用餐人，默认 {@code demo-user}
     * @param date  统计日期（{@code yyyy-MM-dd}），省略则取今天
     * @return 当日汇总；当天没有记录时合计为 0
     */
    @GetMapping("/daily-summary")
    public DailySummaryResponse dailySummary(
            @RequestParam(name = "eater", defaultValue = "demo-user") String eater,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dailySummaryService.summarize(eater, date);
    }

    /**
     * GET /api/meals/rules —— 列出系统当前生效的业务规则与餐次时间窗。
     *
     * <p>把规则做成接口而不是只写在文档里的理由：客户端可以在提交前拉取规则做前置提示，
     * 规则调整后也不必同步改前端文案。规则常量与校验逻辑共用 {@code BusinessLimits}，
     * 不会出现"接口说 1500、实际按 1200 拦"的不一致。
     *
     * @return 规则清单与餐次时间窗
     */
    @GetMapping("/rules")
    public Map<String, Object> rules() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rules", MealRecordService.rulebook());
        body.put("mealTypeWindows", MealRecordService.mealTypeWindows());
        return body;
    }
}
