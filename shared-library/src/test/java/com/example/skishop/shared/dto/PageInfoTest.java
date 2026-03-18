package com.example.skishop.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageInfo テスト")
class PageInfoTest {

    @Test
    @DisplayName("PageInfo.of() を呼び出した場合、正しい totalPages を計算する")
    void should_calculateTotalPages_when_ofCalled() {
        // Act
        var pageInfo = PageInfo.of(0, 10, 25);

        // Assert
        assertThat(pageInfo.page()).isEqualTo(0);
        assertThat(pageInfo.size()).isEqualTo(10);
        assertThat(pageInfo.totalElements()).isEqualTo(25);
        assertThat(pageInfo.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("totalElements がゼロの場合、totalPages は 0 を返す")
    void should_returnZeroTotalPages_when_totalElementsIsZero() {
        // Act
        var pageInfo = PageInfo.of(0, 10, 0);

        // Assert
        assertThat(pageInfo.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("size がゼロの場合、totalPages は 0 を返す")
    void should_returnZeroTotalPages_when_sizeIsZero() {
        // Act
        var pageInfo = PageInfo.of(0, 0, 100);

        // Assert
        assertThat(pageInfo.totalPages()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 3, true, false, false, true",
        "1, 3, false, false, true, true",
        "2, 3, false, true, true, false"
    })
    @DisplayName("ページ位置に応じた isFirst / isLast / hasPrevious / hasNext が正しい")
    void should_returnCorrectPagePosition_when_pagePositionVaries(
            int page, int totalPages,
            boolean expectedIsFirst, boolean expectedIsLast,
            boolean expectedHasPrevious, boolean expectedHasNext) {
        // Arrange
        int size = 10;
        long totalElements = (long) totalPages * size;
        var pageInfo = PageInfo.of(page, size, totalElements);

        // Assert
        assertThat(pageInfo.isFirst()).isEqualTo(expectedIsFirst);
        assertThat(pageInfo.isLast()).isEqualTo(expectedIsLast);
        assertThat(pageInfo.hasPrevious()).isEqualTo(expectedHasPrevious);
        assertThat(pageInfo.hasNext()).isEqualTo(expectedHasNext);
    }

    @Test
    @DisplayName("totalElements がサイズで割り切れる場合、totalPages が正しく計算される")
    void should_calculateExactTotalPages_when_totalElementsDivisibleBySize() {
        // Act
        var pageInfo = PageInfo.of(0, 10, 30);

        // Assert
        assertThat(pageInfo.totalPages()).isEqualTo(3);
    }
}
