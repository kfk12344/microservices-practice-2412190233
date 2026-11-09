package com.zjgsu.hdt.meal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * user-service 的声明式客户端（OpenFeign）。
 *
 * <p>{@code name = "user-service"} 写的是<strong>服务名</strong>，不是 IP、也不是端口。
 * 真正的地址由 Spring Cloud LoadBalancer 在每次调用前从注册中心挑一个实例得出，
 * 因此 user-service 扩容成 2 个实例、或者换 IP，本服务都不用改任何代码。
 *
 * <p>这个接口只负责"怎么发请求"；把 HTTP 失败翻译成业务异常、以及记录命中的实例，
 * 都在门面类 {@link UserClient} 里做 —— 这样 Feign 接口保持成纯契约，便于对照 OpenAPI 阅读。
 */
@FeignClient(name = UserFeignApi.SERVICE_NAME)
public interface UserFeignApi {

    /** Nacos 里注册的服务名，必须与 user-service 的 spring.application.name 一致。 */
    String SERVICE_NAME = "user-service";

    /**
     * 按 ID 查询用户。对方不存在时返回 404，Feign 会抛 {@code FeignException.NotFound}。
     *
     * @param id 用户 ID
     * @return 用户信息；响应体里的 {@code servedBy} 是**实际处理这次请求的实例标识**
     */
    @GetMapping("/api/users/{id}")
    UserInfo getUser(@PathVariable("id") Long id);
}
