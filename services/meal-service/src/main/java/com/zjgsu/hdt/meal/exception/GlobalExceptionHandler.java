package com.zjgsu.hdt.meal.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理 —— 把各种失败翻译成明确的 HTTP 状态码与结构化错误码。
 *
 * <p>拆分之后这张映射表尤其重要，因为调用方需要区分四类失败：
 *
 * <table border="1">
 *   <caption>错误码映射</caption>
 *   <tr><th>状态码</th><th>错误码</th><th>含义</th><th>调用方该怎么办</th></tr>
 *   <tr><td>400</td><td>USER_NOT_FOUND / FOOD_NOT_FOUND</td><td>引用的用户或食物不存在</td><td>改请求</td></tr>
 *   <tr><td>400</td><td>VALIDATION_FAILED 等</td><td>参数格式不合法</td><td>改请求</td></tr>
 *   <tr><td>404</td><td>RESOURCE_NOT_FOUND</td><td>记录本身不存在</td><td>改请求</td></tr>
 *   <tr><td>422</td><td>BUSINESS_RULE_VIOLATION</td><td>格式合法但违反业务规则</td><td>改数据</td></tr>
 *   <tr><td>503</td><td>DEPENDENCY_UNAVAILABLE</td><td>依赖服务连不上 / 超时</td><td>稍后重试</td></tr>
 * </table>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 本服务的资源不存在 → 404。 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(404, "Not Found", ResourceNotFoundException.CODE, ex.getMessage(), request.getRequestURI())
        );
    }

    /**
     * 跨服务引用的资源不存在 → 400。
     *
     * <p>用 400 而不是 404：{@code /api/meals} 这个路径本身是存在的，
     * 不存在的是请求体里引用的 {@code userId} / {@code foodId}。
     */
    @ExceptionHandler(DependencyResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleDependencyResourceNotFound(
            DependencyResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("跨服务引用不存在: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", ex.getCode(), ex.getMessage(), request.getRequestURI())
        );
    }

    /**
     * 依赖服务不可用 → 503。
     *
     * <p>503 而不是 500：这不是本服务的 bug，而是下游暂时不可用，
     * 调用方稍后重试即可，语义上要能区分开。
     */
    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ApiError> handleDependencyUnavailable(
            DependencyUnavailableException ex, HttpServletRequest request) {
        log.error("依赖服务不可用: {}", ex.getService(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiError.of(503, "Service Unavailable", DependencyUnavailableException.CODE,
                        ex.getMessage() + "；本次请求未写入任何数据，可稍后重试",
                        request.getRequestURI())
        );
    }

    /** 业务规则违反 → 422。 */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        log.info("违反业务规则 {}: {}", ex.getRuleId(), ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(
                ApiError.of(422, "Unprocessable Entity", BusinessRuleException.CODE, ex.getMessage(),
                        request.getRequestURI(), List.of("违反规则：" + ex.getRuleId()))
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", "VALIDATION_FAILED", "请求参数校验失败",
                        request.getRequestURI(), details)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", "MALFORMED_REQUEST_BODY", "请求体不是合法的 JSON，或字段类型不匹配",
                        request.getRequestURI())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", "INVALID_PARAMETER_TYPE",
                        "参数 " + ex.getName() + " 的类型不正确", request.getRequestURI())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ApiError.of(400, "Bad Request", "INVALID_ARGUMENT", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期的异常: {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiError.of(500, "Internal Server Error", "INTERNAL_ERROR", "服务内部错误", request.getRequestURI())
        );
    }
}
