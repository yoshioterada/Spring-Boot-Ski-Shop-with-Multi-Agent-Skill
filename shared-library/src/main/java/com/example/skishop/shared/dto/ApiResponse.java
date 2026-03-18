package com.example.skishop.shared.dto;

/**
 * 汎用 API レスポンス DTO。
 *
 * @param <T> レスポンスデータの型
 * @param status  "SUCCESS" または "ERROR"
 * @param code    エラーコード（正常時は null）
 * @param message ユーザー向けメッセージ
 * @param data    レスポンスデータ
 * @param meta    ページング情報等のメタデータ
 */
public record ApiResponse<T>(
        String status,
        String code,
        String message,
        T data,
        Object meta
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", null, null, data, null);
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>("SUCCESS", null, null, data, meta);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>("ERROR", code, message, null, null);
    }
}
