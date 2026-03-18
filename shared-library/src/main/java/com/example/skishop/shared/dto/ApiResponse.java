package com.example.skishop.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 全 API 共通レスポンス形式。
 *
 * @param <T> レスポンスデータの型
 * @param status  HTTP ステータスコード
 * @param code    アプリケーション固有のコード
 * @param message ユーザー向けメッセージ
 * @param data    レスポンスデータ
 * @param meta    ページネーション等のメタ情報
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data,
        PageInfo meta
) {

    /** 成功レスポンス (データあり、メタなし) */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "SUCCESS", "処理が正常に完了しました", data, null);
    }

    /** 成功レスポンス (データあり、メタあり) */
    public static <T> ApiResponse<T> success(T data, PageInfo meta) {
        return new ApiResponse<>(200, "SUCCESS", "処理が正常に完了しました", data, meta);
    }

    /** 成功レスポンス (任意メッセージ) */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, "SUCCESS", message, data, null);
    }

    /** 作成成功レスポンス (201) */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "CREATED", "リソースが正常に作成されました", data, null);
    }

    /** データなし成功レスポンス (204) */
    public static <Void> ApiResponse<Void> noContent() {
        return new ApiResponse<>(204, "NO_CONTENT", "処理が正常に完了しました", null, null);
    }
}
