package com.example.skishop.ai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptSanitizer の単体テスト. P0 完了チェック P0-9 に対応.
 */
class PromptSanitizerTest {

    private final PromptSanitizer sanitizer = new PromptSanitizer();

    @Test
    void sanitize_redactsEmail() {
        String result = sanitizer.sanitize("お問い合わせは user@example.com まで");
        assertThat(result).contains("[REDACTED_EMAIL]");
        assertThat(result).doesNotContain("user@example.com");
    }

    @Test
    void sanitize_redactsUuid() {
        String result = sanitizer.sanitize("userId=550e8400-e29b-41d4-a716-446655440000 の注文");
        assertThat(result).contains("[REDACTED_ID]");
        assertThat(result).doesNotContain("550e8400");
    }

    @Test
    void sanitize_redactsJapanesePhone() {
        String result = sanitizer.sanitize("電話: 03-1234-5678");
        assertThat(result).contains("[REDACTED_PHONE]");
    }

    @Test
    void sanitize_redactsZip() {
        String result = sanitizer.sanitize("配送先 〒150-0001");
        assertThat(result).contains("[REDACTED_ZIP]");
    }

    @Test
    void sanitize_redactsLongDigits() {
        String result = sanitizer.sanitize("カード 4111111111111111");
        assertThat(result).contains("[REDACTED_CARD]");
    }

    @Test
    void sanitize_handlesMultipleAtOnce() {
        String input = "user@example.com / 03-1234-5678 / 550e8400-e29b-41d4-a716-446655440000";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("[REDACTED_EMAIL]");
        assertThat(result).contains("[REDACTED_PHONE]");
        assertThat(result).contains("[REDACTED_ID]");
    }

    @Test
    void sanitize_handlesNullAndEmpty() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void sanitize_preservesNormalText() {
        String input = "過去 30 日のスキーブーツの売上を分析して";
        assertThat(sanitizer.sanitize(input)).isEqualTo(input);
    }

    @Test
    void wrapAsUserMessage_addsSandwichTags() {
        String wrapped = sanitizer.wrapAsUserMessage("hello");
        assertThat(wrapped).startsWith("<user_message>");
        assertThat(wrapped).endsWith("</user_message>");
        assertThat(wrapped).contains("hello");
    }

    @Test
    void hash_isDeterministic() {
        assertThat(sanitizer.hash("hello")).isEqualTo(sanitizer.hash("hello"));
        assertThat(sanitizer.hash("hello")).hasSize(64); // SHA-256 hex
        assertThat(sanitizer.hash("hello")).isNotEqualTo(sanitizer.hash("world"));
    }

    @Test
    void hash_handlesNull() {
        assertThat(sanitizer.hash(null)).isEmpty();
    }
}
