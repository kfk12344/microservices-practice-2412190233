package com.zjgsu.hdt.meal.client;

/**
 * 服务实例寻址组件 —— 把"服务名 → 实例基地址"这件事收敛到一个接口。
 *
 * <p>为什么不让业务代码直接依赖 Spring Cloud 的 {@code DiscoveryClient}：
 * <ol>
 *   <li>{@code DiscoveryClient} 是接口，Spring Cloud 会自动装配多个实现
 *       （{@code compositeDiscoveryClient}、{@code simpleDiscoveryClient}、Nacos 的实现），
 *       直接注入容易出现"多个 primary 候选"的歧义；</li>
 *   <li>测试时想替换成假实现，直接顶掉 {@code DiscoveryClient} 会破坏 Spring Cloud
 *       自身的装配；而本接口只有一个生产实现，替换起来干净利落；</li>
 *   <li>将来换注册中心（Eureka / Consul）只需要换这一层的实现，业务代码不动。</li>
 * </ol>
 */
public interface ServiceInstanceResolver {

    /**
     * 解析某个服务名的当前可用实例，返回可直接拼接业务路径的基地址。
     *
     * <p>每次调用都重新向注册中心查询，<strong>不缓存</strong>：
     * 实例会上下线、会换 IP，缓存会导致服务重启后仍向旧地址发请求。
     *
     * @param serviceName 注册中心里的服务名
     * @return 形如 {@code http://172.18.0.5:8081} 的基地址（不带结尾斜杠）
     * @throws com.zjgsu.hdt.meal.exception.DependencyUnavailableException 查不到任何可用实例时
     */
    String resolveBaseUrl(String serviceName);
}
