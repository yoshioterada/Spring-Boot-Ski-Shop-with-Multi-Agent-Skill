package com.example.skishop.agent.intent.tool;

import com.example.skishop.agent.common.dto.ExtractedConstraints;
import com.example.skishop.agent.common.dto.UserPurchaseHistory;
import com.example.skishop.agent.intent.client.UserProfileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerIntentToolServiceTest {

    private UserProfileClient userProfileClient;
    private CustomerIntentToolService tool;

    @BeforeEach
    void setUp() {
        userProfileClient = mock(UserProfileClient.class);
        tool = new CustomerIntentToolService(userProfileClient);
    }

    @Test
    void classifyIntent_returns_message_unchanged() {
        assertThat(tool.classifyIntent("予算10万円")).isEqualTo("予算10万円");
    }

    @Test
    void classifyIntent_handles_null_message() {
        assertThat(tool.classifyIntent(null)).isEmpty();
    }

    @Test
    void getUserPurchaseHistory_delegates_to_client_for_valid_user() {
        var history = new UserPurchaseHistory("u1", List.of("ski"), "INTERMEDIATE", 3, "SILVER");
        when(userProfileClient.getPurchaseHistory("u1")).thenReturn(history);
        assertThat(tool.getUserPurchaseHistory("u1")).isSameAs(history);
    }

    @Test
    void getUserPurchaseHistory_returns_fallback_for_blank_userId() {
        UserPurchaseHistory result = tool.getUserPurchaseHistory("");
        assertThat(result.userId()).isEqualTo("anonymous");
        assertThat(result.customerTier()).isEqualTo("BRONZE");
        verify(userProfileClient, never()).getPurchaseHistory("");
    }

    @Test
    void getUserPurchaseHistory_returns_fallback_for_null_userId() {
        UserPurchaseHistory result = tool.getUserPurchaseHistory(null);
        assertThat(result.userId()).isEqualTo("anonymous");
        verify(userProfileClient, never()).getPurchaseHistory(null);
    }

    @Test
    void validate_uses_defaults_when_all_inputs_null() {
        ExtractedConstraints c = tool.validateAndFillConstraints(null, null, null, null, null, null, null);
        assertThat(c.skillLevel()).isEqualTo("BEGINNER");
        assertThat(c.groupSize()).isEqualTo(1);
        assertThat(c.tripStartDate()).isNull();
        assertThat(c.tripEndDate()).isNull();
        assertThat(c.budgetYen()).isNull();
        assertThat(c.productCategories()).isEmpty();
        assertThat(c.includesRental()).isTrue();
        assertThat(c.includesPurchase()).isFalse();
    }

    @Test
    void validate_parses_categories_and_dates() {
        ExtractedConstraints c = tool.validateAndFillConstraints(
                "苗場", "2026-12-27", "2026-12-29", 4, "advanced", 100000, "スキー板, ウェア,ブーツ");
        assertThat(c.destination()).isEqualTo("苗場");
        assertThat(c.tripStartDate()).isEqualTo(LocalDate.of(2026, 12, 27));
        assertThat(c.tripEndDate()).isEqualTo(LocalDate.of(2026, 12, 29));
        assertThat(c.skillLevel()).isEqualTo("ADVANCED");
        assertThat(c.groupSize()).isEqualTo(4);
        assertThat(c.budgetYen()).isEqualTo(100000);
        assertThat(c.productCategories()).containsExactly("スキー板", "ウェア", "ブーツ");
        assertThat(c.includesPurchase()).isTrue();
        assertThat(c.includesRental()).isFalse();
    }

    @Test
    void validate_handles_invalid_groupSize_and_blank_skill() {
        ExtractedConstraints c = tool.validateAndFillConstraints(
                null, null, null, 0, "  ", null, "");
        assertThat(c.groupSize()).isEqualTo(1);
        assertThat(c.skillLevel()).isEqualTo("BEGINNER");
        assertThat(c.productCategories()).isEmpty();
    }

    @Test
    void validate_handles_invalid_date_string() {
        ExtractedConstraints c = tool.validateAndFillConstraints(
                "苗場", "not-a-date", "", null, null, null, null);
        assertThat(c.tripStartDate()).isNull();
        assertThat(c.tripEndDate()).isNull();
    }
}
