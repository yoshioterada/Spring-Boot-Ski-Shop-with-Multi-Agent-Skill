-- =============================================================================
-- Seed: 100円 → 1ポイント の付与レートと、過去注文に対するポイントバックフィル
--
-- ■ 設計
--   point-service の PointService.awardPoints() は次式で付与する:
--       points = ceil(amount * tierDef.pointMultiplier)
--   ここに amount として「円」を渡すため、multiplier を 0.01 系にすれば
--   100円 = 1ポイント (BRONZE) を達成できる。
--
--   ティア毎の付与倍率:
--     BRONZE   : 100円 × 0.0100 = 1   pt
--     SILVER   : 100円 × 0.0125 = 1.25 → ceil 2 pt (実質 25% ボーナス)
--     GOLD     : 100円 × 0.0150 = 1.5  → ceil 2 pt (実質 50% ボーナス)
--     PLATINUM : 100円 × 0.0200 = 2   pt          (100% ボーナス)
--
--   ティア昇格しきい値（直近 90 日の獲得 pt 基準を想定）:
--     BRONZE   : 0 pt 〜
--     SILVER   :  5,000 pt 以上 (=  50万円相当の利用)
--     GOLD     : 15,000 pt 以上 (= 150万円相当の利用)
--     PLATINUM : 30,000 pt 以上 (= 300万円相当の利用)
--
-- ■ バックフィル方針
--   skishop_sales.orders を dblink で参照し、直近 90 日かつ DELIVERED の
--   合計金額 / 100 を獲得ポイントとして 1 件のサマリ EARNED トランザクション
--   を user 毎に登録する。全期間 (730日) を対象にすると全員 PLATINUM になり
--   ティア分布が単調になるため、デモとして 90 日窓を採用。
--
-- ■ 冪等性
--   reference_id は 'HIST-BACKFILL-<userId>' 形式。再実行時はこの prefix を
--   削除してから再投入するため、何度走らせても結果は同じ。
--
-- ■ 前提
--   skishop-postgres コンテナ内で実行され、point_db に dblink 拡張が
--   インストール可能であること。CREATE EXTENSION 文を冒頭で実行する。
--
-- ■ 実行方法
--   docker cp docker/initdb/06_seed_points_backfill.sql skishop-postgres:/tmp/
--   docker exec skishop-postgres psql -U postgres -f /tmp/06_seed_points_backfill.sql
-- =============================================================================

\c point_db

CREATE EXTENSION IF NOT EXISTS dblink;

-- 元のスキーマでは NUMERIC(5,2) のため 0.0125 等が 0.01 に丸まる。拡張する。
ALTER TABLE tier_definitions ALTER COLUMN point_multiplier TYPE NUMERIC(6,4);

BEGIN;

-- -----------------------------------------------------------------------------
-- Section 1: 付与レート + ティアしきい値を更新
-- -----------------------------------------------------------------------------
UPDATE tier_definitions
   SET min_points = 0, point_multiplier = 0.0100,
       description = 'Bronze - 100円 = 1ポイント (基本)'
 WHERE tier_level = 'BRONZE';

UPDATE tier_definitions
   SET min_points = 5000, point_multiplier = 0.0125,
       description = 'Silver - 100円 = 1.25ポイント (ティアボーナス 25%)'
 WHERE tier_level = 'SILVER';

UPDATE tier_definitions
   SET min_points = 15000, point_multiplier = 0.0150,
       description = 'Gold - 100円 = 1.5ポイント (ティアボーナス 50%)'
 WHERE tier_level = 'GOLD';

UPDATE tier_definitions
   SET min_points = 30000, point_multiplier = 0.0200,
       description = 'Platinum - 100円 = 2ポイント (ティアボーナス 100%)'
 WHERE tier_level = 'PLATINUM';

-- -----------------------------------------------------------------------------
-- Section 2: デモユーザの過去 90 日分注文をポイントにバックフィル
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS _hist90;
CREATE TEMP TABLE _hist90 AS
SELECT customer_id, total_amount
  FROM dblink(
        'host=localhost port=5432 dbname=skishop_sales user=postgres password=postgres',
        $$
        SELECT customer_id, COALESCE(SUM(total_amount), 0) AS total_amount
          FROM orders
         WHERE status = 'DELIVERED'
           AND customer_id::text LIKE '00000000-0000-0000-0000-%'
           AND created_at >= NOW() - INTERVAL '90 days'
         GROUP BY customer_id
        $$
      ) AS t(customer_id UUID, total_amount NUMERIC);

-- 冪等: 既存の backfill トランザクションをクリア
DELETE FROM point_transactions WHERE reference_id LIKE 'HIST-BACKFILL-%';

-- user_tiers を upsert (一度 BRONZE / 0pt にリセットしてから加算)
INSERT INTO user_tiers (user_id, current_tier, current_balance, total_earned, total_redeemed)
SELECT customer_id, 'BRONZE', 0, 0, 0 FROM _hist90
ON CONFLICT (user_id) DO UPDATE SET
    current_balance = 0,
    total_earned    = 0,
    current_tier    = 'BRONZE',
    updated_at      = NOW();

-- サマリ EARNED トランザクションを 1 件投入
INSERT INTO point_transactions (
    user_id, transaction_type, points, balance_after,
    description, reference_id, expires_at, created_at, version, is_expired
)
SELECT customer_id,
       'EARNED',
       FLOOR(total_amount / 100)::INTEGER,
       FLOOR(total_amount / 100)::INTEGER,
       '過去 90 日分注文サマリ・ポイントバックフィル (100円 = 1pt)',
       'HIST-BACKFILL-' || customer_id::text,
       NOW() + INTERVAL '1 year',
       NOW(),
       0,
       FALSE
  FROM _hist90
 WHERE total_amount > 0;

-- 残高を反映
UPDATE user_tiers ut
   SET current_balance = FLOOR(_hist90.total_amount / 100)::INTEGER,
       total_earned    = FLOOR(_hist90.total_amount / 100)::INTEGER,
       updated_at      = NOW()
  FROM _hist90
 WHERE ut.user_id = _hist90.customer_id;

-- ティアを再判定
UPDATE user_tiers
   SET current_tier = CASE
           WHEN total_earned >= 30000 THEN 'PLATINUM'
           WHEN total_earned >= 15000 THEN 'GOLD'
           WHEN total_earned >=  5000 THEN 'SILVER'
           ELSE 'BRONZE'
       END,
       tier_upgraded_at = NOW(),
       updated_at = NOW()
 WHERE user_id IN (SELECT customer_id FROM _hist90);

COMMIT;

-- -----------------------------------------------------------------------------
-- 確認
-- -----------------------------------------------------------------------------
\echo '=== tier definitions ==='
SELECT tier_level, min_points, point_multiplier, description
  FROM tier_definitions ORDER BY min_points;

\echo '=== ティア分布 ==='
SELECT current_tier, COUNT(*) AS users, MIN(total_earned) AS min_pt, MAX(total_earned) AS max_pt
  FROM user_tiers
 WHERE user_id::text LIKE '00000000-0000-0000-0000-%'
 GROUP BY current_tier
 ORDER BY MIN(total_earned);
