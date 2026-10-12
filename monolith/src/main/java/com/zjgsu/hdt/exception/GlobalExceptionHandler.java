package com.zjgsu.hdt.exception;

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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理 —— 把各类异常统一翻译成结构一致的 JSON 错误响应。
 *
 * <p>状态码约定：
 * <table>
 *   <caption>异常到状态码的映射</caption>
 *   <tr><th>异常</th><th>状态码</th><th>含义</th></tr>
 *   <tr><td>MethodArgumentNotValidException</td><td>400</td><td>请求体字段校验失败</td></tr>
 *   <tr><td>HandlerMethodValidationException</td><td>400</td><td>查询参数/路径变量校验失败</td></tr>
 *   <tr><td>HttpMessageNotReadableException</td><td>400</td><td>请求体不是合法 JSON，或枚举值无法识别</td></tr>
 *   <tr><td>ResourceNotFoundException</td><td>404</td><td>资源不存在</td></tr>
 *   <tr><td>BusinessRuleException</td><td>422</td><td>语法合法但违反业务规则</td></tr>
 *   <tr><td>其它 Exception</td><td>500</td><td>未预期错误（日志记录完整栈，响应不泄露内部细节）</td></tr>
 * </table>
 *
 * <p>为什么业务规则用 422 而不是 400：请求本身格式完全正确，是"内容与业务约束冲突"，
 * 422 Unprocessable Entity 表达的正是这个语义，客户端可据此区分"改格式"还是"改数据"。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 请求体字段校验失败（{@code @Valid @RequestBody}）。
     *
     * @param ex      校验异常，含每个字段的失败原因
     * @param request 当前请求，用于回填 path
     * @return 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .sorted()
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request, details);
    }

    /**
     * 查询参数或路径变量校验失败。
     *
     * @param ex      校验异常
     * @param request 当前请求
     * @return 400 响应
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", request,
                List.of("请检查查询参数或路径变量的类型与格式"));
    }

    /**
     * 路径变量或查询参数类型不匹配，例如 {@code GET /api/meals/abc}（期望数字）。
     *
     * <p>没有这个处理器时，Spring 会抛 {@code MethodArgumentTypeMismatchException}，
     * 被最后的兜底分支接住并返回 500 —— 那是把"调用方传错了"误报成"服务端坏了"。
     * 这里显式映射为 400，并在消息里说明期望的类型。
     *
     * @param ex      类型不匹配异常
     * @param request 当前请求
     * @return 400 响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String expected = ex.getRequiredType() == null ? "合法格式" : ex.getRequiredType().getSimpleName();
        String detail = "参数 %s 的取值 %s 无法解析为 %s".formatted(
                ex.getName(), ex.getValue(), expected);
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE",
                "请求参数类型不正确", request, List.of(detail));
    }

    /**
     * 请求体无法解析：非法 JSON，或枚举值不在允许范围内（例如 mealType 传了"夜宵"）。
     *
     * @param ex      解析异常
     * @param request 当前请求
     * @return 400 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String reason = ex.getMostSpecificCause().getMessage();
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "请求体无法解析，请检查 JSON 格式与字段取值（mealType 仅支持 BREAKFAST / LUNCH / DINNER / SNACK）",
                request, reason == null ? List.of() : List.of(reason));
    }

    /**
     * 资源不存在。
     *
     * @param ex      异常
     * @param request 当前请求
     * @return 404 响应
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, null);
    }

    /**
     * 业务规则不满足（R1–R5）。
     *
     * @param ex      异常，携带规则编号
     * @param request 当前请求
     * @return 422 响应
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request,
                List.of("违反规则：" + ex.getRuleCode() + " —— " + MealRuleDoc.describe(ex.getRuleCode())));
    }

    /**
     * 兜底处理未预期异常。
     *
     * <p>对外只给一句中性说明，完整堆栈写进日志——避免把内部实现细节暴露给调用方。
     *
     * @param ex      异常
     * @param request 当前请求
     * @return 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期的服务端异常，path={}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务器内部错误，请稍后重试或联系管理员", request, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                   HttpServletRequest request, List<String> details) {
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }

    /** 规则编号到中文说明的映射，供错误响应附带可读解释。 */
    private static final class MealRuleDoc {
        private static String describe(String ruleCode) {
            return com.zjgsu.hdt.service.MealRecordService.rulebook()
                    .getOrDefault(ruleCode, "未登记的规则编号");
        }
    }
}
