package com.example.skishop.ai.dto;

/**
 * SSE イベントペイロード (spec § 4.2.1).
 * event: phase / token / action / error
 */
public final class SsePayloads {

    private SsePayloads() {}

    /** event: phase */
    public record PhaseEvent(String phase, String tool) {
        public PhaseEvent(String phase) { this(phase, null); }
    }

    /** event: token */
    public record TokenEvent(String text) {}

    /** event: action — 提案行の構造化抽出 (D-F1-04) */
    public record ActionEvent(String type, String sku, String reason, String severity) {}

    /** event: phase COMPLETED */
    public record CompletedEvent(String phase, int tokens, double costUsd) {
        public CompletedEvent(int tokens, double costUsd) { this("COMPLETED", tokens, costUsd); }
    }

    /** event: error */
    public record ErrorEvent(String message, String code) {}
}
