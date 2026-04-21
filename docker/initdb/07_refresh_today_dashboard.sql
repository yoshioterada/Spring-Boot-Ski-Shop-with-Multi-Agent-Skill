-- =============================================================================
-- Refresh: 本日のダッシュボードデモデータを再投入
--
-- 03_seed_dashboard.sql は DB 初期化時に実行されるが、「本日」のデータは
-- 実行時の日付に依存する。日をまたぐと本日の売上・注文数・新規会員が 0 になる。
-- このスクリプトを再実行することで本日分のデータを再投入する。
--
-- 実行方法 (AKS):
--   kubectl cp docker/initdb/07_refresh_today_dashboard.sql <postgres-pod>:/tmp/
--   kubectl exec <postgres-pod> -- psql -U postgres -f /tmp/07_refresh_today_dashboard.sql
--
-- 実行方法 (Docker Compose):
--   docker cp docker/initdb/07_refresh_today_dashboard.sql skishop-postgres:/tmp/
--   docker exec skishop-postgres psql -U postgres -f /tmp/07_refresh_today_dashboard.sql
--
-- 冪等: 何度実行しても同じ結果になる（既存の本日データを削除してから再投入）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Section 1: skishop_users — 新規会員 12 名を本日の created_at に更新
-- -----------------------------------------------------------------------------
\c skishop_users

BEGIN;

-- 先頭 12 名（demo001〜demo012）の created_at を本日に更新
UPDATE user_profiles
SET created_at = date_trunc('day', NOW()) + ((row_num) * INTERVAL '15 minutes'),
    updated_at = NOW()
FROM (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY id) AS row_num
    FROM user_profiles
    WHERE id IN (
        SELECT ('00000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid
        FROM generate_series(1, 12) AS i
    )
) sub
WHERE user_profiles.id = sub.id;

COMMIT;

SELECT 'new members today' AS metric, COUNT(*)::text AS value
FROM user_profiles
WHERE created_at >= date_trunc('day', NOW());

-- -----------------------------------------------------------------------------
-- Section 2: skishop_sales — 本日の注文 50 件、合計 ¥2,380,000
-- -----------------------------------------------------------------------------
\c skishop_sales

BEGIN;

-- 本日の既存注文をクリア（冪等化のため）
DELETE FROM order_items
 WHERE order_id IN (SELECT id FROM orders WHERE created_at >= date_trunc('day', NOW()));
DELETE FROM orders WHERE created_at >= date_trunc('day', NOW());

-- 商品カタログ一時テーブル
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

-- 本日 50 件、合計 ¥2,380,000
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
            amount := remaining;
        ELSE
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

COMMIT;

-- -----------------------------------------------------------------------------
-- Verification
-- -----------------------------------------------------------------------------
\c skishop_users
SELECT 'new members today' AS metric, COUNT(*)::text AS value
FROM user_profiles
WHERE created_at >= date_trunc('day', NOW());

\c skishop_sales
SELECT 'orders today count' AS metric, COUNT(*)::text AS value
FROM orders WHERE created_at >= date_trunc('day', NOW())
UNION ALL
SELECT 'orders today sum',
       to_char(COALESCE(SUM(total_amount),0), 'FM999,999,999')
FROM orders WHERE created_at >= date_trunc('day', NOW());
