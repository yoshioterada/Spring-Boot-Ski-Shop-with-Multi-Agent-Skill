-- ============================================================
-- AI Analyzer (F4) — SKU velocity materialized view for dead-stock detection
-- Created in: skishop_sales database (sales-management-service)
-- Phase: P0 (basis for F4 dead-stock radar)
-- ADR: D-F4-06 (mv_sku_velocity から取得、リアルタイム集計禁止)
--
-- order_items の SKU カラム名は product_sku、
-- ステータス enum: PENDING/CONFIRMED/PROCESSING/SHIPPED/DELIVERED/CANCELLED/RETURNED
-- ============================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_sku_velocity AS
SELECT
    oi.product_sku                                                         AS sku,
    COUNT(*)             FILTER (WHERE o.created_at >= now() - interval '30 days') AS sales_30,
    COUNT(*)             FILTER (WHERE o.created_at >= now() - interval '90 days') AS sales_90,
    COALESCE(SUM(oi.quantity) FILTER (WHERE o.created_at >= now() - interval '30 days'), 0) AS units_30,
    COALESCE(SUM(oi.quantity) FILTER (WHERE o.created_at >= now() - interval '90 days'), 0) AS units_90,
    MAX(o.created_at)                                                      AS last_sold_at,
    AVG(oi.unit_price)                                                     AS avg_price
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status NOT IN ('CANCELLED', 'RETURNED')
  AND oi.product_sku IS NOT NULL
GROUP BY oi.product_sku;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_sku_velocity_sku ON mv_sku_velocity(sku);

COMMENT ON MATERIALIZED VIEW mv_sku_velocity IS 'AI Analyzer F4 滞留在庫判定用 SKU 別販売速度（CONCURRENTLY REFRESH 1h）';
