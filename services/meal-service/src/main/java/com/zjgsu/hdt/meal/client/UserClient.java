package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 调用 user-service 的客户端。
 *
 * <p>把"HTTP 错误"翻译成两种业务语义：
 * <ul>
 *   <li>对方返回 404 → {@link DependencyResourceNotFoundException}（用户不存在）</li>
 *   <li>连不上/超时/5xx → {@link DependencyUnavailableException}（服务不可用）</li>
 * </ul>
 * 调用方据此决定返回 400 还是 503，二者都不会继续写本地库。
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UserClient(RestTemplate restTemplate,
                      @Value("${services.user-service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        // 去掉末尾斜杠，避免拼出 //api/users
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 查询用户，不存在则抛 {@link DependencyResourceNotFoundException}。
     *
     * <p>注意 catch 顺序：{@code HttpClientErrorException.NotFound} 是
     * {@code RestClientException} 的子类，必须先捕获，否则会被当成"服务不可用"。
     */
    public UserInfo requireUser(Long userId) {
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
            throw new DependencyUnavailableException("user-service", ex);
        }
    }
}
