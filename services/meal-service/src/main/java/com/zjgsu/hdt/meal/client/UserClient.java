package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

import feign.FeignException;

/**
 * 调用 user-service 的门面类 —— 第 08 周起内部改用 <strong>OpenFeign</strong>。
 *
 * <p>为什么还留这一层，而不是让业务直接注入 {@link UserFeignApi}：
 * <ul>
 *   <li>Feign 抛的是 HTTP 层面的异常（{@code FeignException.NotFound}、
 *       {@code RetryableException}、{@code IllegalStateException: no instance}），
 *       业务层不该认识这些东西；</li>
 *   <li>把"HTTP 失败 → 业务语义"的翻译集中在一处，好写测试也好改；</li>
 *   <li>顺带记录本次命中的实例标识，供负载均衡验证使用。</li>
 * </ul>
 *
 * <p>三种失败被翻译成不同的业务语义（与第 07 周保持一致，对外契约没有变化）：
 * <ul>
 *   <li>对方返回 404 → {@link DependencyResourceNotFoundException}（对外 400）</li>
 *   <li>连不上 / 超时 / 5xx / 注册中心里没有实例 → {@link DependencyUnavailableException}（对外 503）</li>
 * </ul>
 *
 * <p>关于"没有可用实例"这一种：Feign 在 LoadBalancer 挑不到实例时会抛
 * {@code IllegalStateException}（"Load balancer does not contain an instance"），
 * 它不属于 {@code FeignException}，所以下面的 catch 用的是 {@code Exception} 兜底。
 */
@Component
public class UserClient {

    /** Nacos 里注册的服务名，必须与 user-service 的 spring.application.name 一致。 */
    public static final String SERVICE_NAME = UserFeignApi.SERVICE_NAME;

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final UserFeignApi api;
    private final InstanceHitRecorder recorder;

    public UserClient(UserFeignApi api, InstanceHitRecorder recorder) {
        this.api = api;
        this.recorder = recorder;
    }

    /**
     * 查询用户，不存在则抛 {@link DependencyResourceNotFoundException}。
     *
     * @param userId 用户 ID
     * @return 用户信息（含处理该请求的实例标识）
     */
    public UserInfo requireUser(Long userId) {
        try {
            UserInfo user = api.getUser(userId);
            if (user == null) {
                throw DependencyResourceNotFoundException.user(userId);
            }
            recorder.record(SERVICE_NAME, user.servedBy());
            return user;
        } catch (DependencyResourceNotFoundException ex) {
            // 自己抛的，原样向上抛（不要被下面的兜底吃掉）
            throw ex;
        } catch (FeignException.NotFound ex) {
            log.warn("user-service 返回 404：userId={}", userId);
            throw DependencyResourceNotFoundException.user(userId);
        } catch (FeignException ex) {
            // 5xx、以及 Feign 自己包装的其它 HTTP 错误
            log.error("调用 user-service 失败：userId={}，status={}", userId, ex.status(), ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        } catch (Exception ex) {
            // 连不上、超时、LoadBalancer 找不到实例等
            log.error("调用 user-service 失败（未拿到有效响应）：userId={}", userId, ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }
}
