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

-- 商品カタログ + 価格帯（tier）+ カテゴリ
-- カテゴリ ID は inventory-management-service と一致:
--   cat-ski / cat-boots / cat-wear / cat-gloves / cat-goggles / cat-helmets / cat-poles
CREATE TEMP TABLE _catalog_seasonal (
    sku       text PRIMARY KEY,
    name      text,
    price     numeric,
    tier      text,    -- 'premium' | 'mid' | 'entry'
    category  text     -- canonical cat-* ID
) ON COMMIT DROP;

INSERT INTO _catalog_seasonal VALUES
  -- ===== cat-ski (スキー板) =====
  ('SKI-001','ATOMIC Redster S9i',           165000, 'premium', 'cat-ski'),
  ('SKI-002','ATOMIC Redster X9i',           155000, 'premium', 'cat-ski'),
  ('SKI-004','SALOMON S/Race FIS GS',        178000, 'premium', 'cat-ski'),
  ('SKI-007','HEAD Supershape e-Speed',      148000, 'premium', 'cat-ski'),
  ('SKI-008','HEAD World Cup Rebels e-GS',   168000, 'premium', 'cat-ski'),
  ('SKI-010','ROSSIGNOL Hero Elite ST Ti',   135000, 'premium', 'cat-ski'),
  ('SKI-013','VOLKL Racetiger SL',           145000, 'premium', 'cat-ski'),
  ('SKI-014','VOLKL Deacon 76',              128000, 'premium', 'cat-ski'),
  ('SKI-019','FISCHER RC4 The Curv GT 80',   140000, 'premium', 'cat-ski'),
  ('SKI-021','NORDICA Dobermann SLR',        138000, 'premium', 'cat-ski'),
  ('SKI-028','OGASAKA TC-SS',                132000, 'premium', 'cat-ski'),
  ('SKI-003','ATOMIC Bent Chetler 100',      110000, 'mid',     'cat-ski'),
  ('SKI-005','SALOMON QST 98',                98000, 'mid',     'cat-ski'),
  ('SKI-006','SALOMON Stance 96',             89000, 'mid',     'cat-ski'),
  ('SKI-009','HEAD Kore 93',                 105000, 'mid',     'cat-ski'),
  ('SKI-011','ROSSIGNOL Experience 82 Ti',    88000, 'mid',     'cat-ski'),
  ('SKI-012','ROSSIGNOL Sender Ti',          115000, 'mid',     'cat-ski'),
  ('SKI-015','VOLKL Mantra M6',              108000, 'mid',     'cat-ski'),
  ('SKI-016','K2 Disruption 82Ti',            95000, 'mid',     'cat-ski'),
  ('SKI-017','K2 Mindbender 99Ti',           112000, 'mid',     'cat-ski'),
  ('SKI-018','K2 Reckoner 102',               88000, 'entry',   'cat-ski'),
  ('SKI-020','FISCHER Ranger 96',             99000, 'entry',   'cat-ski'),
  -- ===== cat-boots (スキーブーツ) =====
  ('BTS-007','HEAD Raptor 140 RS',           105000, 'premium', 'cat-boots'),
  ('BTS-010','ROSSIGNOL Hero World Cup ZJ+', 110000, 'premium', 'cat-boots'),
  ('BTS-001','ATOMIC Redster CS 130',         98000, 'mid',     'cat-boots'),
  ('BTS-002','ATOMIC Hawx Ultra 130',         88000, 'mid',     'cat-boots'),
  ('BTS-004','SALOMON S/Pro Alpha 130',       95000, 'mid',     'cat-boots'),
  ('BTS-008','HEAD Formula 130',              78000, 'mid',     'cat-boots'),
  ('BTS-016','TECNICA Mach1 LV 130',          90000, 'mid',     'cat-boots'),
  ('BTS-003','ATOMIC Hawx Prime 120 S',       72000, 'entry',   'cat-boots'),
  ('BTS-005','SALOMON S/Pro Supra BOA 120',   82000, 'entry',   'cat-boots'),
  ('BTS-006','SALOMON S/Pro MV 100',          58000, 'entry',   'cat-boots'),
  ('BTS-009','HEAD Edge LYT 100',             52000, 'entry',   'cat-boots'),
  ('BTS-011','ROSSIGNOL Speed 120',           68000, 'entry',   'cat-boots'),
  ('BTS-027','FULL TILT Descendant 100',      52000, 'entry',   'cat-boots'),
  -- ===== cat-wear (ウェア) =====
  ('WER-004','PHENIX Norway Team ジャケット',  125000, 'premium', 'cat-wear'),
  ('WER-007','GOLDWIN G-Bliss ジャケット',     135000, 'premium', 'cat-wear'),
  ('WER-012','THE NORTH FACE Summit L5',     148000, 'premium', 'cat-wear'),
  ('WER-014','HELLY HANSEN Alpha 4.0',       115000, 'premium', 'cat-wear'),
  ('WER-001','DESCENTE S.I.O ジャケット',      110000, 'mid',     'cat-wear'),
  ('WER-002','DESCENTE スイスレプリカ',         98000, 'mid',     'cat-wear'),
  ('WER-008','GOLDWIN 2トーンカラー',           88000, 'mid',     'cat-wear'),
  ('WER-009','GOLDWIN G-Bliss パンツ',         85000, 'mid',     'cat-wear'),
  ('WER-016','SPYDER Vanqysh ジャケット',      105000, 'mid',     'cat-wear'),
  ('WER-003','DESCENTE S.I.O パンツ',          68000, 'entry',   'cat-wear'),
  ('WER-005','PHENIX Thunderbolt ジャケット',  78000, 'entry',   'cat-wear'),
  ('WER-006','PHENIX Thunderbolt パンツ',     55000, 'entry',   'cat-wear'),
  ('WER-010','MIZUNO フリースキー ジャケット', 58000, 'entry',   'cat-wear'),
  ('WER-011','MIZUNO スキーパンツ',            42000, 'entry',   'cat-wear'),
  ('WER-013','THE NORTH FACE Freedom',        65000, 'entry',   'cat-wear'),
  ('WER-015','HELLY HANSEN Legendary',        52000, 'entry',   'cat-wear'),
  -- ===== cat-gloves (グローブ) =====
  ('GLV-011','BLACK DIAMOND Guide Finger',    28000, 'premium', 'cat-gloves'),
  ('GLV-002','HESTRA Army Leather Heli Ski',  25000, 'premium', 'cat-gloves'),
  ('GLV-004','HESTRA Leather Fall Line 3F',   24000, 'premium', 'cat-gloves'),
  ('GLV-001','HESTRA Fall Line',              22000, 'mid',     'cat-gloves'),
  ('GLV-025','OUTDOOR RESEARCH Revolution',   22000, 'mid',     'cat-gloves'),
  ('GLV-018','GOLDWIN GR-A106',               16000, 'mid',     'cat-gloves'),
  ('GLV-008','LEKI WCR Flex 3D',              16000, 'mid',     'cat-gloves'),
  ('GLV-013','SWANY SX-70 Toaster',           15000, 'mid',     'cat-gloves'),
  ('GLV-005','REUSCH Worldcup Warrior GS',    15000, 'entry',   'cat-gloves'),
  ('GLV-009','LEKI Griffin Pro 3D',           14000, 'entry',   'cat-gloves'),
  ('GLV-019','SALOMON Force GTX',             12000, 'entry',   'cat-gloves'),
  ('GLV-010','LEKI Stratos',                  11000, 'entry',   'cat-gloves'),
  ('GLV-021','ATOMIC Redster レーシング',     10000, 'entry',   'cat-gloves'),
  ('GLV-007','REUSCH Primus R-TEX XT',         9800, 'entry',   'cat-gloves'),
  ('GLV-023','ROSSIGNOL Speed IMPR',           9000, 'entry',   'cat-gloves'),
  ('GLV-020','SALOMON Propeller Dry',          8500, 'entry',   'cat-gloves'),
  -- ===== cat-goggles (ゴーグル) =====
  ('GGL-006','SMITH 4D MAG',                  42000, 'premium', 'cat-goggles'),
  ('GGL-002','OAKLEY Airbrake XL',            38000, 'premium', 'cat-goggles'),
  ('GGL-005','SMITH I/O Mag',                 35000, 'premium', 'cat-goggles'),
  ('GGL-001','OAKLEY Flight Deck L',          32000, 'mid',     'cat-goggles'),
  ('GGL-020','POC Fovea Clarity',             32000, 'mid',     'cat-goggles'),
  ('GGL-014','GIRO Contour RS',               30000, 'mid',     'cat-goggles'),
  ('GGL-009','SWANS ROVO-MDH',                28000, 'mid',     'cat-goggles'),
  ('GGL-019','POC Orb Clarity',               28000, 'mid',     'cat-goggles'),
  ('GGL-003','OAKLEY Line Miner L',           25000, 'mid',     'cat-goggles'),
  ('GGL-015','GIRO Method',                   25000, 'mid',     'cat-goggles'),
  ('GGL-011','SWANS Ridgeline',               24000, 'entry',   'cat-goggles'),
  ('GGL-004','OAKLEY Flight Tracker L',       22000, 'entry',   'cat-goggles'),
  ('GGL-007','SMITH Squad MAG',               22000, 'entry',   'cat-goggles'),
  ('GGL-016','GIRO Axis',                     22000, 'entry',   'cat-goggles'),
  ('GGL-010','SWANS RACAN-MDH',               20000, 'entry',   'cat-goggles'),
  ('GGL-008','SMITH Skyline XL',              18000, 'entry',   'cat-goggles'),
  ('GGL-017','BOLLE Nevada',                  18000, 'entry',   'cat-goggles'),
  ('GGL-018','BOLLE Torus',                   16000, 'entry',   'cat-goggles'),
  -- ===== cat-helmets (ヘルメット) =====
  ('HLM-017','POC Skull Dura X MIPS',         55000, 'premium', 'cat-helmets'),
  ('HLM-025','BOLLE Medalist Carbon Pro',     55000, 'premium', 'cat-helmets'),
  ('HLM-001','GIRO Strive MIPS',              52000, 'premium', 'cat-helmets'),
  ('HLM-012','SWEET PROTECTION Trooper 2Vi',  48000, 'premium', 'cat-helmets'),
  ('HLM-020','ROSSIGNOL Hero Giant Impacts',  45000, 'mid',     'cat-helmets'),
  ('HLM-004','SMITH Vantage MIPS',            42000, 'mid',     'cat-helmets'),
  ('HLM-014','UVEX Race+',                    42000, 'mid',     'cat-helmets'),
  ('HLM-002','GIRO Neo MIPS',                 38000, 'mid',     'cat-helmets'),
  ('HLM-010','SALOMON Driver Pro Sigma MIPS', 38000, 'mid',     'cat-helmets'),
  ('HLM-019','HEAD Race',                     38000, 'mid',     'cat-helmets'),
  ('HLM-027','ANON Merak WaveCel',            38000, 'mid',     'cat-helmets'),
  ('HLM-026','OAKLEY MOD5 MIPS',              35000, 'mid',     'cat-helmets'),
  ('HLM-007','ATOMIC Redster CTD',            35000, 'mid',     'cat-helmets'),
  ('HLM-013','SWEET PROTECTION Switcher MIPS',35000, 'mid',     'cat-helmets'),
  ('HLM-016','POC Obex MIPS',                 30000, 'mid',     'cat-helmets'),
  ('HLM-003','GIRO Range MIPS',               32000, 'mid',     'cat-helmets'),
  ('HLM-009','SALOMON MTN Lab',               32000, 'mid',     'cat-helmets'),
  ('HLM-005','SMITH Code MIPS',               28000, 'entry',   'cat-helmets'),
  ('HLM-008','ATOMIC Count AMID',             28000, 'entry',   'cat-helmets'),
  ('HLM-030','SCOTT Symbol 2 Plus MIPS',      28000, 'entry',   'cat-helmets'),
  ('HLM-018','HEAD Radar MIPS',               25000, 'entry',   'cat-helmets'),
  ('HLM-022','K2 Verdict MIPS',               25000, 'entry',   'cat-helmets'),
  ('HLM-006','SMITH Level MIPS',              22000, 'entry',   'cat-helmets'),
  ('HLM-021','ROSSIGNOL Fit Impacts MIPS',    22000, 'entry',   'cat-helmets'),
  ('HLM-024','BOLLE Atmos MIPS',              22000, 'entry',   'cat-helmets'),
  ('HLM-029','MARKER Ampire 2 MIPS',          20000, 'entry',   'cat-helmets'),
  ('HLM-011','SALOMON Pioneer LT MIPS',       18000, 'entry',   'cat-helmets'),
  ('HLM-015','UVEX Legend 2.0',               18000, 'entry',   'cat-helmets'),
  ('HLM-023','K2 Phase Pro',                  18000, 'entry',   'cat-helmets'),
  ('HLM-028','ALPINA Grand',                  15000, 'entry',   'cat-helmets'),
  -- ===== cat-poles (ポール) =====
  ('POL-004','LEKI Carbon 14 3D',             28000, 'premium', 'cat-poles'),
  ('POL-018','SALOMON MTN Carbon S3',         24000, 'premium', 'cat-poles'),
  ('POL-001','LEKI WCR SL 3D',                22000, 'premium', 'cat-poles'),
  ('POL-002','LEKI WCR GS 3D',                22000, 'premium', 'cat-poles'),
  ('POL-015','ATOMIC BCT Touring Carbon',     22000, 'premium', 'cat-poles'),
  ('POL-020','ROSSIGNOL Hero Carbon Safety',  22000, 'premium', 'cat-poles'),
  ('POL-010','SWIX WorldCup GS',              20000, 'mid',     'cat-poles'),
  ('POL-006','SINANO CK-GS',                  18000, 'mid',     'cat-poles'),
  ('POL-007','SINANO CK-SL',                  18000, 'mid',     'cat-poles'),
  ('POL-011','SWIX WorldCup SL',              18000, 'mid',     'cat-poles'),
  ('POL-013','ATOMIC Redster Carbon',         18000, 'mid',     'cat-poles'),
  ('POL-021','ROSSIGNOL Tactic Carbon Safety',18000, 'mid',     'cat-poles'),
  ('POL-003','LEKI SpeedS',                   15000, 'entry',   'cat-poles'),
  ('POL-008','SINANO Free-K',                 14000, 'entry',   'cat-poles'),
  ('POL-005','LEKI Detect S',                 12000, 'entry',   'cat-poles'),
  ('POL-016','SALOMON X North',               12000, 'entry',   'cat-poles'),
  ('POL-019','ROSSIGNOL Stove',               10000, 'entry',   'cat-poles'),
  ('POL-009','SINANO Eagle R',                10000, 'entry',   'cat-poles'),
  ('POL-012','SWIX Sonic R1',                 10000, 'entry',   'cat-poles'),
  ('POL-014','ATOMIC AMT SQS',                 8000, 'entry',   'cat-poles'),
  ('POL-017','SALOMON Arctic S3',              8000, 'entry',   'cat-poles');

-- カテゴリ別の購買比率（合計 100）。リアルなスキー EC の購買分布を模擬。
--   ski 22 / boots 18 / wear 18 / helmets 12 / goggles 12 / gloves 10 / poles 8
CREATE TEMP TABLE _category_weights (category text PRIMARY KEY, weight int) ON COMMIT DROP;
INSERT INTO _category_weights VALUES
  ('cat-ski',     22),
  ('cat-boots',   18),
  ('cat-wear',    18),
  ('cat-helmets', 12),
  ('cat-goggles', 12),
  ('cat-gloves',  10),
  ('cat-poles',    8);

-- 既存履歴注文を全クリア
DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'ORD-H-%');
DELETE FROM orders WHERE order_number LIKE 'ORD-H-%';

-- ステージングテーブル: orders と order_items の両方を一貫したデータで投入するため、
-- 注文 1 件分の全情報（uuid, 商品 sku/name/price/quantity, ts, customer 等）を一旦ここに作成。
-- これにより「order の total_amount と order_item の sku が食い違う」問題を防ぐ。
CREATE TEMP TABLE _staging_orders (
    order_id     uuid,
    customer_id  uuid,
    ts           timestamp,
    sku          text,
    name         text,
    unit_price   numeric,
    quantity     int,
    total_amount numeric,
    status       text,
    payment_status text,
    shipping_address text
) ON COMMIT DROP;

-- 730 日分（過去 2 年）の注文を季節性 + カテゴリ別重み付きで生成
INSERT INTO _staging_orders
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
            WHEN 1  THEN 52
            WHEN 2  THEN 38
            WHEN 3  THEN 30
            WHEN 4  THEN 42
            WHEN 5  THEN 58
            WHEN 6  THEN 48
            WHEN 7  THEN 22
            WHEN 8  THEN 12
            WHEN 9  THEN 18
            WHEN 10 THEN 25
            WHEN 11 THEN 32
            WHEN 12 THEN 50
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
        (w.day_date + INTERVAL '10 hours' + (random() * INTERVAL '11 hours')) AS ts,
        ('00000000-0000-0000-0000-' || lpad((1 + (random() * 299)::int)::text, 12, '0'))::uuid AS customer_uuid,
        n,
        cat.category
    FROM weighted w
    CROSS JOIN LATERAL generate_series(
        1,
        GREATEST(1, w.daily_count + (random() * 6 - 3)::int)
    ) AS n
    -- LATERAL JOIN により行ごとにランダム抽選される。
    -- 重み付きランダム選択: A-Res (Efraimidis & Spirakis) 法。
    --   key = random()^(1/weight) を最大化したものを選ぶ → weight に比例した確率で抽選される。
    -- 注意: PostgreSQL は uncorrelated な LATERAL を 1 回しか評価しないため、
    -- WHERE 句に外部参照 (n IS NOT NULL) を入れて行ごとに強制再評価させる。
    CROSS JOIN LATERAL (
        SELECT category
        FROM _category_weights
        WHERE n IS NOT NULL
        ORDER BY power(random(), 1.0 / weight) DESC
        LIMIT 1
    ) cat
),
priced AS (
    SELECT
        gen_random_uuid() AS order_id,
        s.customer_uuid,
        s.ts,
        c.sku, c.name, c.price,
        -- 高額カテゴリ (ski/boots/wear/helmets) は 1 個、小物 (gloves/goggles/poles) は 1〜3 個
        CASE
            WHEN c.category IN ('cat-gloves','cat-goggles','cat-poles') THEN 1 + (random() * 2)::int
            ELSE 1
        END AS quantity,
        CASE WHEN random() < 0.85 THEN 'DELIVERED'
             WHEN random() < 0.95 THEN 'SHIPPED'
             ELSE 'CANCELLED' END AS status,
        CASE WHEN random() < 0.92 THEN 'CAPTURED' ELSE 'AUTHORIZED' END AS payment_status
    FROM seeds s
    -- s への参照 (cs.category = s.category) があるため自動的に行ごとに再評価される。
    JOIN LATERAL (
        SELECT sku, name, price, category
        FROM _catalog_seasonal cs
        WHERE cs.category = s.category
          AND (cs.tier = s.tier_pref
               -- 該当 tier に商品が無い小カテゴリ向けのフォールバック
               OR NOT EXISTS (SELECT 1 FROM _catalog_seasonal x
                              WHERE x.category = s.category AND x.tier = s.tier_pref))
        ORDER BY random()
        LIMIT 1
    ) c ON true
)
SELECT
    order_id,
    customer_uuid,
    ts,
    sku, name, price AS unit_price, quantity, price * quantity AS total_amount,
    status, payment_status,
    '東京都港区赤坂' || (1 + (random()*8)::int) || '-' || (1 + (random()*30)::int) || '-' || (1 + (random()*20)::int) AS shipping_address
FROM priced;

-- orders を投入
INSERT INTO orders (
    id, order_number, customer_id, status, payment_status,
    subtotal_amount, tax_amount, shipping_amount, discount_amount, total_amount,
    currency, shipping_address, created_at, updated_at, version
)
SELECT
    order_id,
    'ORD-H-' || to_char(ts, 'YYYYMMDD') || '-' || lpad((row_number() OVER (ORDER BY ts))::text, 6, '0'),
    customer_id,
    status, payment_status,
    total_amount, 0, 0, 0, total_amount,
    'JPY',
    shipping_address,
    ts, ts, 0
FROM _staging_orders;

-- order_items を同じステージングから投入（sku/quantity/unit_price が orders と完全に整合）
INSERT INTO order_items (
    id, order_id, product_id, product_name, product_sku,
    quantity, unit_price, subtotal
)
SELECT
    gen_random_uuid(),
    order_id,
    sku, name, sku,
    quantity, unit_price, total_amount
FROM _staging_orders;

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

-- 直近 30 日の SKU 接頭辞別シェア（カテゴリ分散の妥当性確認用）
SELECT
    LEFT(oi.product_sku, 3) AS sku_prefix,
    COUNT(*)                AS line_items,
    to_char(SUM(oi.subtotal), 'FM999,999,999') AS revenue
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.created_at >= NOW() - INTERVAL '30 days'
  AND o.status NOT IN ('CANCELLED','RETURNED')
GROUP BY 1
ORDER BY 1;
