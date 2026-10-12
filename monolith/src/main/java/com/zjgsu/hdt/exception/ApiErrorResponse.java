package com.zjgsu.hdt.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一的接口错误响应体。
 *
 * <p>所有错误（参数非法、资源不存在、业务规则不满足、服务器内部错误）都返回同一结构，
 * 客户端只需要写一套错误处理逻辑。字段含义：
 * <ul>
 *   <li>{@code timestamp} —— 服务端出错时间，排查日志时用于对时；</li>
 *   <li>{@code status} —— HTTP 状态码，与响应行一致；</li>
 *   <li>{@code error} —— 状态码的简短英文名，例如 {@code Unprocessable Entity}；</li>
 *   <li>{@code code} —— 便于程序判断的稳定标识，例如 {@code BUSINESS_RULE_VIOLATION}；</li>
 *   <li>{@code message} —— 面向使用者的中文说明；</li>
 *   <li>{@code path} —— 出错的请求路径；</li>
 *   <li>{@code details} —— 明细列表，字段校验失败时逐条列出是哪个字段出了什么问题。</li>
 * </ul>
 *
 * @param timestamp 出错时间
 * @param status    HTTP 状态码
 * @param error     状态码英文名
 * @param code      稳定错误标识，供程序判断
 * @param message   面向使用者的说明
 * @param path      请求路径
 * @param details   明细列表，可为空
 */
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details
) {

    /**
     * 构造错误响应。
     *
     * @param status   HTTP 状态码
     * @param error    状态码英文名
     * @param code     稳定错误标识
     * @param message  使用者可读说明
     * @param path     请求路径
     * @param details  明细，可为 {@code null}
     * @return 错误响应体
     */
    public static ApiErrorResponse of(int status, String error, String code,
                                     String message, String path, List<String> details) {
        return new ApiErrorResponse(
                LocalDateTime.now(),
                status,
                error,
                code,
                message,
                path,
                details == null ? List.of() : List.copyOf(details)
        );
    }
}
