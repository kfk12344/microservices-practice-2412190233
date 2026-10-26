package com.zjgsu.hdt.user.exception;

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
 * 全局异常处理：把内部异常翻译成结构化的 HTTP 响应。
 *
 * <p>错误码是对外契约，meal-service 依赖 {@code RESOURCE_NOT_FOUND} 来判断"用户不存在"，
 * 因此这里的状态码不能随意改动。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(404, "Not Found", ResourceNotFoundException.CODE, ex.getMessage(), request.getRequestURI())
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
