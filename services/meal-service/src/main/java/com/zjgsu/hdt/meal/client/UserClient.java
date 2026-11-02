package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 调用 user-service 的客户端 —— 第 07 周改为<strong>通过 Nacos 服务发现</strong>寻址。
 *
 * <p>与第 06 周的区别：以前地址由配置项写死（{@code USER_SERVICE_URL}），
 * 现在每次调用前都通过 {@link ServiceInstanceResolver} 查一次 {@code user-service}
 * 的当前实例，拿到 IP 与端口再拼接原业务路径。
 *
 * <p>为什么每次都要查、而不是启动时查一次缓存起来：实例会上下线、会换 IP。
 * 缓存地址就意味着"对方重启后本服务还往老地址发请求"，
 * 而且这种错误只在对方重启后才暴露，很难排查。
 *
 * <p>三种失败被翻译成不同的业务语义：
 * <ul>
 *   <li>注册中心里没有实例 → {@link DependencyUnavailableException}（503）</li>
 *   <li>对方返回 404 → {@link DependencyResourceNotFoundException}（400）</li>
 *   <li>连接失败 / 超时 / 5xx → {@link DependencyUnavailableException}（503）</li>
 * </ul>
 */
@Component
public class UserClient {

    /** Nacos 里注册的服务名，必须与 user-service 的 spring.application.name 一致。 */
    public static final String SERVICE_NAME = "user-service";

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;
    private final ServiceInstanceResolver instanceResolver;

    public UserClient(RestTemplate restTemplate, ServiceInstanceResolver instanceResolver) {
        this.restTemplate = restTemplate;
        this.instanceResolver = instanceResolver;
    }

    /**
     * 查询用户，不存在则抛 {@link DependencyResourceNotFoundException}。
     *
     * <p>注意 catch 顺序：{@code HttpClientErrorException.NotFound} 是
     * {@code RestClientException} 的子类，必须先捕获，否则 404 会被当成"服务不可用"。
     */
    public UserInfo requireUser(Long userId) {
        // 先解析实例地址（查不到实例会直接抛 503，不会进入后面的请求）
        String baseUrl = instanceResolver.resolveBaseUrl(SERVICE_NAME);
        String url = baseUrl + "/api/users/" + userId;

        try {
            UserInfo user = restTemplate.getForObject(url, UserInfo.class);
            if (user == null) {
                throw DependencyResourceNotFoundException.user(userId);
            }
            return user;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("user-service 返回 404：userId={}", userId);
            throw DependencyResourceNotFoundException.user(userId);
        } catch (RestClientException ex) {
            log.error("调用 user-service 失败：url={}", url, ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }
}
