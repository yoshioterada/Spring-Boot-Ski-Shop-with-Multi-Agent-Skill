-- =============================================================================
-- Seed: Dashboard 季節性サンプルデータ
--   日本のスキー業界の購買傾向に合わせた 730 日（過去 2 年）分の注文を生成。
--
--     | 時期      | 内容                          | 注文ボリューム | 想定単価帯 |
--     |-----------|-------------------------------|---------------|-----------|
--     | 3月       | 来季モデル発表 / 早期受注開始 | 中            | 高        |
--     | 4月       | 早期受注本格化                | 大            | 高        |
--     | 5月       | 早期受注締切ピーク            | 最大           | 最高       |
--     | 6月       | 早期受注後半                  | 大            | 高        |
--     | 7月       | 通常オーダー（割引率ダウン）  | 小            | 中        |
--     | 8月       | オフシーズン                  | 最小           | 中        |
--     | 9月       | 商品入荷開始                  | 中            | 中〜高    |
--     | 10月      | 入荷ピーク                    | 中            | 中〜高    |
--     | 11月      | プレシーズン                  | 大            | 中        |
--     | 12月      | 店頭販売（初心者向けピーク）  | 最大           | 中〜低    |
--     | 1月       | 初心者購入ピーク              | 最大           | 中〜低    |
--     | 2月       | 初心者後半                    | 大            | 中〜低    |
--
--   実行は 03_seed_dashboard.sql の後で。再実行可能（履歴注文は ORD-H-* で識別して全削除→再生成）。
-- =============================================================================

\c skishop_sales

BEGIN;

-- 商品カタログ + 価格帯（tier）
CREATE TEMP TABLE _catalog_seasonal (
    sku   text PRIMARY KEY,
    name  text,
    price numeric,
    tier  text  -- 'premium' | 'mid' | 'entry'
) ON COMMIT DROP;

INSERT INTO _catalog_seasonal VALUES
  -- premium (>= 120,000) : 上級者・玄人向け
  ('SKI-001','ATOMIC Redster S9i', 165000, 'premium'),
  ('SKI-002','ATOMIC Redster X9i', 155000, 'premium'),
  ('SKI-004','SALOMON S/Race FIS GS', 178000, 'premium'),
  ('SKI-007','HEAD Supershape e-Speed', 148000, 'premium'),
  ('SKI-008','HEAD World Cup Rebels e-GS', 168000, 'premium'),
  ('SKI-010','ROSSIGNOL Hero Elite ST Ti', 135000, 'premium'),
  ('SKI-013','VOLKL Racetiger SL', 145000, 'premium'),
  ('SKI-014','VOLKL Deacon 76', 128000, 'premium'),
  ('SKI-019','FISCHER RC4 The Curv GT 80', 140000, 'premium'),
  ('SKI-021','NORDICA Dobermann SLR', 138000, 'premium'),
  ('SKI-028','OGASAKA TC-SS', 132000, 'premium'),
  ('BTS-007','HEAD Raptor 140 RS', 105000, 'premium'),
  ('BTS-010','ROSSIGNOL Hero World Cup ZJ+', 110000, 'premium'),
  -- mid (70,000〜120,000) : 中級者
  ('SKI-003','ATOMIC Bent Chetler 100', 110000, 'mid'),
  ('SKI-005','SALOMON QST 98', 98000, 'mid'),
  ('SKI-006','SALOMON Stance 96', 89000, 'mid'),
  ('SKI-009','HEAD Kore 93', 105000, 'mid'),
  ('SKI-011','ROSSIGNOL Experience 82 Ti', 88000, 'mid'),
  ('SKI-012','ROSSIGNOL Sender Ti', 115000, 'mid'),
  ('SKI-015','VOLKL Mantra M6', 108000, 'mid'),
  ('SKI-016','K2 Disruption 82Ti', 95000, 'mid'),
  ('SKI-017','K2 Mindbender 99Ti', 112000, 'mid'),
  ('SKI-018','K2 Reckoner 102', 88000, 'mid'),
  ('SKI-020','FISCHER Ranger 96', 99000, 'mid'),
  ('BTS-001','ATOMIC Redster CS 130', 98000, 'mid'),
  ('BTS-002','ATOMIC Hawx Ultra 130', 88000, 'mid'),
  ('BTS-004','SALOMON S/Pro Alpha 130', 95000, 'mid'),
  ('BTS-008','HEAD Formula 130', 78000, 'mid'),
  ('BTS-016','TECNICA Mach1 LV 130', 90000, 'mid'),
  -- entry (< 70,000) : 初心者・レンタル卒業組
  ('BTS-003','ATOMIC Hawx Prime 120 S', 72000, 'entry'),
  ('BTS-005','SALOMON S/Pro Supra BOA 120', 82000, 'entry'),
  ('BTS-006','SALOMON S/Pro MV 100', 58000, 'entry'),
  ('BTS-009','HEAD Edge LYT 100', 52000, 'entry'),
  ('BTS-011','ROSSIGNOL Speed 120', 68000, 'entry'),
  ('BTS-012','ROSSIGNOL Alltrack Pro 120 LT', 75000, 'entry'),
  ('BTS-015','NORDICA Speedmachine 3 110', 62000, 'entry'),
  ('BTS-017','TECNICA Mach Sport HV 120', 65000, 'entry'),
  ('BTS-024','FISCHER RC One 110', 55000, 'entry'),
  ('BTS-026','K2 BFC 100', 48000, 'entry'),
  ('BTS-027','FULL TILT Descendant 100', 52000, 'entry'),
  ('BTS-029','REXXAM XX-97', 68000, 'entry');

-- 既存履歴注文を全クリア
DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'ORD-H-%');
DELETE FROM orders WHERE order_number LIKE 'ORD-H-%';

-- 730 日分（過去 2 年）を季節性付きで投入
WITH days AS (
    SELECT
        d_offset,
        (date_trunc('day', NOW()) - (d_offset * INTERVAL '1 day'))::date AS day_date
    FROM generate_series(1, 730) AS d_offset
),
weighted AS (
    SELECT
        d_offset, day_date,
        EXTRACT(MONTH FROM day_date)::int AS m,
        -- 月別の 1 日あたり注文目標数
        CASE EXTRACT(MONTH FROM day_date)::int
            WHEN 1  THEN 52  -- 初心者購入ピーク
            WHEN 2  THEN 38
            WHEN 3  THEN 30  -- 早期受注スタート
            WHEN 4  THEN 42
            WHEN 5  THEN 58  -- 早期受注締切ピーク
            WHEN 6  THEN 48
            WHEN 7  THEN 22  -- 通常オーダー
            WHEN 8  THEN 12  -- オフシーズン
            WHEN 9  THEN 18  -- 入荷開始
            WHEN 10 THEN 25
            WHEN 11 THEN 32  -- プレシーズン
            WHEN 12 THEN 50  -- 店頭販売ピーク
        END AS daily_count,
        -- 月別の購買層（価格帯）
        CASE
            WHEN EXTRACT(MONTH FROM day_date)::int BETWEEN 3 AND 6 THEN 'premium'
            WHEN EXTRACT(MONTH FROM day_date)::int IN (12, 1, 2)   THEN 'entry'
            ELSE 'mid'
        END AS tier_pref
    FROM days
),
seeds AS (
    SELECT
        w.day_date, w.tier_pref,
        -- 注文時刻（10:00〜21:00 にゆらぎ）
        (w.day_date + INTERVAL '10 hours' + (random() * INTERVAL '11 hours')) AS ts,
        -- ランダムに顧客 1〜300 から
        ('00000000-0000-0000-0000-' || lpad((1 + (random() * 299)::int)::text, 12, '0'))::uuid AS customer_uuid,
        n
    FROM weighted w
    CROSS JOIN LATERAL generate_series(
        1,
        GREATEST(1, w.daily_count + (random() * 6 - 3)::int)  -- ±3 件の揺らぎ
    ) AS n
),
priced AS (
    SELECT
        s.*,
        -- 該当 tier から商品をランダム選択 + 数量 1〜3
        c.sku, c.name, c.price,
        (1 + (random() * 2)::int) AS quantity
    FROM seeds s
    JOIN LATERAL (
        SELECT sku, name, price
        FROM _catalog_seasonal
        WHERE tier = s.tier_pref
        ORDER BY random()
        LIMIT 1
    ) c ON true
),
inserted AS (
    INSERT INTO orders (
        id, order_number, customer_id, status, payment_status,
        subtotal_amount, tax_amount, shipping_amount, discount_amount, total_amount,
        currency, shipping_address, created_at, updated_at, version
    )
    SELECT
        gen_random_uuid(),
        'ORD-H-' || to_char(ts, 'YYYYMMDD') || '-' || lpad((row_number() OVER (ORDER BY ts, n))::text, 6, '0'),
        customer_uuid,
        CASE WHEN random() < 0.85 THEN 'DELIVERED'
             WHEN random() < 0.95 THEN 'SHIPPED'
             ELSE 'CANCELLED' END,
        CASE WHEN random() < 0.92 THEN 'CAPTURED' ELSE 'AUTHORIZED' END,
        price * quantity, 0, 0, 0, price * quantity,
        'JPY',
        '東京都港区赤坂' || (1 + (random()*8)::int) || '-' || (1 + (random()*30)::int) || '-' || (1 + (random()*20)::int),
        ts, ts, 0
    FROM priced
    RETURNING id, total_amount
),
catalog_pick AS (
    -- inserted の各注文に対応する商品を再選択（金額不変）
    SELECT id, total_amount,
           (SELECT sku FROM _catalog_seasonal ORDER BY random() LIMIT 1) AS sku
    FROM inserted
)
INSERT INTO order_items (id, order_id, product_id, product_name, product_sku, quantity, unit_price, subtotal)
SELECT
    gen_random_uuid(),
    cp.id,
    cp.sku, cs.name, cp.sku,
    1, cp.total_amount, cp.total_amount
FROM catalog_pick cp
JOIN _catalog_seasonal cs ON cs.sku = cp.sku;

COMMIT;

-- 検証クエリ
\c skishop_sales
SELECT
    to_char(date_trunc('month', created_at), 'YYYY-MM') AS month,
    COUNT(*) AS orders,
    to_char(SUM(total_amount), 'FM9,999,999,999') AS sales
FROM orders
GROUP BY 1
ORDER BY 1;
