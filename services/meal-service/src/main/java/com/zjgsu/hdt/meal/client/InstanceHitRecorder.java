package com.zjgsu.hdt.meal.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 记录"每个依赖服务的每个实例被调用了多少次"。
 *
 * <p>存在的目的只有一个：<strong>让负载均衡可以被证明</strong>。
 * 作业里明确要求"仅展示注册列表不算完成分发验证"，所以必须有可核验的实际分发证据。
 *
 * <p>做法：user-service / food-service 在响应体里带上 {@code servedBy}（自己的实例标识），
 * meal-service 每次调用成功后把标识记一笔。于是
 * {@code GET /api/meals/instances} 返回的计数表就是真实的调用分布 ——
 * 如果两个实例都出现在表里，说明调用确实被分发到两台机器上，而不是只打到一台。
 *
 * <p>注意这是**诊断用途**的统计，不是业务数据：进程重启即清空，也不落库。
 */
@Component
public class InstanceHitRecorder {

    /** serviceName → (instanceId → 调用次数) */
    private final Map<String, Map<String, AtomicInteger>> hits = new ConcurrentHashMap<>();

    /**
     * 记一次调用。
     *
     * @param serviceName 被调用的服务名
     * @param instanceId  对方在响应里回报的实例标识；为空时记为 {@code unknown}
     */
    public void record(String serviceName, String instanceId) {
        if (serviceName == null || serviceName.isBlank()) {
            return;
        }
        String key = (instanceId == null || instanceId.isBlank()) ? "unknown" : instanceId;
        hits.computeIfAbsent(serviceName, name -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, id -> new AtomicInteger())
                .incrementAndGet();
    }

    /**
     * 当前计数快照（按服务名、实例名排序，方便人读与脚本比对）。
     *
     * @return serviceName → (instanceId → 次数)
     */
    public Map<String, Map<String, Integer>> snapshot() {
        Map<String, Map<String, Integer>> result = new TreeMap<>();
        hits.forEach((service, perInstance) -> {
            Map<String, Integer> inner = new TreeMap<>();
            perInstance.forEach((instance, counter) -> inner.put(instance, counter.get()));
            result.put(service, inner);
        });
        // 固定字段顺序，避免 JSON 每次不同
        return new LinkedHashMap<>(result);
    }

    /** 清空计数（e2e 脚本在统计前调用，保证从 0 开始）。 */
    public void reset() {
        hits.clear();
    }
}
