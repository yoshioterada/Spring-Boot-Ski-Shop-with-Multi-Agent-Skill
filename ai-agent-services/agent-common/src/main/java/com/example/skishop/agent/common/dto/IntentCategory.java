package com.example.skishop.agent.common.dto;

/**
 * 顧客インテントの大分類。Sealed interface でパターンマッチングを安全に利用する。
 */
public sealed interface IntentCategory
        permits IntentCategory.Purchase, IntentCategory.Rental,
                IntentCategory.Advice, IntentCategory.Support {

    record Purchase(String productCategory) implements IntentCategory {}
    record Rental(String productCategory, int durationDays) implements IntentCategory {}
    record Advice(String topic) implements IntentCategory {}
    record Support(String issueType) implements IntentCategory {}
}
