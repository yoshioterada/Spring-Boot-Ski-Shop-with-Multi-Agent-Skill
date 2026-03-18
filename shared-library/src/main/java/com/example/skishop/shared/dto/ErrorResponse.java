package com.example.skishop.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 Problem Details 互換のエラーレスポンスレコード。
 *
 * @param type      エラー種別 URI
 * @param title     エラータイトル
 * @param status    HTTP ステータスコード
 * @param detail    エラー詳細メッセージ
 * @param instance  エラー発生リソースの URI
 * @param timestamp エラー発生日時 (ISO 8601)
 * @param errors    フィールドバリデーションエラーの詳細リスト
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        List<FieldError> errors
) {

    private static final String BASE_URI = "https://example.com/errors/";

    /**
     * フィールドバリデーションエラーの詳細レコード。
     *
     * @param field         エラーが発生したフィールド名
     * @param message       エラーメッセージ
     * @param rejectedValue 拒否された入力値
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(
            String field,
            String message,
            Object rejectedValue
    ) {}

    /**
     * 基本エラーレスポンスを生成するファクトリメソッド。
     *
     * @param errorCode アプリケーション固有エラーコード (URI のパスに使用)
     * @param title     エラータイトル
     * @param status    HTTP ステータスコード
     * @param detail    エラー詳細メッセージ
     * @param instance  エラー発生リソースの URI
     * @return ErrorResponse インスタンス
     */
    public static ErrorResponse of(
            String errorCode,
            String title,
            int status,
            String detail,
            String instance) {
        return new ErrorResponse(
                BASE_URI + errorCode,
                title,
                status,
                detail,
                instance,
                Instant.now(),
                null
        );
    }

    /**
     * バリデーションエラーレスポンスを生成するファクトリメソッド。
     *
     * @param detail   エラー詳細メッセージ
     * @param instance エラー発生リソースの URI
     * @param errors   フィールドエラーのリスト
     * @return ErrorResponse インスタンス
     */
    public static ErrorResponse validationError(
            String detail,
            String instance,
            List<FieldError> errors) {
        return new ErrorResponse(
                BASE_URI + "validation-failed",
                "Validation Failed",
                400,
                detail,
                instance,
                Instant.now(),
                errors
        );
    }

    /**
     * 404 Not Found エラーレスポンスを生成するファクトリメソッド。
     *
     * @param detail   エラー詳細メッセージ
     * @param instance エラー発生リソースの URI
     * @return ErrorResponse インスタンス
     */
    public static ErrorResponse notFound(String detail, String instance) {
        return of("not-found", "Resource Not Found", 404, detail, instance);
    }

    /**
     * 500 Internal Server Error レスポンスを生成するファクトリメソッド。
     *
     * @param instance エラー発生リソースの URI
     * @return ErrorResponse インスタンス
     */
    public static ErrorResponse internalError(String instance) {
        return of(
                "internal-error",
                "Internal Server Error",
                500,
                "サーバーでエラーが発生しました",
                instance
        );
    }
}
