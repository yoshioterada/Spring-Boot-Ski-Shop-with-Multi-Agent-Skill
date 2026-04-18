package com.example.skishop.agent.common.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 顧客インテントの大分類。Sealed interface でパターンマッチングを安全に利用する。
 *
 * <p>JSON 表現は {@code type} プロパティを判別子として使う:
 * <pre>
 *   {"type":"PURCHASE","productCategory":"スキー板"}
 *   {"type":"RENTAL","productCategory":"スキー板","durationDays":3}
 *   {"type":"ADVICE","topic":"装備選び"}
 *   {"type":"SUPPORT","issueType":"返品"}
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IntentCategory.Purchase.class, name = "PURCHASE"),
        @JsonSubTypes.Type(value = IntentCategory.Rental.class, name = "RENTAL"),
        @JsonSubTypes.Type(value = IntentCategory.Advice.class, name = "ADVICE"),
        @JsonSubTypes.Type(value = IntentCategory.Support.class, name = "SUPPORT")
})
public sealed interface IntentCategory
        permits IntentCategory.Purchase, IntentCategory.Rental,
                IntentCategory.Advice, IntentCategory.Support {

    record Purchase(String productCategory) implements IntentCategory {}
    record Rental(String productCategory, int durationDays) implements IntentCategory {}
    record Advice(String topic) implements IntentCategory {}
    record Support(String issueType) implements IntentCategory {}
}
