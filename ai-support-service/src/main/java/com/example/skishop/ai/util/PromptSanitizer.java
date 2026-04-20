package com.example.skishop.ai.util;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * LLM プロンプトに渡すテキストから個人情報（PII）を除去するユーティリティ.
 * <p>
 * spec § 22 D-COM-02 / D-F5-07 の具体実装.
 * <ul>
 *   <li>メールアドレスを {@code [REDACTED_EMAIL]} に置換</li>
 *   <li>電話番号（日本形式）を {@code [REDACTED_PHONE]} に置換</li>
 *   <li>郵便番号 {@code 〒xxx-xxxx} を {@code [REDACTED_ZIP]} に置換</li>
 *   <li>UUID v4（user_id 等）を {@code [REDACTED_ID]} に置換</li>
 *   <li>数字 16 桁以上の連続（クレジットカード番号など）を {@code [REDACTED_CARD]} に置換</li>
 * </ul>
 *
 * <p>D-COM-08: 監査ログにはサニタイズ後テキストの SHA-256 ハッシュのみを保存し、原文は保存しない.
 */
@Component
public class PromptSanitizer {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern PHONE_JP = Pattern.compile(
            "\\b0\\d{1,4}-?\\d{1,4}-?\\d{4}\\b");
    private static final Pattern ZIP_JP = Pattern.compile(
            "〒?\\d{3}-?\\d{4}");
    private static final Pattern LONG_DIGITS = Pattern.compile(
            "\\b\\d{16,19}\\b");

    /**
     * 入力文字列から PII を除去する. null は空文字を返す.
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input;
        result = LONG_DIGITS.matcher(result).replaceAll("[REDACTED_CARD]");
        result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = UUID.matcher(result).replaceAll("[REDACTED_ID]");
        result = PHONE_JP.matcher(result).replaceAll("[REDACTED_PHONE]");
        result = ZIP_JP.matcher(result).replaceAll("[REDACTED_ZIP]");
        return result;
    }

    /**
     * プロンプトインジェクション対策のサンドイッチタグで包む（spec D-COM-05）.
     */
    public String wrapAsUserMessage(String sanitizedInput) {
        return "<user_message>\n" + sanitizedInput + "\n</user_message>";
    }

    /**
     * 監査ログ用に SHA-256 ハッシュ（hex）を返す（D-COM-08）.
     */
    public String hash(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
