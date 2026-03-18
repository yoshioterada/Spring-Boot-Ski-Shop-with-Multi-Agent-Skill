package com.example.skishop.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * ビジネスルール違反を表す例外。
 * HTTP 422 Unprocessable Entity に対応する。
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    /**
     * デフォルトのHTTPステータス (422) でビジネス例外を生成する。
     *
     * @param errorCode アプリケーション固有のエラーコード
     * @param message   ユーザー向けエラーメッセージ
     */
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    /**
     * 任意のHTTPステータスでビジネス例外を生成する。
     *
     * @param errorCode  アプリケーション固有のエラーコード
     * @param message    ユーザー向けエラーメッセージ
     * @param httpStatus HTTP ステータスコード
     */
    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * 原因例外を持つビジネス例外を生成する。
     *
     * @param errorCode アプリケーション固有のエラーコード
     * @param message   ユーザー向けエラーメッセージ
     * @param cause     原因例外
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
