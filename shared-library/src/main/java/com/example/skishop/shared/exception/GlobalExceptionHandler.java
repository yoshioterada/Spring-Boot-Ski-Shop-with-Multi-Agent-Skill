package com.example.skishop.shared.exception;

import com.example.skishop.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;

/**
 * 全コントローラー共通の例外ハンドラー。
 * RFC 7807 Problem Details 形式でエラーレスポンスを返す。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 拒否値をマスクすべき機密フィールド名のセット */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "confirmPassword", "currentPassword", "newPassword",
            "token", "accessToken", "refreshToken", "apiKey", "secret",
            "creditCardNumber", "cvv", "cardNumber"
    );

    /**
     * バリデーションエラーのハンドリング (400 Bad Request)。
     * 全フィールドのエラーを一括で返す。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        BindingResult bindingResult = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        maskIfSensitive(fe.getField(), fe.getRejectedValue())))
                .toList();

        log.warn("バリデーションエラー: パス={}, エラー件数={}", request.getRequestURI(), fieldErrors.size());

        ErrorResponse errorResponse = ErrorResponse.validationError(
                "入力内容に誤りがあります（%d件）".formatted(fieldErrors.size()),
                request.getRequestURI(),
                fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * リソース未検出エラーのハンドリング (404 Not Found)。
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("リソース未検出: パス={}, メッセージ={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.notFound(ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * ビジネスルール違反エラーのハンドリング (422 Unprocessable Entity)。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("ビジネスルール違反: パス={}, コード={}, メッセージ={}",
                request.getRequestURI(), ex.getErrorCode(), ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                "Business Rule Violation",
                ex.getHttpStatus().value(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * 予期しないエラーのハンドリング (500 Internal Server Error)。
     * 内部情報はログにのみ記録し、クライアントには最小限の情報を返す。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error("予期しないエラー: パス={}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.internalError(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 機密フィールドの拒否値をマスクする。
     * パスワード、トークン、クレジットカード番号等の機密フィールドは "***" に置換する。
     *
     * @param fieldName     フィールド名
     * @param rejectedValue 拒否された値
     * @return マスク済みの値、または元の値
     */
    private Object maskIfSensitive(String fieldName, Object rejectedValue) {
        if (fieldName == null || rejectedValue == null) {
            return rejectedValue;
        }
        return SENSITIVE_FIELDS.contains(fieldName) ? "***" : rejectedValue;
    }
}
