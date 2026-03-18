package com.example.skishop.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Test
    @DisplayName("ResourceNotFoundException の場合、HTTP 404 ProblemDetail を返す")
    void should_return404_when_resourceNotFoundExceptionThrown() {
        // Arrange
        var ex = new ResourceNotFoundException("Product", 1L);

        // Act
        var result = handler.handleResourceNotFound(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getDetail()).contains("Product");
        assertThat(result.getTitle()).isEqualTo("Resource Not Found");
    }

    @Test
    @DisplayName("BusinessException の場合、HTTP 422 ProblemDetail を返す")
    void should_return422_when_businessExceptionThrown() {
        // Arrange
        var ex = new BusinessException("STOCK_INSUFFICIENT", "在庫が不足しています");

        // Act
        var result = handler.handleBusinessException(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(result.getDetail()).isEqualTo("在庫が不足しています");
        assertThat(result.getTitle()).isEqualTo("Business Rule Violation");
        assertThat(result.getProperties()).containsKey("errorCode");
    }

    @Test
    @DisplayName("ConflictException の場合、HTTP 409 ProblemDetail を返す")
    void should_return409_when_conflictExceptionThrown() {
        // Arrange
        var ex = new ConflictException("DUPLICATE_SKU", "SKU が重複しています");

        // Act
        var result = handler.handleConflictException(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getDetail()).isEqualTo("SKU が重複しています");
        assertThat(result.getTitle()).isEqualTo("Resource Conflict");
    }

    @Test
    @DisplayName("楽観的ロック競合の場合、HTTP 409 ProblemDetail を返す")
    void should_return409_when_optimisticLockingFailureThrown() {
        // Arrange
        var ex = mock(ObjectOptimisticLockingFailureException.class);
        when(ex.getMessage()).thenReturn("Optimistic locking failed");

        // Act
        var result = handler.handleOptimisticLocking(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getTitle()).isEqualTo("Optimistic Lock Conflict");
    }

    @Test
    @DisplayName("バリデーションエラーの場合、HTTP 400 ProblemDetail を返す")
    void should_return400_when_validationErrorOccurs() {
        // Arrange
        var bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("request", "name", "名前は必須です");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        var ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        // Act
        var result = handler.handleValidationErrors(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Validation Failed");
        assertThat(result.getProperties()).containsKey("errors");
    }

    @Test
    @DisplayName("予期しない例外の場合、HTTP 500 ProblemDetail を返す")
    void should_return500_when_unexpectedExceptionThrown() {
        // Arrange
        var ex = new RuntimeException("予期しないエラー");

        // Act
        var result = handler.handleGenericException(ex, request);

        // Assert
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getTitle()).isEqualTo("Internal Server Error");
        assertThat(result.getDetail()).isEqualTo("サーバーでエラーが発生しました。");
    }
}
