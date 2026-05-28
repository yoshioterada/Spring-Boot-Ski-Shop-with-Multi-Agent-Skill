package com.example.skishop.inventory.service;

import com.example.skishop.inventory.model.SearchLog;
import com.example.skishop.inventory.repository.SearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchAnalyticsServiceTest {

    @Mock
    private SearchLogRepository searchLogRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    private SearchAnalyticsService searchAnalyticsService;

    @BeforeEach
    void setUp() {
        searchAnalyticsService = new SearchAnalyticsService(searchLogRepository, mongoTemplate);
    }

    @Test
    @DisplayName("ゼロヒット検索ログに拡張クエリと検索元が保存される")
    void should_saveZeroHitSearchLogWithSource_when_aiSupportSearchHasNoResults() {
        // Arrange
        when(searchLogRepository.save(any(SearchLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        searchAnalyticsService.logSearchAsync(" Powder Ski ", "Powder Ski deep snow", "ai-support-semantic", 0, -10);

        // Assert
        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogRepository).save(captor.capture());
        SearchLog saved = captor.getValue();
        assertThat(saved.getKeyword()).isEqualTo("powder ski");
        assertThat(saved.getEnhancedKeyword()).isEqualTo("powder ski deep snow");
        assertThat(saved.getSource()).isEqualTo("ai-support-semantic");
        assertThat(saved.getHitCount()).isZero();
        assertThat(saved.getDurationMs()).isZero();
    }
}