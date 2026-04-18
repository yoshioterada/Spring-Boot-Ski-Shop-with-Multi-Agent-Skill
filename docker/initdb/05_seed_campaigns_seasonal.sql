-- =============================================================================
-- Seed: Seasonal Campaigns & Coupons for ski-shop dashboard demo
--
--   coupon_db.campaigns : 17 件（1〜12 月のシーズンごとに対象顧客・割引率を設定）
--   coupon_db.coupons   : 51 件（各キャンペーンに 3 種類のサンプルコード）
--
-- 「現在日時 = 2026-04-18」を想定し、3-4 月のキャンペーンが is_active=true 状態。
-- 各キャンペーンの開始日・終了日はその年の対応シーズンに合わせる。
--
-- UUID は名前から md5 で導出し、冪等再実行に耐える（ON CONFLICT (id) DO NOTHING / DO UPDATE）。
-- 同じくクーポンコードはユニーク制約があるため、ON CONFLICT (code) DO NOTHING。
--
-- 実行方法:
--   docker cp docker/initdb/05_seed_campaigns_seasonal.sql skishop-postgres:/tmp/
--   docker exec skishop-postgres psql -U postgres -f /tmp/05_seed_campaigns_seasonal.sql
-- =============================================================================

\c coupon_db

BEGIN;

-- -----------------------------------------------------------------------------
-- Helper: 一時的な VALUES でキャンペーン定義をまとめてアップサート
-- -----------------------------------------------------------------------------
WITH src AS (
    SELECT
        ('00000000-0000-0000-0000-' || substr(md5(slug), 1, 12))::uuid AS id,
        name,
        description,
        campaign_type,
        start_date::timestamptz,
        end_date::timestamptz,
        is_active,
        max_coupons,
        rules::jsonb
    FROM (VALUES
        -- =====================================================================
        -- ❄️ 1〜2月：ハイシーズン（初心者・観光・インバウンド）
        -- =====================================================================
        ('camp-jan-beginner-set',
         '初心者セット即納キャンペーン',
         '板＋ビンディング＋ブーツ＋ポールの 4 点セットを当日出荷。「今日買って明日滑れる」がコンセプト。',
         'FIXED_AMOUNT',
         '2026-01-05 00:00:00+09', '2026-02-28 23:59:59+09',
         FALSE, 500,
         '{"season":"high","target":"beginner","priority":"convenience","sameDayShipping":true}'),

        ('camp-jan-school-discount',
         'スクール連動割引',
         '提携スクール申込者限定の 10% OFF クーポン。スクール卒業生の継続購入を促進。',
         'PERCENTAGE',
         '2026-01-05 00:00:00+09', '2026-02-28 23:59:59+09',
         FALSE, 1000,
         '{"season":"high","target":"beginner","partnerProgram":"ski-school"}'),

        ('camp-feb-rental-graduate',
         'レンタル卒業キャンペーン',
         '「3 回レンタルするなら買った方が安い」初購入向け 15% OFF。',
         'PERCENTAGE',
         '2026-02-01 00:00:00+09', '2026-02-28 23:59:59+09',
         FALSE, 800,
         '{"season":"high","target":"beginner","trigger":"rental-history-3plus"}'),

        -- =====================================================================
        -- 🌸 3〜4月：シーズン末（中上級者・買い替え層） ※現時点でアクティブ
        -- =====================================================================
        ('camp-mar-season-end-sale',
         'シーズンエンドセール',
         '現行モデル最大 30% OFF。在庫一掃と買い替え需要を狙う中上級者向けセール。',
         'PERCENTAGE',
         '2026-03-15 00:00:00+09', '2026-04-30 23:59:59+09',
         TRUE, 2000,
         '{"season":"end","target":"intermediate-advanced","intent":"clearance"}'),

        ('camp-mar-test-ride-coupon',
         '試乗会連動クーポン',
         '提携スキー場の試乗会参加者へ ¥5,000 OFF。「試乗 → 予約」で次シーズンの早期予約を後押し。',
         'FIXED_AMOUNT',
         '2026-03-15 00:00:00+09', '2026-04-30 23:59:59+09',
         TRUE, 600,
         '{"season":"end","target":"intermediate-advanced","partnerProgram":"test-ride"}'),

        ('camp-apr-next-season-preorder',
         '来季モデル先行予約割',
         '来季モデルを今すぐ予約すると 20% OFF。サイズ・フレックス完全指定可能。',
         'PERCENTAGE',
         '2026-04-01 00:00:00+09', '2026-04-30 23:59:59+09',
         TRUE, 400,
         '{"season":"end","target":"advanced","preorder":true,"sizeLock":true}'),

        -- =====================================================================
        -- 🏔 5〜7月：早期受注シーズン（上級者・競技者） ※実質売上ピーク
        -- =====================================================================
        ('camp-may-early-order',
         '来季モデル早期受注会',
         '上級者・デモ・レーサー向けに来季モデルを 25% OFF で早期確保。サイズ・フレックス完全指定。',
         'PERCENTAGE',
         '2026-05-01 00:00:00+09', '2026-07-15 23:59:59+09',
         FALSE, 800,
         '{"season":"early-order","target":"advanced-racer","sizeLock":true,"countdown":true}'),

        ('camp-jun-custom-order',
         'カスタムオーダー対応割',
         'プレート・ビンディング指定のカスタムオーダーで ¥15,000 OFF。レース志向ユーザ向け。',
         'FIXED_AMOUNT',
         '2026-06-01 00:00:00+09', '2026-07-15 23:59:59+09',
         FALSE, 200,
         '{"season":"early-order","target":"racer","customizationLevel":"high"}'),

        ('camp-jul-limited-deadline',
         '数量限定・締切訴求クーポン',
         '人気サイズは ◯月 ◯日締切。「今予約しないと手に入らない」緊急性を訴求。',
         'PERCENTAGE',
         '2026-07-01 00:00:00+09', '2026-07-31 23:59:59+09',
         FALSE, 300,
         '{"season":"early-order","target":"advanced","scarcity":true,"deadline":"strict"}'),

        -- =====================================================================
        -- ☀️ 8〜9月：オフシーズン（コア層囲い込み）
        -- =====================================================================
        ('camp-aug-tuneup-early',
         'チューンナップ早割予約',
         'シーズンイン前のチューンナップ早期予約 20% OFF。コア顧客のリテンション施策。',
         'PERCENTAGE',
         '2026-08-01 00:00:00+09', '2026-09-30 23:59:59+09',
         FALSE, 500,
         '{"season":"off","target":"core","retention":true,"service":"tune-up"}'),

        ('camp-sep-member-exclusive',
         '会員限定先行情報クーポン',
         'メール・会員限定で来季モデル詳細解説 + ¥3,000 OFF コードを配布。',
         'FIXED_AMOUNT',
         '2026-09-01 00:00:00+09', '2026-09-30 23:59:59+09',
         FALSE, 1500,
         '{"season":"off","target":"core","channel":"email-only","distribution":"member-only"}'),

        -- =====================================================================
        -- 🍁 10〜11月：シーズンイン前（初心者・復帰層）
        -- =====================================================================
        ('camp-oct-season-in-fair',
         'シーズンイン準備フェア（送料無料）',
         'ワックス・ビンディング調整無料 + 送料無料。「今年こそ始めよう」訴求。',
         'FREE_SHIPPING',
         '2026-10-01 00:00:00+09', '2026-11-30 23:59:59+09',
         FALSE, 2000,
         '{"season":"pre-in","target":"beginner-returning","freeServices":["wax","binding-adjust"]}'),

        ('camp-oct-beginner-starter',
         '初心者スターター割',
         '初購入セット 15% OFF。シーズンイン前の最後の決断を後押し。',
         'PERCENTAGE',
         '2026-10-15 00:00:00+09', '2026-11-30 23:59:59+09',
         FALSE, 1000,
         '{"season":"pre-in","target":"beginner","firstPurchase":true}'),

        ('camp-nov-last-year-clearance',
         '昨季モデル最終放出',
         '昨季モデルを最大 40% OFF。価格重視層・初心者にも訴求。',
         'PERCENTAGE',
         '2026-11-01 00:00:00+09', '2026-11-30 23:59:59+09',
         FALSE, 1500,
         '{"season":"pre-in","target":"price-sensitive","intent":"clearance"}'),

        -- =====================================================================
        -- 🎄 12月：シーズン序盤・ギフト需要
        -- =====================================================================
        ('camp-dec-same-day-ship',
         '即納保証キャンペーン（送料無料）',
         '在庫品は即日出荷 + 送料無料。「明日届く安心感」を訴求。',
         'FREE_SHIPPING',
         '2026-12-01 00:00:00+09', '2026-12-31 23:59:59+09',
         FALSE, 3000,
         '{"season":"early-in","priority":"speed","sameDayShipping":true}'),

        ('camp-dec-gift-accessory',
         'ギフト向けアクセサリー割',
         'ゴーグル・ヘルメット・グローブを 15% OFF。年末年始のギフト需要を狙う。',
         'PERCENTAGE',
         '2026-12-01 00:00:00+09', '2026-12-25 23:59:59+09',
         FALSE, 1200,
         '{"season":"early-in","occasion":"gift","categories":["goggles","helmet","gloves"]}'),

        ('camp-dec-year-end-shipping',
         '年内発送保証クーポン',
         '12/28 までのご注文で年内発送保証 + ¥2,000 OFF。',
         'FIXED_AMOUNT',
         '2026-12-15 00:00:00+09', '2026-12-28 23:59:59+09',
         FALSE, 800,
         '{"season":"early-in","guarantee":"year-end-shipping","deadline":"2026-12-28"}')
    ) AS t(slug, name, description, campaign_type, start_date, end_date, is_active, max_coupons, rules)
)
INSERT INTO campaigns (
    id, name, description, campaign_type, start_date, end_date, is_active, max_coupons, generated_coupons, rules, created_at, updated_at
)
SELECT
    id, name, description, campaign_type, start_date, end_date, is_active, max_coupons, 0, rules,
    NOW(), NOW()
FROM src
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    campaign_type = EXCLUDED.campaign_type,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    is_active = EXCLUDED.is_active,
    max_coupons = EXCLUDED.max_coupons,
    rules = EXCLUDED.rules,
    updated_at = NOW();

-- -----------------------------------------------------------------------------
-- Section 2: 各キャンペーンに 3 種類の代表クーポンを発行
--   コード規約: SKI-<MMM>-<TAG>-<XX>
--   例: SKI-JAN-START-01
-- -----------------------------------------------------------------------------
WITH camp_lookup AS (
    SELECT id AS campaign_id, name,
           ('00000000-0000-0000-0000-' || substr(md5(slug), 1, 12))::uuid AS expected_id,
           slug
    FROM (VALUES
        ('camp-jan-beginner-set'), ('camp-jan-school-discount'), ('camp-feb-rental-graduate'),
        ('camp-mar-season-end-sale'), ('camp-mar-test-ride-coupon'), ('camp-apr-next-season-preorder'),
        ('camp-may-early-order'), ('camp-jun-custom-order'), ('camp-jul-limited-deadline'),
        ('camp-aug-tuneup-early'), ('camp-sep-member-exclusive'),
        ('camp-oct-season-in-fair'), ('camp-oct-beginner-starter'), ('camp-nov-last-year-clearance'),
        ('camp-dec-same-day-ship'), ('camp-dec-gift-accessory'), ('camp-dec-year-end-shipping')
    ) AS s(slug)
    JOIN campaigns c ON c.id = ('00000000-0000-0000-0000-' || substr(md5(s.slug), 1, 12))::uuid
),
coupon_src AS (
    SELECT * FROM (VALUES
        -- 1〜2月 -----------------------------------------------------------------
        ('camp-jan-beginner-set',     'SKI-JAN-START-01', 'FIXED_AMOUNT', 'FIXED',     10000.00,  50000.00, NULL,    300, '2026-02-28 23:59:59+09'),
        ('camp-jan-beginner-set',     'SKI-JAN-START-02', 'FIXED_AMOUNT', 'FIXED',     15000.00,  80000.00, NULL,    150, '2026-02-28 23:59:59+09'),
        ('camp-jan-beginner-set',     'SKI-JAN-START-03', 'FREE_SHIPPING','FIXED',      1500.00,  20000.00, NULL,    500, '2026-02-28 23:59:59+09'),

        ('camp-jan-school-discount',  'SKI-JAN-SCHOOL-01','PERCENTAGE',   'PERCENTAGE',   10.00,  10000.00, 20000.00, 800, '2026-02-28 23:59:59+09'),
        ('camp-jan-school-discount',  'SKI-JAN-SCHOOL-02','PERCENTAGE',   'PERCENTAGE',   12.00,  20000.00, 30000.00, 200, '2026-02-28 23:59:59+09'),
        ('camp-jan-school-discount',  'SKI-JAN-SCHOOL-VIP','PERCENTAGE',  'PERCENTAGE',   15.00,  30000.00, 45000.00,  50, '2026-02-28 23:59:59+09'),

        ('camp-feb-rental-graduate',  'SKI-FEB-GRAD-01',  'PERCENTAGE',   'PERCENTAGE',   15.00,  30000.00, 30000.00, 500, '2026-02-28 23:59:59+09'),
        ('camp-feb-rental-graduate',  'SKI-FEB-GRAD-02',  'FIXED_AMOUNT', 'FIXED',      8000.00,  40000.00, NULL,    250, '2026-02-28 23:59:59+09'),
        ('camp-feb-rental-graduate',  'SKI-FEB-GRAD-03',  'FREE_SHIPPING','FIXED',      1500.00,  10000.00, NULL,    300, '2026-02-28 23:59:59+09'),

        -- 3〜4月（現在アクティブ）-----------------------------------------------
        ('camp-mar-season-end-sale',  'SKI-MAR-END-30',   'PERCENTAGE',   'PERCENTAGE',   30.00,  20000.00, 60000.00,1000, '2026-04-30 23:59:59+09'),
        ('camp-mar-season-end-sale',  'SKI-MAR-END-20',   'PERCENTAGE',   'PERCENTAGE',   20.00,  10000.00, 40000.00, 800, '2026-04-30 23:59:59+09'),
        ('camp-mar-season-end-sale',  'SKI-MAR-END-FREE', 'FREE_SHIPPING','FIXED',      1500.00,  15000.00, NULL,    500, '2026-04-30 23:59:59+09'),

        ('camp-mar-test-ride-coupon', 'SKI-MAR-TEST-01',  'FIXED_AMOUNT', 'FIXED',      5000.00,  50000.00, NULL,    400, '2026-04-30 23:59:59+09'),
        ('camp-mar-test-ride-coupon', 'SKI-MAR-TEST-02',  'FIXED_AMOUNT', 'FIXED',      8000.00,  80000.00, NULL,    150, '2026-04-30 23:59:59+09'),
        ('camp-mar-test-ride-coupon', 'SKI-MAR-TEST-VIP', 'FIXED_AMOUNT', 'FIXED',     12000.00, 120000.00, NULL,     50, '2026-04-30 23:59:59+09'),

        ('camp-apr-next-season-preorder','SKI-APR-PRE-20','PERCENTAGE',   'PERCENTAGE',   20.00,  50000.00, 80000.00, 300, '2026-04-30 23:59:59+09'),
        ('camp-apr-next-season-preorder','SKI-APR-PRE-25','PERCENTAGE',   'PERCENTAGE',   25.00, 100000.00,120000.00, 100, '2026-04-30 23:59:59+09'),
        ('camp-apr-next-season-preorder','SKI-APR-PRE-FREE','FREE_SHIPPING','FIXED',    1500.00,  20000.00, NULL,    400, '2026-04-30 23:59:59+09'),

        -- 5〜7月 -----------------------------------------------------------------
        ('camp-may-early-order',      'SKI-MAY-EARLY-25', 'PERCENTAGE',   'PERCENTAGE',   25.00,  60000.00,100000.00, 600, '2026-07-15 23:59:59+09'),
        ('camp-may-early-order',      'SKI-MAY-EARLY-30', 'PERCENTAGE',   'PERCENTAGE',   30.00, 120000.00,150000.00, 200, '2026-07-15 23:59:59+09'),
        ('camp-may-early-order',      'SKI-MAY-EARLY-FREE','FREE_SHIPPING','FIXED',     1500.00,  30000.00, NULL,    400, '2026-07-15 23:59:59+09'),

        ('camp-jun-custom-order',     'SKI-JUN-CUSTOM-01','FIXED_AMOUNT', 'FIXED',     15000.00, 100000.00, NULL,    150, '2026-07-15 23:59:59+09'),
        ('camp-jun-custom-order',     'SKI-JUN-CUSTOM-02','FIXED_AMOUNT', 'FIXED',     25000.00, 200000.00, NULL,     50, '2026-07-15 23:59:59+09'),
        ('camp-jun-custom-order',     'SKI-JUN-CUSTOM-VIP','PERCENTAGE',  'PERCENTAGE',   15.00, 150000.00, 60000.00,  30, '2026-07-15 23:59:59+09'),

        ('camp-jul-limited-deadline', 'SKI-JUL-LIMIT-30', 'PERCENTAGE',   'PERCENTAGE',   30.00,  80000.00,120000.00, 200, '2026-07-31 23:59:59+09'),
        ('camp-jul-limited-deadline', 'SKI-JUL-LIMIT-20', 'PERCENTAGE',   'PERCENTAGE',   20.00,  40000.00, 50000.00, 100, '2026-07-31 23:59:59+09'),
        ('camp-jul-limited-deadline', 'SKI-JUL-LIMIT-FREE','FREE_SHIPPING','FIXED',     1500.00,  20000.00, NULL,    150, '2026-07-31 23:59:59+09'),

        -- 8〜9月 -----------------------------------------------------------------
        ('camp-aug-tuneup-early',     'SKI-AUG-TUNE-20',  'PERCENTAGE',   'PERCENTAGE',   20.00,   5000.00, 10000.00, 300, '2026-09-30 23:59:59+09'),
        ('camp-aug-tuneup-early',     'SKI-AUG-TUNE-30',  'PERCENTAGE',   'PERCENTAGE',   30.00,  10000.00, 15000.00, 100, '2026-09-30 23:59:59+09'),
        ('camp-aug-tuneup-early',     'SKI-AUG-TUNE-MEMBER','FIXED_AMOUNT','FIXED',      3000.00,  10000.00, NULL,    200, '2026-09-30 23:59:59+09'),

        ('camp-sep-member-exclusive', 'SKI-SEP-MEM-01',   'FIXED_AMOUNT', 'FIXED',      3000.00,  20000.00, NULL,   1000, '2026-09-30 23:59:59+09'),
        ('camp-sep-member-exclusive', 'SKI-SEP-MEM-02',   'FIXED_AMOUNT', 'FIXED',      5000.00,  40000.00, NULL,    400, '2026-09-30 23:59:59+09'),
        ('camp-sep-member-exclusive', 'SKI-SEP-MEM-VIP',  'PERCENTAGE',   'PERCENTAGE',   12.00,  60000.00, 30000.00, 100, '2026-09-30 23:59:59+09'),

        -- 10〜11月 ---------------------------------------------------------------
        ('camp-oct-season-in-fair',   'SKI-OCT-FAIR-FREE','FREE_SHIPPING','FIXED',      1500.00,  10000.00, NULL,   1500, '2026-11-30 23:59:59+09'),
        ('camp-oct-season-in-fair',   'SKI-OCT-FAIR-WAX', 'FIXED_AMOUNT', 'FIXED',      2000.00,  15000.00, NULL,    400, '2026-11-30 23:59:59+09'),
        ('camp-oct-season-in-fair',   'SKI-OCT-FAIR-BIND','FIXED_AMOUNT', 'FIXED',      3000.00,  25000.00, NULL,    100, '2026-11-30 23:59:59+09'),

        ('camp-oct-beginner-starter', 'SKI-OCT-BEG-15',   'PERCENTAGE',   'PERCENTAGE',   15.00,  30000.00, 30000.00, 700, '2026-11-30 23:59:59+09'),
        ('camp-oct-beginner-starter', 'SKI-OCT-BEG-20',   'PERCENTAGE',   'PERCENTAGE',   20.00,  60000.00, 50000.00, 200, '2026-11-30 23:59:59+09'),
        ('camp-oct-beginner-starter', 'SKI-OCT-BEG-FREE', 'FREE_SHIPPING','FIXED',      1500.00,  20000.00, NULL,    100, '2026-11-30 23:59:59+09'),

        ('camp-nov-last-year-clearance','SKI-NOV-CLR-30', 'PERCENTAGE',   'PERCENTAGE',   30.00,  10000.00, 50000.00,1000, '2026-11-30 23:59:59+09'),
        ('camp-nov-last-year-clearance','SKI-NOV-CLR-40', 'PERCENTAGE',   'PERCENTAGE',   40.00,  30000.00,100000.00, 400, '2026-11-30 23:59:59+09'),
        ('camp-nov-last-year-clearance','SKI-NOV-CLR-FREE','FREE_SHIPPING','FIXED',     1500.00,  15000.00, NULL,    100, '2026-11-30 23:59:59+09'),

        -- 12月 -------------------------------------------------------------------
        ('camp-dec-same-day-ship',    'SKI-DEC-SHIP-FREE','FREE_SHIPPING','FIXED',      1500.00,   5000.00, NULL,   2500, '2026-12-31 23:59:59+09'),
        ('camp-dec-same-day-ship',    'SKI-DEC-SHIP-NEXT','FIXED_AMOUNT', 'FIXED',      1000.00,  10000.00, NULL,    400, '2026-12-31 23:59:59+09'),
        ('camp-dec-same-day-ship',    'SKI-DEC-SHIP-VIP', 'FIXED_AMOUNT', 'FIXED',      3000.00,  30000.00, NULL,    100, '2026-12-31 23:59:59+09'),

        ('camp-dec-gift-accessory',   'SKI-DEC-GIFT-15',  'PERCENTAGE',   'PERCENTAGE',   15.00,   8000.00, 15000.00, 800, '2026-12-25 23:59:59+09'),
        ('camp-dec-gift-accessory',   'SKI-DEC-GIFT-20',  'PERCENTAGE',   'PERCENTAGE',   20.00,  20000.00, 25000.00, 300, '2026-12-25 23:59:59+09'),
        ('camp-dec-gift-accessory',   'SKI-DEC-GIFT-FREE','FREE_SHIPPING','FIXED',      1500.00,   8000.00, NULL,    100, '2026-12-25 23:59:59+09'),

        ('camp-dec-year-end-shipping','SKI-DEC-YEND-2K',  'FIXED_AMOUNT', 'FIXED',      2000.00,  15000.00, NULL,    600, '2026-12-28 23:59:59+09'),
        ('camp-dec-year-end-shipping','SKI-DEC-YEND-3K',  'FIXED_AMOUNT', 'FIXED',      3000.00,  25000.00, NULL,    150, '2026-12-28 23:59:59+09'),
        ('camp-dec-year-end-shipping','SKI-DEC-YEND-FREE','FREE_SHIPPING','FIXED',      1500.00,  10000.00, NULL,     50, '2026-12-28 23:59:59+09')
    ) AS c(slug, code, coupon_type, discount_type, discount_value, minimum_amount, maximum_discount, usage_limit, expires_at)
)
INSERT INTO coupons (
    id, campaign_id, code, coupon_type, discount_value, discount_type,
    minimum_amount, maximum_discount, usage_limit, used_count, is_active,
    expires_at, created_at, updated_at, version
)
SELECT
    ('00000000-0000-0000-0000-' || substr(md5('coupon-' || c.code), 1, 12))::uuid,
    ('00000000-0000-0000-0000-' || substr(md5(c.slug), 1, 12))::uuid,
    c.code,
    c.coupon_type,
    c.discount_value,
    c.discount_type,
    c.minimum_amount,
    c.maximum_discount,
    c.usage_limit,
    0,
    TRUE,
    c.expires_at::timestamptz,
    NOW(),
    NOW(),
    0
FROM coupon_src c
ON CONFLICT (code) DO UPDATE SET
    discount_value = EXCLUDED.discount_value,
    discount_type = EXCLUDED.discount_type,
    minimum_amount = EXCLUDED.minimum_amount,
    maximum_discount = EXCLUDED.maximum_discount,
    usage_limit = EXCLUDED.usage_limit,
    expires_at = EXCLUDED.expires_at,
    is_active = TRUE,
    updated_at = NOW();

-- generated_coupons を実際の coupons 件数に合わせて更新
UPDATE campaigns c
SET generated_coupons = sub.cnt,
    updated_at = NOW()
FROM (
    SELECT campaign_id, COUNT(*) AS cnt
    FROM coupons
    GROUP BY campaign_id
) sub
WHERE c.id = sub.campaign_id;

COMMIT;

-- -----------------------------------------------------------------------------
-- 確認
-- -----------------------------------------------------------------------------
\echo '=== campaigns summary ==='
SELECT
    to_char(start_date AT TIME ZONE 'Asia/Tokyo', 'MM-DD') AS start_md,
    to_char(end_date   AT TIME ZONE 'Asia/Tokyo', 'MM-DD') AS end_md,
    is_active,
    campaign_type,
    generated_coupons,
    name
FROM campaigns
ORDER BY start_date;

\echo '=== coupon counts per campaign ==='
SELECT c.name, COUNT(co.id) AS coupon_count
FROM campaigns c LEFT JOIN coupons co ON co.campaign_id = c.id
GROUP BY c.name, c.start_date
ORDER BY c.start_date;
