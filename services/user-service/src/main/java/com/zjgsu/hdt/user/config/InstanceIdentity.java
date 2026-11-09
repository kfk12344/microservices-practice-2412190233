package com.zjgsu.hdt.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本实例的唯一标识，用于<strong>验证负载均衡</strong>。
 *
 * <p>同一个服务起多个实例时，它们的 {@code spring.application.name} 是一样的，
 * 光看响应分不出是哪台机器处理的。所以每个实例启动时确定一个 ID，
 * 随业务响应一起返回（{@code servedBy} 字段），调用方据此就能统计分发情况。
 *
 * <p>取值优先级：
 * <ol>
 *   <li>环境变量 {@code APP_INSTANCE_ID}（compose 里给每个实例分别设置，如 {@code user-service-1}）</li>
 *   <li>没设置时退化为 {@code 应用名:端口}，至少能区分同一宿主机上的不同端口</li>
 * </ol>
 */
@Component
public class InstanceIdentity {

    private final String id;

    public InstanceIdentity(
            @Value("${app.instance-id:}") String configuredId,
            @Value("${spring.application.name:service}") String applicationName,
            @Value("${server.port:0}") String port) {
        this.id = (configuredId == null || configuredId.isBlank())
                ? applicationName + ":" + port
                : configuredId;
    }

    /** 本实例标识。 */
    public String id() {
        return id;
    }
}
