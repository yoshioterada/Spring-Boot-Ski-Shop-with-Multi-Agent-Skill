package com.example.skishop.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 全サービス共通のグローバル例外ハンドラー。
 * RFC 7807 Problem Details 形式でエラーレスポンスを返す。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://skishop.example.com/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("リソースが見つかりません: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(ERROR_TYPE_BASE + "not-found"));
        problem.setTitle("Resource Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("ビジネスルール違反: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(ERROR_TYPE_BASE + "business-error"));
        problem.setTitle("Business Rule Violation");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(
            ConflictException ex, HttpServletRequest request) {
        log.warn("リソース競合: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(ERROR_TYPE_BASE + "conflict"));
        problem.setTitle("Resource Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode());
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("楽観的ロック競合: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "リソースが他のリクエストによって更新されました。再度お試しください。");
        problem.setType(URI.create(ERROR_TYPE_BASE + "optimistic-lock-conflict"));
        problem.setTitle("Optimistic Lock Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorMap)
                .toList();
        var detail = "入力内容に誤りがあります（%d件）".formatted(fieldErrors.size());
        log.warn("入力バリデーションエラー: {}", detail);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create(ERROR_TYPE_BASE + "validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("予期しないエラー: {}", ex.getMessage(), ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "サーバーでエラーが発生しました。");
        problem.setType(URI.create(ERROR_TYPE_BASE + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private Map<String, String> toFieldErrorMap(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "",
                "rejectedValue", error.getRejectedValue() != null ? error.getRejectedValue().toString() : ""
        );
    }
}
