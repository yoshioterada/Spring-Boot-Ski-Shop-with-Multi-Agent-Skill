package com.example.skishop.shared.exception;

import com.example.skishop.shared.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler テスト")
class GlobalExceptionHandlerTest {

    /** テスト用 DTO: パスワードフィールドを持つ */
    record LoginRequest(String username, String password) {}

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    @DisplayName("ResourceNotFoundException が発生した場合、404 ステータスの ErrorResponse を返す")
    void should_return404ErrorResponse_when_resourceNotFoundExceptionThrown() {
        // Arrange
        var exception = new ResourceNotFoundException("User", 99L);

        // Act
        var response = handler.handleResourceNotFound(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().detail()).contains("User");
        assertThat(response.getBody().detail()).contains("99");
        assertThat(response.getBody().instance()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("BusinessException が発生した場合、422 ステータスの ErrorResponse を返す")
    void should_return422ErrorResponse_when_businessExceptionThrown() {
        // Arrange
        var exception = new BusinessException("STOCK_INSUFFICIENT", "在庫が不足しています");

        // Act
        var response = handler.handleBusinessException(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().detail()).isEqualTo("在庫が不足しています");
        assertThat(response.getBody().instance()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("BusinessException にカスタム HTTP ステータスがある場合、そのステータスを返す")
    void should_returnCustomStatus_when_businessExceptionHasCustomStatus() {
        // Arrange
        var exception = new BusinessException("CONFLICT", "重複するリクエスト", HttpStatus.CONFLICT);

        // Act
        var response = handler.handleBusinessException(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    @DisplayName("予期しない Exception が発生した場合、500 ステータスの ErrorResponse を返す (内部情報は非公開)")
    void should_return500WithoutInternalDetails_when_unexpectedExceptionThrown() {
        // Arrange
        var exception = new RuntimeException("内部エラー詳細 (クライアントに見せてはいけない)");

        // Act
        var response = handler.handleUnexpectedException(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        // 内部情報がクライアントに漏洩していないことを確認
        assertThat(response.getBody().detail()).doesNotContain("内部エラー詳細");
        assertThat(response.getBody().instance()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("ResourceNotFoundException のメッセージに resourceType と id が含まれる")
    void should_containResourceTypeAndId_when_resourceNotFoundExceptionCreated() {
        // Act
        var exception = new ResourceNotFoundException("Order", "ORD-001");

        // Assert
        assertThat(exception.getMessage()).contains("Order");
        assertThat(exception.getMessage()).contains("ORD-001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getErrorCode()).isEqualTo("not-found");
    }

    @Test
    @DisplayName("バリデーションエラーのハンドリング時、パスワードフィールドの拒否値はマスクされる")
    void should_maskPasswordField_when_validationErrorOccursOnSensitiveField() {
        // Arrange
        var loginRequest = new LoginRequest("user@example.com", "secret123");
        var bindingResult = new BeanPropertyBindingResult(loginRequest, "loginRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "loginRequest", "password", "secret123", false, null, null, "パスワードは必須です"));

        var ex = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        var response = handler.handleValidationErrors(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        var passwordErrors = response.getBody().errors().stream()
                .filter(e -> "password".equals(e.field()))
                .toList();
        assertThat(passwordErrors).hasSize(1);
        assertThat(passwordErrors.get(0).rejectedValue()).isEqualTo("***");
    }
}
