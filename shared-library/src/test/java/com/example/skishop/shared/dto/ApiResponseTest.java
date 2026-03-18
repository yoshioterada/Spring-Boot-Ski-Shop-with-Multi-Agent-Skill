package com.example.skishop.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse テスト")
class ApiResponseTest {

    @Test
    @DisplayName("success(data) を呼び出した場合、ステータス 200 と SUCCESS コードを返す")
    void should_returnStatus200_when_successWithData() {
        // Arrange
        var data = "test-data";

        // Act
        var result = ApiResponse.success(data);

        // Assert
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.data()).isEqualTo("test-data");
        assertThat(result.meta()).isNull();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("success(data, meta) を呼び出した場合、PageInfo メタ情報を含む")
    void should_includePageInfo_when_successWithMeta() {
        // Arrange
        var data = List.of("item1", "item2");
        var meta = PageInfo.of(0, 10, 2);

        // Act
        var result = ApiResponse.success(data, meta);

        // Assert
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).containsExactly("item1", "item2");
        assertThat(result.meta()).isNotNull();
        assertThat(result.meta().totalElements()).isEqualTo(2);
        assertThat(result.meta().totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("success(message, data) を呼び出した場合、カスタムメッセージを返す")
    void should_returnCustomMessage_when_successWithMessage() {
        // Arrange
        var customMessage = "カスタムメッセージ";
        var data = "data";

        // Act
        var result = ApiResponse.success(customMessage, data);

        // Assert
        assertThat(result.message()).isEqualTo("カスタムメッセージ");
        assertThat(result.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("created(data) を呼び出した場合、ステータス 201 と CREATED コードを返す")
    void should_returnStatus201_when_created() {
        // Arrange
        var data = "new-resource";

        // Act
        var result = ApiResponse.created(data);

        // Assert
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.code()).isEqualTo("CREATED");
        assertThat(result.data()).isEqualTo("new-resource");
    }

    @Test
    @DisplayName("noContent() を呼び出した場合、ステータス 204 とデータ null を返す")
    void should_returnStatus204WithNullData_when_noContent() {
        // Act
        ApiResponse<?> result = ApiResponse.noContent();

        // Assert
        assertThat(result.status()).isEqualTo(204);
        assertThat(result.code()).isEqualTo("NO_CONTENT");
        assertThat(result.data()).isNull();
    }
}
