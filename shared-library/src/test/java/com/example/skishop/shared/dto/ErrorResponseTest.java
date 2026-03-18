package com.example.skishop.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse テスト")
class ErrorResponseTest {

    @Test
    @DisplayName("notFound() を呼び出した場合、status 404 の ErrorResponse を返す")
    void should_return404ErrorResponse_when_notFoundCalled() {
        // Arrange
        var detail = "User が見つかりません: 123";
        var instance = "/api/v1/users/123";

        // Act
        var result = ErrorResponse.notFound(detail, instance);

        // Assert
        assertThat(result.status()).isEqualTo(404);
        assertThat(result.title()).isEqualTo("Resource Not Found");
        assertThat(result.detail()).isEqualTo(detail);
        assertThat(result.instance()).isEqualTo(instance);
        assertThat(result.type()).contains("not-found");
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.errors()).isNull();
    }

    @Test
    @DisplayName("internalError() を呼び出した場合、status 500 の ErrorResponse を返す")
    void should_return500ErrorResponse_when_internalErrorCalled() {
        // Arrange
        var instance = "/api/v1/users";

        // Act
        var result = ErrorResponse.internalError(instance);

        // Assert
        assertThat(result.status()).isEqualTo(500);
        assertThat(result.instance()).isEqualTo(instance);
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("validationError() を呼び出した場合、フィールドエラーリストを含む ErrorResponse を返す")
    void should_includeFieldErrors_when_validationErrorCalled() {
        // Arrange
        var fieldErrors = List.of(
                new ErrorResponse.FieldError("email", "有効なメールアドレスを入力してください", "invalid-email"),
                new ErrorResponse.FieldError("name", "名前は必須です", "")
        );

        // Act
        var result = ErrorResponse.validationError(
                "入力内容に誤りがあります（2件）",
                "/api/v1/users",
                fieldErrors);

        // Assert
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).extracting(ErrorResponse.FieldError::field)
                .containsExactly("email", "name");
        assertThat(result.errors()).extracting(ErrorResponse.FieldError::message)
                .containsExactly("有効なメールアドレスを入力してください", "名前は必須です");
        assertThat(result.errors()).extracting(ErrorResponse.FieldError::rejectedValue)
                .containsExactly("invalid-email", "");
    }

    @Test
    @DisplayName("of() を呼び出した場合、指定されたエラーコードとステータスの ErrorResponse を返す")
    void should_createErrorResponseWithCorrectFields_when_ofCalled() {
        // Act
        var result = ErrorResponse.of(
                "business-error",
                "Business Error",
                422,
                "在庫が不足しています",
                "/api/v1/orders");

        // Assert
        assertThat(result.status()).isEqualTo(422);
        assertThat(result.title()).isEqualTo("Business Error");
        assertThat(result.detail()).isEqualTo("在庫が不足しています");
        assertThat(result.type()).contains("business-error");
        assertThat(result.timestamp()).isNotNull();
    }
}
