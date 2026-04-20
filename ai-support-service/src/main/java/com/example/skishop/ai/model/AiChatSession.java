package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI チャットセッション永続化 (spec § 4.2.1 / D-F1-01: 8 ターン制限).
 * 30 日 TTL (D-F3-02).
 */
@Document(collection = "ai_analyzer_chat_sessions")
public class AiChatSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private List<Turn> turns = new ArrayList<>();

    @Indexed(expireAfter = "0s")
    private Instant ttl;

    private Instant createdAt;
    private Instant updatedAt;

    public AiChatSession() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.ttl = now.plus(30, java.time.temporal.ChronoUnit.DAYS);
    }

    /** D-F1-01: 直近 8 ターンに制限 */
    public void addTurn(String role, String content) {
        turns.add(new Turn(role, content, Instant.now()));
        if (turns.size() > 16) { // 8 user + 8 assistant = 16 messages
            // 最古の 2 メッセージ (1 ターン) を要約テキストに置換
            String summary = "[要約] " + turns.get(0).content().substring(0, Math.min(100, turns.get(0).content().length())) + "...";
            turns.subList(0, 2).clear();
            turns.addFirst(new Turn("system", summary, Instant.now()));
        }
        this.updatedAt = Instant.now();
    }

    public List<Turn> getRecentTurns() {
        return List.copyOf(turns);
    }

    public record Turn(String role, String content, Instant timestamp) {}

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<Turn> getTurns() { return turns; }
    public void setTurns(List<Turn> turns) { this.turns = turns; }

    public Instant getTtl() { return ttl; }
    public void setTtl(Instant ttl) { this.ttl = ttl; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
