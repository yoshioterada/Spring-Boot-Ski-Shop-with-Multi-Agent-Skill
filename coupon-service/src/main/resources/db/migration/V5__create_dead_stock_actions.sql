-- ============================================================
-- AI Analyzer (F4) — Dead-stock coupon issuance audit table
-- Created in: skishop_coupon database (coupon-service)
-- Phase: P0 (basis for F4 coupon issuance audit)
-- ADR: D-F4-04 (30 日以内重複発行を 409 で拒否)、D-F4-05 (AI 提案値と実発行値を両方記録)
-- ============================================================

CREATE TABLE IF NOT EXISTS dead_stock_actions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku               VARCHAR(64) NOT NULL,
    coupon_id         UUID        NOT NULL REFERENCES coupons(id),
    ai_suggested_pct  NUMERIC(4,1) NOT NULL CHECK (ai_suggested_pct BETWEEN 0 AND 100),
    actual_pct        NUMERIC(4,1) NOT NULL,
    approver_user_id  VARCHAR(64) NOT NULL,
    memo              TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pct_range CHECK (actual_pct BETWEEN 0 AND 40)
);

CREATE INDEX IF NOT EXISTS idx_dead_stock_actions_sku_created
    ON dead_stock_actions(sku, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_dead_stock_actions_coupon_id
    ON dead_stock_actions(coupon_id);

COMMENT ON TABLE dead_stock_actions IS 'AI Analyzer F4 滞留在庫クーポン発行の監査ログ';
COMMENT ON COLUMN dead_stock_actions.ai_suggested_pct IS 'AI が提案した割引率（参考、後日精度評価用）';
COMMENT ON COLUMN dead_stock_actions.actual_pct IS '管理者が実際に承認した割引率（必ず 0-40% にクリップ済み）';
