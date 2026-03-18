package com.example.skishop.shared.exception;

/**
 * リソースが見つからない場合の例外。
 * HTTP 404 Not Found に対応する。
 */
public class ResourceNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "not-found";

    /**
     * リソース種別と ID を指定して ResourceNotFoundException を生成する。
     *
     * @param resourceType リソース種別名 (例: "User", "Order")
     * @param id           リソースの識別子
     */
    public ResourceNotFoundException(String resourceType, Object id) {
        super(ERROR_CODE, "%s が見つかりません: %s".formatted(resourceType, id),
                org.springframework.http.HttpStatus.NOT_FOUND);
    }

    /**
     * 任意のメッセージで ResourceNotFoundException を生成する。
     *
     * @param message エラーメッセージ
     */
    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message, org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
