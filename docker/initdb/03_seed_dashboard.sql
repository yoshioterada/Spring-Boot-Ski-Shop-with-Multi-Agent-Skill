-- =============================================================================
-- Seed: Dashboard demo data
--   - skishop_users.user_profiles : 300 名（男150 / 女150、status=ACTIVE、CUSTOMER）
--   - skishop_sales.orders        :
--       * 本日 50 件、合計 ¥2,380,000（先頭 50 名のユーザに 1 件ずつ）
--       * 過去 180 日間、日次 8〜25 件の履歴注文（日/週/月チャート用）
--
-- ユーザ UUID は決定的に '00000000-0000-0000-0000-' || lpad(i, 12, '0')。
-- 同じ UUID を skishop_sales.orders.customer_id に流用するため、両 DB を跨いで
-- 名前解決ができる。
--
-- 実行方法:
--   docker exec -i skishop-postgres psql -U postgres -f /tmp/03_seed_dashboard.sql
-- もしくは:
--   docker cp docker/initdb/03_seed_dashboard.sql skishop-postgres:/tmp/
--   docker exec skishop-postgres psql -U postgres -f /tmp/03_seed_dashboard.sql
--
-- 重複実行に備えて ON CONFLICT で冪等化。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Section 1: skishop_users — 300 名のユーザプロファイル
-- -----------------------------------------------------------------------------
\c skishop_users

BEGIN;

INSERT INTO user_profiles (
    id, email, first_name, last_name, phone_number, gender,
    status, email_verified, phone_verified, role_id, created_at, updated_at
)
SELECT
    ('00000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
    'demo' || lpad(i::text, 3, '0') || '@skishop.example.jp',
    CASE WHEN i % 2 = 1 THEN
        (ARRAY['大翔','蓮','樹','陽翔','悠真','颯太','朝陽','湊','大和','蒼',
               '律','翔','直樹','健太','翼','拓海','涼太','駿','海斗','颯',
               '圭太','翔太','悠','陸','海','晴','慶','大樹','蒼太','悠斗',
               '怜','響','奏','瑛太','陽向','海翔','陸斗','颯斗','拓真','悠輝',
               '優真','侑樹','英輝','大輔','宏樹','和樹','拓実','侑大','直人','圭吾'])
        -- 姓と名のインデックスをズラすことで、(姓,名) ペアが 300 名全員で重複しないようにする。
        -- 50 姓×50 名を 17×block で差し込み (17 は 50 と互いに素)、
        -- 同一姓グループ内でも名が重複しない。
        [ (((i-1)/2 % 50 + ((i-1)/100) * 17) % 50) + 1 ]
    ELSE
        (ARRAY['凛','結愛','陽葵','葵','紬','莉子','結菜','美羽','愛莉','心春',
               '詩','杏','花','美咲','麻衣','葉月','萌','真央','由美','千夏',
               '美穂','彩','纹','奈々','茨','彩花','美月','優奈','心愛','咲良',
               '楓','桜','雛','光','穂乃花','茑莉','百合','晴香','明日香','千尋',
               '美香','奈緒','理沙','菜々子','里奈','沙織','彩乃','由佳','直子','美鈴'])
        [ (((i-1)/2 % 50 + ((i-1)/100) * 17) % 50) + 1 ]
    END,
    (ARRAY['佐藤','鈴木','高橋','田中','伊藤','渡辺','山本','中村','小林','加藤',
           '吉田','山田','佐々木','山口','松本','井上','木村','林','斎藤','清水',
           '山崎','森','池田','橋本','阿部','石川','山下','中島','石井','小川',
           '前田','岡田','長谷川','藤田','後藤','近藤','坂本','遠藤','青木','藤井',
           '西村','福田','太田','三浦','岡本','松田','中川','中野','原田','小野'])
    [ ((i-1) % 50) + 1 ],
    '090-' || lpad(((1000 + i*13) % 9000 + 1000)::text, 4, '0')
           || '-' || lpad(((4000 + i*7) % 9000 + 1000)::text, 4, '0'),
    CASE WHEN i % 2 = 1 THEN 'MALE' ELSE 'FEMALE' END,
    'ACTIVE',
    true,
    false,
    'f88ded98-6908-4a5f-b75b-6ceede175260'::uuid,  -- CUSTOMER
    -- 先頭 12 名は本日作成（新規会員数 KPI 用）、残りは過去 180 日に分布
    CASE
        WHEN i <= 12 THEN
            date_trunc('day', NOW()) + (i * INTERVAL '15 minutes')
        ELSE
            NOW()
              - ((1 + (random() * 179)::int) * INTERVAL '1 day')
              - (random() * INTERVAL '1 day')
    END,
    NOW()
FROM generate_series(1, 300) AS i
ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    updated_at = NOW();

COMMIT;

-- -----------------------------------------------------------------------------
-- Section 2: skishop_sales — 注文データ
--   2-A: 本日 50 件、合計 ¥2,380,000
--   2-B: 過去 180 日間、約 2700〜4500 件の履歴
-- -----------------------------------------------------------------------------
\c skishop_sales

BEGIN;

-- セッション内一時テーブル: 商品カタログ（SKU と価格）
CREATE TEMP TABLE _catalog (sku text PRIMARY KEY, name text, price numeric) ON COMMIT DROP;

INSERT INTO _catalog VALUES
  ('SKI-001','ATOMIC Redster S9i', 165000),
  ('SKI-002','ATOMIC Redster X9i', 155000),
  ('SKI-003','ATOMIC Bent Chetler 100', 110000),
  ('SKI-004','SALOMON S/Race FIS GS', 178000),
  ('SKI-005','SALOMON QST 98', 98000),
  ('SKI-006','SALOMON Stance 96', 89000),
  ('SKI-007','HEAD Supershape e-Speed', 148000),
  ('SKI-008','HEAD World Cup Rebels e-GS', 168000),
  ('SKI-009','HEAD Kore 93', 105000),
  ('SKI-010','ROSSIGNOL Hero Elite ST Ti', 135000),
  ('SKI-011','ROSSIGNOL Experience 82 Ti', 88000),
  ('SKI-012','ROSSIGNOL Sender Ti', 115000),
  ('SKI-013','VOLKL Racetiger SL', 145000),
  ('SKI-014','VOLKL Deacon 76', 128000),
  ('SKI-015','VOLKL Mantra M6', 108000),
  ('SKI-016','K2 Disruption 82Ti', 95000),
  ('SKI-017','K2 Mindbender 99Ti', 112000),
  ('SKI-018','K2 Reckoner 102', 88000),
  ('SKI-019','FISCHER RC4 The Curv GT 80', 140000),
  ('SKI-020','FISCHER Ranger 96', 99000),
  ('BTS-001','ATOMIC Redster CS 130', 98000),
  ('BTS-002','ATOMIC Hawx Ultra 130', 88000),
  ('BTS-003','ATOMIC Hawx Prime 120 S', 72000),
  ('BTS-004','SALOMON S/Pro Alpha 130', 95000),
  ('BTS-005','SALOMON S/Pro Supra BOA 120', 82000),
  ('BTS-006','SALOMON S/Pro MV 100', 58000),
  ('BTS-007','HEAD Raptor 140 RS', 105000),
  ('BTS-008','HEAD Formula 130', 78000),
  ('BTS-009','HEAD Edge LYT 100', 52000),
  ('BTS-010','ROSSIGNOL Hero World Cup ZJ+', 110000);

-- ---------- 2-A : 本日 50 件、合計 ¥2,380,000 ----------
-- 再実行のため当日の注文を一旦クリア
DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE created_at >= date_trunc('day', NOW()));
DELETE FROM orders WHERE created_at >= date_trunc('day', NOW());
DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'ORD-H-%');
DELETE FROM orders WHERE order_number LIKE 'ORD-H-%';

DO $$
DECLARE
    i           int;
    amount      numeric;
    target_sum  numeric := 2380000;
    remaining   numeric := 2380000;
    new_oid     uuid;
    customer_uuid uuid;
    today_str   text := to_char(NOW(), 'YYYYMMDD');
    statuses    text[] := ARRAY['DELIVERED','DELIVERED','DELIVERED','SHIPPED','PROCESSING'];
    pay_status  text;
    ord_status  text;
    prod        record;
    qty         int;
    max_amt     numeric;
BEGIN
    FOR i IN 1..50 LOOP
        IF i = 50 THEN
            amount := remaining;  -- 残額をすべて 50 件目で消化
        ELSE
            -- 残額から (50 - i) 件 × 最低 1,000 を確保した上限
            max_amt := remaining - (50 - i) * 1000;
            IF max_amt > 80000 THEN max_amt := 80000; END IF;
            IF max_amt < 5000 THEN max_amt := 5000; END IF;
            amount := round((5000 + random() * (max_amt - 5000))::numeric / 100) * 100;
            IF amount > remaining - (50 - i) * 1000 THEN
                amount := remaining - (50 - i) * 1000;
            END IF;
        END IF;

        customer_uuid := ('00000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid;
        new_oid := gen_random_uuid();
        ord_status := statuses[1 + (random() * 4)::int];
        pay_status := CASE WHEN ord_status IN ('DELIVERED','SHIPPED') THEN 'CAPTURED' ELSE 'AUTHORIZED' END;

        INSERT INTO orders (
            id, order_number, customer_id, status, payment_status,
            subtotal_amount, tax_amount, shipping_amount, discount_amount, total_amount,
            currency, shipping_address, created_at, updated_at, version
        ) VALUES (
            new_oid,
            'ORD-' || today_str || '-' || lpad(i::text, 5, '0'),
            customer_uuid,
            ord_status, pay_status,
            amount, 0, 0, 0, amount,
            'JPY',
            '東京都渋谷区道玄坂1-1-' || (i % 50 + 1),
            date_trunc('day', NOW()) + (i * INTERVAL '17 minutes'),
            NOW(),
            0
        );

        SELECT * INTO prod FROM _catalog ORDER BY random() LIMIT 1;
        qty := GREATEST(1, (amount / prod.price)::int);
        IF qty * prod.price > amount THEN qty := 1; END IF;

        INSERT INTO order_items (
            id, order_id, product_id, product_name, product_sku,
            quantity, unit_price, subtotal
        ) VALUES (
            gen_random_uuid(), new_oid, prod.sku, prod.name, prod.sku,
            qty, round(amount / qty, 2), amount
        );

        remaining := remaining - amount;
    END LOOP;

    RAISE NOTICE '本日の合計売上 = ¥% (target=¥%)', target_sum - remaining, target_sum;
END $$;

-- ---------- 2-B : 過去 180 日間の履歴注文 ----------
-- 1 日あたり 8〜25 件をランダム生成。過去 180 日 × 平均 ~16 件 = ~2900 件。
-- 月→週→日チャートそれぞれで意味のあるデータ密度になる。
WITH days AS (
    SELECT generate_series(1, 180) AS d_offset
),
seeds AS (
    SELECT
        d_offset,
        n,
        -- 注文時刻: 過去 d_offset 日前のランダムな時刻
        (date_trunc('day', NOW()) - (d_offset * INTERVAL '1 day')
            + (random() * INTERVAL '23 hours')) AS ts,
        -- 顧客 UUID をランダムに 1〜300 から
        ('00000000-0000-0000-0000-' || lpad((1 + (random() * 299)::int)::text, 12, '0'))::uuid AS customer_uuid,
        -- 金額: 5,000〜120,000 (100円刻み)
        round((5000 + random() * 115000)::numeric / 100) * 100 AS amt
    FROM days
    CROSS JOIN LATERAL generate_series(1, 8 + (random() * 17)::int) AS n
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
        CASE WHEN random() < 0.90 THEN 'CAPTURED' ELSE 'AUTHORIZED' END,
        amt, 0, 0, 0, amt,
        'JPY',
        '東京都港区赤坂' || (1 + (random()*8)::int) || '-' || (1 + (random()*30)::int) || '-' || (1 + (random()*20)::int),
        ts, ts, 0
    FROM seeds
    RETURNING id, total_amount
)
INSERT INTO order_items (id, order_id, product_id, product_name, product_sku, quantity, unit_price, subtotal)
SELECT
    gen_random_uuid(),
    io.id,
    c.sku, c.name, c.sku,
    1, io.total_amount, io.total_amount
FROM inserted io
JOIN LATERAL (
    SELECT sku, name FROM _catalog ORDER BY random() LIMIT 1
) c ON true;

COMMIT;

-- -----------------------------------------------------------------------------
-- Verification
-- -----------------------------------------------------------------------------
\c skishop_users
SELECT 'user_profiles total' AS metric, COUNT(*)::text AS value FROM user_profiles
UNION ALL
SELECT 'active', COUNT(*)::text FROM user_profiles WHERE status='ACTIVE'
UNION ALL
SELECT 'created today', COUNT(*)::text FROM user_profiles WHERE created_at >= date_trunc('day', NOW());

\c skishop_sales
SELECT 'orders today count' AS metric, COUNT(*)::text AS value
FROM orders WHERE created_at >= date_trunc('day', NOW())
UNION ALL
SELECT 'orders today sum',
       to_char(COALESCE(SUM(total_amount),0), 'FM999,999,999')
FROM orders WHERE created_at >= date_trunc('day', NOW())
UNION ALL
SELECT 'orders historical count',
       COUNT(*)::text
FROM orders WHERE created_at < date_trunc('day', NOW())
UNION ALL
SELECT 'order_items total',
       COUNT(*)::text
FROM order_items;
