// ============================================================
// AI Analyzer (F5) — Zero-hit aggregation index for search_logs
// Database: skishop_inventory (inventory-management-service)
// Phase: P0
// ADR: D-F5-04 (search_count >= 3 集計の高速化)、§ 20.10.1
//
// 既存の search_logs コレクション（SearchLog.java）に対し
// ゼロヒット集計を高速化する partial filter index を追加する。
// ============================================================

db = db.getSiblingDB("skishop_inventory");

db.search_logs.createIndex(
    { hitCount: 1, createdAt: -1 },
    {
        name: "idx_zero_hit_recent",
        partialFilterExpression: { hitCount: 0 }
    }
);

print("[AI Analyzer P0] idx_zero_hit_recent index created on skishop_inventory.search_logs");
