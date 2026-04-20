-- ============================================================
-- AI Analyzer (F2) — Seasonal weights master table
-- Created in: skishop_sales database (sales-management-service)
-- Phase: P0 (basis for F2 seasonal forecast)
-- ADR: D-F2-02 (季節係数はマスタ化、コードへハードコード禁止)
-- ============================================================

CREATE TABLE IF NOT EXISTS seasonal_weights (
    category_id  VARCHAR(64) NOT NULL,
    month        SMALLINT    NOT NULL CHECK (month BETWEEN 1 AND 12),
    weight       NUMERIC(5,3) NOT NULL CHECK (weight >= 0),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id, month)
);

COMMENT ON TABLE seasonal_weights IS 'AI Analyzer F2 季節予測の月次重み（カテゴリ×月）';
COMMENT ON COLUMN seasonal_weights.weight IS '1.0=平均、>1.0=ピーク月、<1.0=オフ月';

-- ============================================================
-- Seed: 7 categories × 12 months = 84 rows
-- 値は冬商戦（10〜2 月にピーク）を想定した初期値。業務担当が後で調整可能。
-- ============================================================
INSERT INTO seasonal_weights (category_id, month, weight) VALUES
  -- cat-ski: スキー本体（早期受注 5 月、ピーク 12 月）
  ('cat-ski',     1, 1.4), ('cat-ski',     2, 1.2), ('cat-ski',     3, 0.8), ('cat-ski',     4, 0.4),
  ('cat-ski',     5, 1.8), ('cat-ski',     6, 0.6), ('cat-ski',     7, 0.3), ('cat-ski',     8, 0.2),
  ('cat-ski',     9, 0.7), ('cat-ski',    10, 1.5), ('cat-ski',    11, 1.7), ('cat-ski',    12, 2.4),
  -- cat-boots: ブーツ
  ('cat-boots',   1, 1.5), ('cat-boots',   2, 1.3), ('cat-boots',   3, 0.7), ('cat-boots',   4, 0.4),
  ('cat-boots',   5, 1.2), ('cat-boots',   6, 0.5), ('cat-boots',   7, 0.3), ('cat-boots',   8, 0.3),
  ('cat-boots',   9, 0.9), ('cat-boots',  10, 1.6), ('cat-boots',  11, 1.8), ('cat-boots',  12, 2.5),
  -- cat-wear: ウェア
  ('cat-wear',    1, 1.6), ('cat-wear',    2, 1.4), ('cat-wear',    3, 0.6), ('cat-wear',    4, 0.3),
  ('cat-wear',    5, 0.4), ('cat-wear',    6, 0.3), ('cat-wear',    7, 0.3), ('cat-wear',    8, 0.4),
  ('cat-wear',    9, 1.1), ('cat-wear',   10, 1.8), ('cat-wear',   11, 2.0), ('cat-wear',   12, 1.8),
  -- cat-gloves: グローブ
  ('cat-gloves',  1, 1.5), ('cat-gloves',  2, 1.3), ('cat-gloves',  3, 0.7), ('cat-gloves',  4, 0.4),
  ('cat-gloves',  5, 0.5), ('cat-gloves',  6, 0.4), ('cat-gloves',  7, 0.3), ('cat-gloves',  8, 0.4),
  ('cat-gloves',  9, 1.0), ('cat-gloves', 10, 1.6), ('cat-gloves', 11, 1.9), ('cat-gloves', 12, 2.0),
  -- cat-goggles: ゴーグル
  ('cat-goggles', 1, 1.4), ('cat-goggles', 2, 1.2), ('cat-goggles', 3, 0.7), ('cat-goggles', 4, 0.4),
  ('cat-goggles', 5, 0.5), ('cat-goggles', 6, 0.4), ('cat-goggles', 7, 0.3), ('cat-goggles', 8, 0.5),
  ('cat-goggles', 9, 1.1), ('cat-goggles',10, 1.7), ('cat-goggles',11, 1.9), ('cat-goggles',12, 1.9),
  -- cat-helmets: ヘルメット
  ('cat-helmets', 1, 1.4), ('cat-helmets', 2, 1.2), ('cat-helmets', 3, 0.7), ('cat-helmets', 4, 0.5),
  ('cat-helmets', 5, 0.6), ('cat-helmets', 6, 0.4), ('cat-helmets', 7, 0.4), ('cat-helmets', 8, 0.5),
  ('cat-helmets', 9, 1.0), ('cat-helmets',10, 1.6), ('cat-helmets',11, 1.8), ('cat-helmets',12, 2.0),
  -- cat-poles: ポール
  ('cat-poles',   1, 1.3), ('cat-poles',   2, 1.1), ('cat-poles',   3, 0.7), ('cat-poles',   4, 0.4),
  ('cat-poles',   5, 1.0), ('cat-poles',   6, 0.5), ('cat-poles',   7, 0.3), ('cat-poles',   8, 0.3),
  ('cat-poles',   9, 0.8), ('cat-poles',  10, 1.5), ('cat-poles',  11, 1.7), ('cat-poles',  12, 2.0)
ON CONFLICT (category_id, month) DO NOTHING;
