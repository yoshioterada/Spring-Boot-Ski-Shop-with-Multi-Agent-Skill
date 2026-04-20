// ============================================================
// AI Analyzer — Collections + TTL indexes for ai-support-service
// Database: skishop_ai (ai-support-service)
// Phase: P0
// ADR:
//   D-F3-02 (weekly_summaries は 90 日 TTL、ai_analyzer_chat_sessions は 30 日 TTL)
//   D-F5-05 (zero_hit_dismissals は 90 日で自動失効)
// ============================================================

db = db.getSiblingDB("skishop_ai");

// ---- weekly_summaries (F3) ----
if (!db.getCollectionNames().includes("weekly_summaries")) {
    db.createCollection("weekly_summaries");
}
db.weekly_summaries.createIndex(
    { weekStartDate: 1 },
    { name: "uk_week_start", unique: true }
);
db.weekly_summaries.createIndex(
    { ttl: 1 },
    { name: "ttl_weekly_summaries", expireAfterSeconds: 0 }
);

// ---- ai_analyzer_chat_sessions (F1) ----
if (!db.getCollectionNames().includes("ai_analyzer_chat_sessions")) {
    db.createCollection("ai_analyzer_chat_sessions");
}
db.ai_analyzer_chat_sessions.createIndex(
    { userId: 1, createdAt: -1 },
    { name: "idx_chat_user_created" }
);
db.ai_analyzer_chat_sessions.createIndex(
    { ttl: 1 },
    { name: "ttl_chat_sessions", expireAfterSeconds: 0 }
);

// ---- zero_hit_dismissals (F5) ----
if (!db.getCollectionNames().includes("zero_hit_dismissals")) {
    db.createCollection("zero_hit_dismissals");
}
db.zero_hit_dismissals.createIndex(
    { normalizedKeyword: 1 },
    { name: "uk_dismissed_keyword", unique: true }
);
db.zero_hit_dismissals.createIndex(
    { expiresAt: 1 },
    { name: "ttl_dismissals", expireAfterSeconds: 0 }
);

// ---- llm_audit_logs (D-COM-08, prompt 原文を保存しない) ----
if (!db.getCollectionNames().includes("llm_audit_logs")) {
    db.createCollection("llm_audit_logs");
}
db.llm_audit_logs.createIndex(
    { createdAt: -1 },
    { name: "idx_audit_created" }
);
db.llm_audit_logs.createIndex(
    { traceId: 1 },
    { name: "idx_audit_trace" }
);
db.llm_audit_logs.createIndex(
    { ttl: 1 },
    { name: "ttl_audit_logs", expireAfterSeconds: 0 }
);

print("[AI Analyzer P0] AI Analyzer collections + TTL indexes created on skishop_ai");
