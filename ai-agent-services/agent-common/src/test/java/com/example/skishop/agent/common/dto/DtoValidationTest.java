package com.example.skishop.agent.common.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator V = FACTORY.getValidator();

    @Test
    void weatherAgentRequest_default_unit_and_forecastDays() {
        var req = new WeatherAgentRequest("Naeba, Japan", null, null, null, null);
        assertThat(req.unit()).isEqualTo("celsius");
        assertThat(req.forecastDays()).isEqualTo(7);
    }

    @Test
    void weatherAgentRequest_blank_location_violates_NotBlank() {
        var req = new WeatherAgentRequest("", null, null, null, null);
        assertThat(V.validate(req)).hasSize(1);
    }

    @Test
    void customerIntentRequest_blank_userMessage_violates() {
        var req = new CustomerIntentRequest("u1", "", null, "ja");
        assertThat(V.validate(req)).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void customerIntentRequest_too_long_userMessage_violates() {
        var msg = "a".repeat(2001);
        var req = new CustomerIntentRequest("u1", msg, null, "ja");
        assertThat(V.validate(req)).hasSize(1);
    }

    @Test
    void equipmentMatchRequest_default_quantity_and_categories() {
        var req = new EquipmentMatchRequest("u1", "BEGINNER", null, null, null, null, false, true, 0);
        assertThat(req.quantity()).isEqualTo(1);
        assertThat(req.desiredCategories()).isEmpty();
    }

    @Test
    void inventoryCheckRequest_default_required_quantity() {
        var req = new InventoryCheckRequest(java.util.List.of("p1"), 0);
        assertThat(req.requiredQuantity()).isEqualTo(1);
    }

    @Test
    void reservationRequest_default_ttl() {
        var req = new ReservationRequest("o1", "u1",
                java.util.List.of(new ReservationRequest.ReservationItem("p1", 1)), 0);
        assertThat(req.reservationTtlMinutes()).isEqualTo(30);
    }

    @Test
    void pricingRequest_default_tier_and_quantity() {
        var req = new PricingRequest("p1", "u1", null, "Naeba", 0);
        assertThat(req.customerTier()).isEqualTo("BRONZE");
        assertThat(req.quantity()).isEqualTo(1);
    }

    @Test
    void intentCategory_pattern_matches() {
        IntentCategory ic = new IntentCategory.Purchase("ski");
        String result = switch (ic) {
            case IntentCategory.Purchase p -> "purchase:" + p.productCategory();
            case IntentCategory.Rental r -> "rental";
            case IntentCategory.Advice a -> "advice";
            case IntentCategory.Support s -> "support";
        };
        assertThat(result).isEqualTo("purchase:ski");
    }

    @Test
    void skiFeasibilityResult_has_overallCondition() {
        var r = new SkiFeasibilityResult("HIGH", 80, "COLD", "POWDER", "EXCELLENT");
        assertThat(r.overallCondition()).isEqualTo("EXCELLENT");
    }

    // --- 全分岐カバレッジ用: 各 record の if-default 両方を通す ---

    @Test
    void weatherAgentRequest_explicit_unit_and_days_kept() {
        var req = new WeatherAgentRequest("Naeba", "Naeba Ski", "Q?", "fahrenheit", 3);
        assertThat(req.unit()).isEqualTo("fahrenheit");
        assertThat(req.forecastDays()).isEqualTo(3);
    }

    @Test
    void weatherAgentRequest_4arg_constructor() {
        var req = new WeatherAgentRequest("Naeba", "Naeba Ski", "celsius", 5);
        assertThat(req.question()).isNull();
        assertThat(req.forecastDays()).isEqualTo(5);
    }

    @Test
    void equipmentMatchRequest_explicit_values_kept() {
        var req = new EquipmentMatchRequest("u1", "ADVANCED",
                new BodyMeasurements(170, 65, 27, "REGULAR"),
                java.util.List.of("ski", "boots"), 50000, "Naeba",
                false, true, 2);
        assertThat(req.quantity()).isEqualTo(2);
        assertThat(req.desiredCategories()).hasSize(2);
    }

    @Test
    void inventoryCheckRequest_explicit_quantity_kept() {
        var req = new InventoryCheckRequest(java.util.List.of("p1"), 5);
        assertThat(req.requiredQuantity()).isEqualTo(5);
    }

    @Test
    void reservationRequest_explicit_ttl_kept() {
        var req = new ReservationRequest("o1", "u1",
                java.util.List.of(new ReservationRequest.ReservationItem("p1", 1)), 60);
        assertThat(req.reservationTtlMinutes()).isEqualTo(60);
    }

    @Test
    void pricingRequest_explicit_tier_and_quantity_kept() {
        var req = new PricingRequest("p1", "u1", "GOLD", "Naeba", 3);
        assertThat(req.customerTier()).isEqualTo("GOLD");
        assertThat(req.quantity()).isEqualTo(3);
    }

    @Test
    void intentCategory_all_variants() {
        IntentCategory[] cases = {
                new IntentCategory.Purchase("ski"),
                new IntentCategory.Rental("boots", 3),
                new IntentCategory.Advice("how-to-pick-skis"),
                new IntentCategory.Support("billing")
        };
        for (var ic : cases) {
            String result = switch (ic) {
                case IntentCategory.Purchase p -> "purchase";
                case IntentCategory.Rental r -> "rental:" + r.durationDays();
                case IntentCategory.Advice a -> "advice:" + a.topic();
                case IntentCategory.Support s -> "support:" + s.issueType();
            };
            assertThat(result).isNotNull();
        }
    }
}
