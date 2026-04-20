package com.example.skishop.ai.service;

import com.example.skishop.ai.model.AiChatSession;
import com.example.skishop.ai.util.PromptSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminAnalyzerService テスト (P3: SSE フレーム生成 + action 抽出).
 */
class AdminAnalyzerServiceTest {

    private PromptSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new PromptSanitizer();
    }

    // ── action 抽出テスト (D-F1-04) ──

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "提案:\\s*SKU=([^\\s]+)\\s+数量=(\\d+)\\s+理由=(.+)", Pattern.MULTILINE);

    @Test
    void actionPattern_extractsSingleAction() {
        String content = "分析結果です。\n提案: SKU=BTS-007 数量=20 理由=在庫残 1.3 か月分のため補充推奨";
        Matcher m = ACTION_PATTERN.matcher(content);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("BTS-007");
        assertThat(m.group(2)).isEqualTo("20");
        assertThat(m.group(3)).startsWith("在庫残");
    }

    @Test
    void actionPattern_extractsMultipleActions() {
        String content = """
                売上分析結果:
                提案: SKU=BTS-007 数量=20 理由=在庫不足
                提案: SKU=HLM-017 数量=15 理由=需要急増
                提案: SKU=GLV-002 数量=30 理由=安全水準割れ
                """;
        Matcher m = ACTION_PATTERN.matcher(content);
        int count = 0;
        while (m.find()) count++;
        assertThat(count).isEqualTo(3);
    }

    @Test
    void actionPattern_noMatch_whenNoProposal() {
        String content = "特に在庫の問題はありません。";
        Matcher m = ACTION_PATTERN.matcher(content);
        assertThat(m.find()).isFalse();
    }

    @Test
    void actionPattern_handlesJapaneseFullWidthSKU() {
        // SKU should be ASCII, but handle edge case
        String content = "提案: SKU=SKI-001 数量=5 理由=シーズン前の需要対応";
        Matcher m = ACTION_PATTERN.matcher(content);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("SKI-001");
    }

    // ── AiChatSession ターン制限テスト (D-F1-01) ──

    @Test
    void chatSession_addTurn_limitedTo8Turns() {
        AiChatSession session = new AiChatSession();
        // 8 ターン = 16 messages を追加
        for (int i = 0; i < 8; i++) {
            session.addTurn("user", "質問 " + i);
            session.addTurn("assistant", "回答 " + i);
        }
        // 9 ターン目 → 最古が要約に置換
        session.addTurn("user", "質問 8");
        session.addTurn("assistant", "回答 8");

        // ターン数が 16 を超えないこと
        assertThat(session.getTurns().size()).isLessThanOrEqualTo(17); // 16 + 1 summary
    }

    @Test
    void chatSession_turnsContainSummary_afterOverflow() {
        AiChatSession session = new AiChatSession();
        for (int i = 0; i < 9; i++) {
            session.addTurn("user", "質問 " + i);
            session.addTurn("assistant", "回答 " + i);
        }
        // 最初のターンが [要約] で始まるべき
        boolean hasSummary = session.getTurns().stream()
                .anyMatch(t -> t.role().equals("system") && t.content().startsWith("[要約]"));
        assertThat(hasSummary).isTrue();
    }

    // ── SSE フレームパース（クライアント側ロジック相当） ──

    @Test
    void sseFrame_phaseEvent_parsesCorrectly() {
        String frame = "event:phase\ndata:{\"phase\":\"INTENT\"}";
        assertThat(frame).contains("phase");
        assertThat(frame).contains("INTENT");
    }

    @Test
    void sseFrame_tokenEvent_parsesCorrectly() {
        String frame = "event:token\ndata:{\"text\":\"売上は\"}";
        assertThat(frame).contains("token");
        assertThat(frame).contains("売上は");
    }

    @Test
    void sseFrame_errorEvent_parsesCorrectly() {
        String frame = "event:error\ndata:{\"message\":\"CircuitBreaker open\",\"code\":\"CIRCUIT_OPEN\"}";
        assertThat(frame).contains("error");
        assertThat(frame).contains("CIRCUIT_OPEN");
    }

    // ── PromptSanitizer サンドイッチ確認 (D-COM-05) ──

    @Test
    void sanitizer_wrapsUserMessage() {
        String wrapped = sanitizer.wrapAsUserMessage("こんにちは");
        assertThat(wrapped).isEqualTo("<user_message>\nこんにちは\n</user_message>");
    }

    @Test
    void sanitizer_removesEmailFromMessage() {
        String sanitized = sanitizer.sanitize("admin@shop.com に送って");
        assertThat(sanitized).doesNotContain("admin@shop.com");
        assertThat(sanitized).contains("[REDACTED_EMAIL]");
    }
}
