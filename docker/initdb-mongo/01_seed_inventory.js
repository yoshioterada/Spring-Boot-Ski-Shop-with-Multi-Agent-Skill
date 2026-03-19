// =============================================================================
// MongoDB Seed Script — Ski Shop Inventory (skishop_inventory)
// 実在するスキー用品メーカー・製品名をベースにしたサンプルデータ
// カテゴリ 7 種 × 各 30 件 = 合計 210 商品
// =============================================================================

const db = db.getSiblingDB("skishop_inventory");

// ---------------------------------------------------------------------------
// 0. 既存データがある場合はスキップ
// ---------------------------------------------------------------------------
if (db.products.countDocuments() > 0) {
  print(">>> products collection already has data – skipping seed.");
  quit();
}

const now = new Date();

// ---------------------------------------------------------------------------
// 1. カテゴリ登録
// ---------------------------------------------------------------------------
const categories = [
  { _id: "cat-ski",     name: "スキー板",     description: "各種スキー板（オールマウンテン・レーシング・フリースタイル）",     sortOrder: 1 },
  { _id: "cat-boots",   name: "スキーブーツ", description: "各種スキーブーツ（レーシング・オールマウンテン・バックカントリー）", sortOrder: 2 },
  { _id: "cat-wear",    name: "スキーウェア", description: "スキージャケット・パンツ・ワンピース",                         sortOrder: 3 },
  { _id: "cat-gloves",  name: "グローブ",     description: "スキー用グローブ・ミトン",                                   sortOrder: 4 },
  { _id: "cat-goggles", name: "ゴーグル",     description: "スキー・スノーボード用ゴーグル",                             sortOrder: 5 },
  { _id: "cat-helmets", name: "ヘルメット",   description: "スキー・スノーボード用ヘルメット",                           sortOrder: 6 },
  { _id: "cat-poles",   name: "ポール",       description: "スキーポール・ストック",                                     sortOrder: 7 },
];

categories.forEach(function (c) {
  c.childIds  = [];
  c.active    = true;
  c.createdAt = now;
  c.updatedAt = now;
  c._class    = "com.example.skishop.inventory.model.Category";
});

db.categories.insertMany(categories);
print(">>> Inserted " + categories.length + " categories");

// ---------------------------------------------------------------------------
// Helper — 商品ドキュメント生成
// ---------------------------------------------------------------------------
function product(sku, name, brand, categoryId, price, stock, desc, attrs, tags) {
  return {
    sku:              sku,
    name:             name,
    description:      desc,
    brand:            brand,
    categoryId:       categoryId,
    attributes:       attrs  || {},
    tags:             tags   || [],
    images:           [],
    regularPrice:     NumberDecimal(String(price)),
    salePrice:        null,
    saleStartDate:    null,
    saleEndDate:      null,
    currency:         "JPY",
    stockQuantity:    stock,
    reservedQuantity: 0,
    locationCode:     "WH-01",
    status:           "ACTIVE",
    createdAt:        now,
    updatedAt:        now,
    _class:           "com.example.skishop.inventory.model.Product"
  };
}

// ---------------------------------------------------------------------------
// 2. スキー板 (30 件)
// ---------------------------------------------------------------------------
const skis = [
  product("SKI-001", "ATOMIC Redster S9i",             "ATOMIC",    "cat-ski",  165000,  8,  "FIS 対応 SL レーシングスキー。高いエッジグリップとクイックなターンレスポンス。",          { length: "165cm", radius: "12.5m", type: "SL" },            ["レーシング","上級者","SL"]),
  product("SKI-002", "ATOMIC Redster X9i",             "ATOMIC",    "cat-ski",  155000, 10,  "GS レーシングモデル。パワフルなターンと安定した高速性能。",                           { length: "175cm", radius: "17m",   type: "GS" },            ["レーシング","上級者","GS"]),
  product("SKI-003", "ATOMIC Bent Chetler 100",        "ATOMIC",    "cat-ski",  110000, 15,  "フリーライド向けオールマウンテンスキー。パウダーからゲレンデまで幅広く対応。",             { length: "180cm", radius: "18m",   type: "フリーライド" },    ["フリーライド","中上級者","パウダー"]),
  product("SKI-004", "SALOMON S/Race FIS GS",          "SALOMON",   "cat-ski",  178000,  6,  "ワールドカップ GS レーシングスキー。最高峰のグリップ力と安定性。",                      { length: "185cm", radius: "21m",   type: "GS" },            ["レーシング","上級者","GS","FIS"]),
  product("SKI-005", "SALOMON QST 98",                 "SALOMON",   "cat-ski",   98000, 18,  "オールマウンテンフリーライドスキー。あらゆるコンディションに対応する万能モデル。",          { length: "176cm", radius: "17m",   type: "オールマウンテン" },["オールマウンテン","中上級者","フリーライド"]),
  product("SKI-006", "SALOMON Stance 96",              "SALOMON",   "cat-ski",   89000, 20,  "オールマウンテンスキー。カービングからパウダーまで快適に楽しめる。",                      { length: "174cm", radius: "16m",   type: "オールマウンテン" },["オールマウンテン","中級者"]),
  product("SKI-007", "HEAD Supershape e-Speed",        "HEAD",      "cat-ski",  148000, 12,  "ハイパフォーマンス基礎スキー。EMC テクノロジーで振動を吸収し滑らかなターンを実現。",       { length: "170cm", radius: "13m",   type: "基礎" },           ["基礎","上級者","カービング"]),
  product("SKI-008", "HEAD World Cup Rebels e-GS",     "HEAD",      "cat-ski",  168000,  7,  "ワールドカップ GS モデル。卓越したエッジグリップと高速安定性。",                        { length: "180cm", radius: "20m",   type: "GS" },            ["レーシング","上級者","GS"]),
  product("SKI-009", "HEAD Kore 93",                   "HEAD",      "cat-ski",  105000, 14,  "軽量フリーライドスキー。グラフェン搭載で軽さとパワーを両立。",                          { length: "177cm", radius: "16.5m", type: "フリーライド" },    ["フリーライド","中上級者","軽量"]),
  product("SKI-010", "ROSSIGNOL Hero Elite ST Ti",     "ROSSIGNOL", "cat-ski",  135000, 11,  "技術選向け基礎スキー。精密なカービングターンが可能。",                                 { length: "167cm", radius: "13m",   type: "基礎" },           ["基礎","上級者","カービング"]),
  product("SKI-011", "ROSSIGNOL Experience 82 Ti",     "ROSSIGNOL", "cat-ski",   88000, 22,  "中級者向けオールマウンテンスキー。快適な操作性とターン性能のバランスが秀逸。",             { length: "172cm", radius: "14m",   type: "オールマウンテン" },["オールマウンテン","中級者"]),
  product("SKI-012", "ROSSIGNOL Sender Ti",            "ROSSIGNOL", "cat-ski",  115000, 10,  "チタン補強のフリーライドスキー。パウダーでの浮力とハードパックでの安定性を両立。",          { length: "182cm", radius: "19m",   type: "フリーライド" },    ["フリーライド","上級者","パウダー"]),
  product("SKI-013", "VOLKL Racetiger SL",             "VOLKL",     "cat-ski",  145000,  9,  "SL レーシングスキー。クイックなエッジ切り替えと強力なグリップ。",                        { length: "165cm", radius: "12m",   type: "SL" },            ["レーシング","上級者","SL"]),
  product("SKI-014", "VOLKL Deacon 76",                "VOLKL",     "cat-ski",  128000, 13,  "基礎・技術系スキー。高速カービングに最適なフレックスとトーション。",                      { length: "168cm", radius: "14.5m", type: "基礎" },           ["基礎","上級者","カービング"]),
  product("SKI-015", "VOLKL Mantra M6",                "VOLKL",     "cat-ski",  108000, 16,  "フリーライドオールマウンテンスキー。あらゆる地形で頼れるパフォーマンス。",                  { length: "177cm", radius: "17.5m", type: "フリーライド" },    ["フリーライド","オールマウンテン","中上級者"]),
  product("SKI-016", "K2 Disruption 82Ti",             "K2",        "cat-ski",   95000, 17,  "フロントサイド向けオールマウンテンスキー。チタン層で安定したカービングを実現。",            { length: "170cm", radius: "14m",   type: "オールマウンテン" },["オールマウンテン","中上級者","カービング"]),
  product("SKI-017", "K2 Mindbender 99Ti",             "K2",        "cat-ski",  112000, 12,  "フリーライドスキー。チタン補強でハードバーンも攻められる。",                             { length: "179cm", radius: "19m",   type: "フリーライド" },    ["フリーライド","上級者"]),
  product("SKI-018", "K2 Reckoner 102",                "K2",        "cat-ski",   88000, 20,  "ツインチップのフリースタイルスキー。パーク・パイプからパウダーまで楽しめる。",               { length: "177cm", radius: "17m",   type: "フリースタイル" },  ["フリースタイル","パーク","中上級者"]),
  product("SKI-019", "FISCHER RC4 The Curv GT 80",     "FISCHER",   "cat-ski",  140000, 10,  "基礎・デモスキー。高精度カービングを追求したハイエンドモデル。",                          { length: "171cm", radius: "14m",   type: "基礎" },           ["基礎","上級者","カービング"]),
  product("SKI-020", "FISCHER Ranger 96",              "FISCHER",   "cat-ski",   99000, 14,  "フリーツーリングスキー。軽量で登りも滑りも楽しめる。",                                   { length: "178cm", radius: "17m",   type: "フリーライド" },    ["フリーライド","ツーリング","中上級者"]),
  product("SKI-021", "NORDICA Dobermann SLR",          "NORDICA",   "cat-ski",  138000,  8,  "SL レーシングスキー。正確かつアグレッシブなターンを可能にするレース専用設計。",              { length: "165cm", radius: "11.5m", type: "SL" },            ["レーシング","上級者","SL"]),
  product("SKI-022", "NORDICA Enforcer 100",           "NORDICA",   "cat-ski",  105000, 15,  "フリーライドオールマウンテンスキー。どんなコンディションでも力強い滑りを実現。",              { length: "179cm", radius: "18.5m", type: "フリーライド" },    ["フリーライド","オールマウンテン","中上級者"]),
  product("SKI-023", "BLIZZARD Brahma 88",             "BLIZZARD",  "cat-ski",   98000, 18,  "オールマウンテンスキー。カーボンフリップコアで軽量かつパワフル。",                         { length: "173cm", radius: "15m",   type: "オールマウンテン" },["オールマウンテン","中上級者"]),
  product("SKI-024", "BLIZZARD Rustler 9",             "BLIZZARD",  "cat-ski",  108000, 11,  "フリーライドスキー。パウダーでの浮力と整地での操作性を両立。",                            { length: "180cm", radius: "18m",   type: "フリーライド" },    ["フリーライド","パウダー","中上級者"]),
  product("SKI-025", "ELAN Wingman 82 Ti",             "ELAN",      "cat-ski",   85000, 20,  "オールマウンテンスキー。安定感のある乗り味で幅広いスキーヤーに対応。",                     { length: "170cm", radius: "14m",   type: "オールマウンテン" },["オールマウンテン","中級者"]),
  product("SKI-026", "ELAN Ripstick 96",               "ELAN",      "cat-ski",   95000, 16,  "フリーライドスキー。軽量で取り回しが良くバックカントリーにも最適。",                       { length: "176cm", radius: "16m",   type: "フリーライド" },    ["フリーライド","バックカントリー","中上級者"]),
  product("SKI-027", "DYNASTAR Speed Zone 10 Ti",      "DYNASTAR",  "cat-ski",  118000, 12,  "ハイスピードカービングスキー。チタンプレート搭載で高い安定性。",                          { length: "172cm", radius: "14m",   type: "基礎" },           ["基礎","上級者","カービング"]),
  product("SKI-028", "OGASAKA TC-SS",                  "OGASAKA",   "cat-ski",  132000,  9,  "技術選用基礎スキー。日本製ならではの精緻な作りと繊細なレスポンス。",                      { length: "165cm", radius: "12.5m", type: "基礎" },           ["基礎","技術選","上級者"]),
  product("SKI-029", "OGASAKA Unity U-ES/1",           "OGASAKA",   "cat-ski",  115000, 13,  "中上級者向け基礎スキー。スムーズなターン導入が特徴。",                                   { length: "168cm", radius: "13m",   type: "基礎" },           ["基礎","中上級者"]),
  product("SKI-030", "LINE Blade Optic 96",            "LINE",      "cat-ski",   92000, 15,  "フリーライドスキー。エッジグリップとフロート感を高次元で両立。",                           { length: "178cm", radius: "17m",   type: "フリーライド" },    ["フリーライド","中上級者"]),
];

// ---------------------------------------------------------------------------
// 3. スキーブーツ (30 件)
// ---------------------------------------------------------------------------
const boots = [
  product("BTS-001", "ATOMIC Redster CS 130",            "ATOMIC",    "cat-boots", 98000, 10, "SL レーシングブーツ。タイトフィットで精密なエッジコントロール。",                          { flex: "130", lastWidth: "97mm",  buckles: "4" }, ["レーシング","上級者","SL"]),
  product("BTS-002", "ATOMIC Hawx Ultra 130",            "ATOMIC",    "cat-boots", 88000, 15, "軽量ハイパフォーマンスブーツ。メモリーフィットで快適なフィット感。",                        { flex: "130", lastWidth: "98mm",  buckles: "4" }, ["オールマウンテン","上級者","軽量"]),
  product("BTS-003", "ATOMIC Hawx Prime 120 S",          "ATOMIC",    "cat-boots", 72000, 18, "中上級者向けフリーライドブーツ。快適さとパフォーマンスのバランスが秀逸。",                   { flex: "120", lastWidth: "100mm", buckles: "4" }, ["オールマウンテン","中上級者"]),
  product("BTS-004", "SALOMON S/Pro Alpha 130",          "SALOMON",   "cat-boots", 95000, 12, "レーシング対応ハイパフォーマンスブーツ。カスタムシェルフィットテクノロジー搭載。",            { flex: "130", lastWidth: "97mm",  buckles: "4" }, ["レーシング","上級者"]),
  product("BTS-005", "SALOMON S/Pro Supra BOA 120",      "SALOMON",   "cat-boots", 82000, 16, "BOA フィットシステム搭載。素早い着脱と精密なフィット調整が可能。",                        { flex: "120", lastWidth: "100mm", buckles: "3+BOA" }, ["オールマウンテン","中上級者","BOA"]),
  product("BTS-006", "SALOMON S/Pro MV 100",             "SALOMON",   "cat-boots", 58000, 22, "中級者向けミディアムボリュームブーツ。長時間の快適さを追求。",                            { flex: "100", lastWidth: "102mm", buckles: "4" }, ["オールマウンテン","中級者"]),
  product("BTS-007", "HEAD Raptor 140 RS",               "HEAD",      "cat-boots",105000,  8, "トップレーシングブーツ。ワールドカップでも使用されるハイエンドモデル。",                    { flex: "140", lastWidth: "95mm",  buckles: "4" }, ["レーシング","上級者","FIS"]),
  product("BTS-008", "HEAD Formula 130",                 "HEAD",      "cat-boots", 78000, 14, "オールマウンテンハイパフォーマンスブーツ。快適さを保ちながら高い操作性を発揮。",              { flex: "130", lastWidth: "100mm", buckles: "4" }, ["オールマウンテン","上級者"]),
  product("BTS-009", "HEAD Edge LYT 100",                "HEAD",      "cat-boots", 52000, 25, "軽量コンフォートブーツ。初中級者でも扱いやすい柔軟なフレックス。",                        { flex: "100", lastWidth: "104mm", buckles: "4" }, ["オールマウンテン","初中級者","軽量"]),
  product("BTS-010", "ROSSIGNOL Hero World Cup ZJ+",     "ROSSIGNOL", "cat-boots",110000,  6, "FIS ワールドカップレーシングブーツ。最高峰のパワー伝達と反応性。",                         { flex: "150", lastWidth: "93mm",  buckles: "4" }, ["レーシング","上級者","FIS"]),
  product("BTS-011", "ROSSIGNOL Speed 120",              "ROSSIGNOL", "cat-boots", 68000, 16, "スポーツ系パフォーマンスブーツ。レースからフリーライドまでカバー。",                       { flex: "120", lastWidth: "100mm", buckles: "4" }, ["オールマウンテン","上級者"]),
  product("BTS-012", "ROSSIGNOL Alltrack Pro 120 LT",    "ROSSIGNOL", "cat-boots", 75000, 14, "ハイクモード付きフリーツーリングブーツ。ゲレンデ外でも活躍。",                            { flex: "120", lastWidth: "100mm", buckles: "4" }, ["フリーライド","バックカントリー","上級者"]),
  product("BTS-013", "NORDICA Dobermann GP 130",         "NORDICA",   "cat-boots", 92000, 10, "レーシングブーツ。高い剛性とダイレクトなパワー伝達。",                                  { flex: "130", lastWidth: "96mm",  buckles: "4" }, ["レーシング","上級者"]),
  product("BTS-014", "NORDICA Speedmachine 3 130",       "NORDICA",   "cat-boots", 85000, 13, "ハイパフォーマンスブーツ。コルクフィットインナーで足型に馴染む。",                        { flex: "130", lastWidth: "100mm", buckles: "4" }, ["オールマウンテン","上級者"]),
  product("BTS-015", "NORDICA Speedmachine 3 110",       "NORDICA",   "cat-boots", 62000, 20, "中上級者向けオールラウンドブーツ。バランスの良いフレックスと快適性。",                     { flex: "110", lastWidth: "102mm", buckles: "4" }, ["オールマウンテン","中上級者"]),
  product("BTS-016", "TECNICA Mach1 LV 130",             "TECNICA",   "cat-boots", 90000, 11, "ナローフィット上級者ブーツ。C.A.S. カスタマイズ対応でジャストフィット。",                  { flex: "130", lastWidth: "98mm",  buckles: "4" }, ["レーシング","上級者","ナロー"]),
  product("BTS-017", "TECNICA Mach Sport HV 120",        "TECNICA",   "cat-boots", 65000, 17, "ワイドフィットスポーツブーツ。幅広い足にも快適にフィット。",                              { flex: "120", lastWidth: "103mm", buckles: "4" }, ["オールマウンテン","中上級者","ワイド"]),
  product("BTS-018", "TECNICA Cochise 130 DYN",          "TECNICA",   "cat-boots", 88000, 12, "バックカントリーブーツ。ハイクモード搭載で登攀も快適。",                                 { flex: "130", lastWidth: "99mm",  buckles: "3" }, ["バックカントリー","ツーリング","上級者"]),
  product("BTS-019", "LANGE RS 130",                     "LANGE",     "cat-boots", 95000, 10, "レーシングブーツの定番。正確なエッジングと高いレスポンス。",                              { flex: "130", lastWidth: "97mm",  buckles: "4" }, ["レーシング","上級者"]),
  product("BTS-020", "LANGE XT3 130 LV",                 "LANGE",     "cat-boots", 85000, 12, "軽量フリーツーリングブーツ。ダウンヒルパフォーマンスとハイク性能を両立。",                  { flex: "130", lastWidth: "97mm",  buckles: "4" }, ["バックカントリー","ツーリング","上級者"]),
  product("BTS-021", "DALBELLO DRS 130",                 "DALBELLO",  "cat-boots", 88000, 10, "レーシングブーツ。高い剛性と優れたパワー伝達で攻めの滑りに対応。",                        { flex: "130", lastWidth: "96mm",  buckles: "4" }, ["レーシング","上級者"]),
  product("BTS-022", "DALBELLO Lupo AX 120",             "DALBELLO",  "cat-boots", 78000, 14, "ツーリングブーツ。軽量設計でハイクアップも軽快に。",                                     { flex: "120", lastWidth: "99mm",  buckles: "3" }, ["バックカントリー","ツーリング","中上級者"]),
  product("BTS-023", "FISCHER RC4 The Curv 130",         "FISCHER",   "cat-boots", 92000, 10, "ハイパフォーマンスブーツ。バキュームフィットで自分だけのフィット感。",                      { flex: "130", lastWidth: "97mm",  buckles: "4" }, ["レーシング","基礎","上級者"]),
  product("BTS-024", "FISCHER RC One 110",               "FISCHER",   "cat-boots", 55000, 20, "中級者向けスポーツブーツ。軽量で扱いやすいフレックス設計。",                              { flex: "110", lastWidth: "102mm", buckles: "4" }, ["オールマウンテン","中級者"]),
  product("BTS-025", "K2 Recon Pro 130",                 "K2",        "cat-boots", 82000, 12, "ハイパフォーマンスフリーライドブーツ。パワフルかつ快適な滑りを提供。",                     { flex: "130", lastWidth: "98mm",  buckles: "4" }, ["フリーライド","上級者"]),
  product("BTS-026", "K2 BFC 100",                       "K2",        "cat-boots", 48000, 22, "ワイドフィットコンフォートブーツ。長時間でも快適に過ごせる設計。",                         { flex: "100", lastWidth: "103mm", buckles: "4" }, ["オールマウンテン","中級者","ワイド"]),
  product("BTS-027", "FULL TILT Descendant 100",         "FULL TILT", "cat-boots", 52000, 18, "3 ピースデザインブーツ。フリースタイルスキーヤーに人気の柔軟なフレックス。",                { flex: "100", lastWidth: "102mm", buckles: "3" }, ["フリースタイル","パーク","中級者"]),
  product("BTS-028", "REXXAM R-EVO 130M",                "REXXAM",    "cat-boots", 88000, 11, "日本製レーシングブーツ。日本人の足型に合わせた専用ラスト設計。",                           { flex: "130", lastWidth: "98mm",  buckles: "4" }, ["レーシング","基礎","上級者"]),
  product("BTS-029", "REXXAM XX-97",                     "REXXAM",    "cat-boots", 68000, 15, "日本製コンフォートブーツ。幅広の足にも対応するワイドラスト。",                            { flex: "97",  lastWidth: "104mm", buckles: "4" }, ["オールマウンテン","中級者","ワイド"]),
  product("BTS-030", "SCARPA Maestrale RS",              "SCARPA",    "cat-boots", 95000, 10, "バックカントリーツーリングブーツ。軽量でハイク性能に優れたモデル。",                       { flex: "125", lastWidth: "100mm", buckles: "3+BOA" }, ["バックカントリー","ツーリング","上級者"]),
];

// ---------------------------------------------------------------------------
// 4. スキーウェア (30 件)
// ---------------------------------------------------------------------------
const wear = [
  product("WER-001", "DESCENTE S.I.O ジャケット",                    "DESCENTE",       "cat-wear", 110000,  8, "技術選モデルジャケット。高い防水透湿性と動きやすさを両立。",                       { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","上級者","技術選"]),
  product("WER-002", "DESCENTE スイスレプリカ ジャケット",              "DESCENTE",       "cat-wear",  98000, 10, "スイスチーム着用モデルのレプリカジャケット。スタイリッシュなデザイン。",               { waterproof: "20000mm", breathability: "20000g", size: "L" }, ["ジャケット","レプリカ"]),
  product("WER-003", "DESCENTE S.I.O パンツ",                        "DESCENTE",       "cat-wear",  68000, 12, "技術選モデルパンツ。ストレッチ素材で動きを妨げない。",                            { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["パンツ","上級者","技術選"]),
  product("WER-004", "PHENIX Norway Team ジャケット",                 "PHENIX",         "cat-wear", 125000,  6, "ノルウェーチーム公式モデル。最高峰の防水透湿性能と保温性。",                       { waterproof: "30000mm", breathability: "25000g", size: "L" }, ["ジャケット","レプリカ","チームモデル"]),
  product("WER-005", "PHENIX Thunderbolt ジャケット",                 "PHENIX",         "cat-wear",  78000, 14, "ハイパフォーマンスフリーライドジャケット。軽量かつ高機能。",                        { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","フリーライド"]),
  product("WER-006", "PHENIX Thunderbolt パンツ",                     "PHENIX",         "cat-wear",  55000, 16, "フリーライドパンツ。耐久性のある素材と快適なフィット感。",                         { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["パンツ","フリーライド"]),
  product("WER-007", "GOLDWIN G-Bliss ジャケット",                    "GOLDWIN",        "cat-wear", 135000,  5, "プレミアムスキージャケット。GORE-TEX 3L 採用で最高レベルの防水透湿。",             { waterproof: "GORE-TEX", breathability: "GORE-TEX", size: "M" }, ["ジャケット","GORE-TEX","プレミアム"]),
  product("WER-008", "GOLDWIN 2トーンカラー フーデッドジャケット",       "GOLDWIN",        "cat-wear",  88000,  9, "スタイリッシュな 2 トーンカラー。高い防水性と洗練されたデザイン。",                  { waterproof: "20000mm", breathability: "40000g", size: "L" }, ["ジャケット","スタイリッシュ"]),
  product("WER-009", "GOLDWIN G-Bliss パンツ",                        "GOLDWIN",        "cat-wear",  85000,  8, "プレミアムスキーパンツ。GORE-TEX 採用で快適な着心地。",                           { waterproof: "GORE-TEX", breathability: "GORE-TEX", size: "M" }, ["パンツ","GORE-TEX","プレミアム"]),
  product("WER-010", "MIZUNO フリースキー ジャケット",                  "MIZUNO",         "cat-wear",  58000, 18, "高い運動性能と保温性を両立したスキージャケット。",                                { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","フリースキー"]),
  product("WER-011", "MIZUNO スキーパンツ",                            "MIZUNO",         "cat-wear",  42000, 20, "動きやすさを追求したスキーパンツ。ブレスサーモ搭載で暖かい。",                     { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["パンツ","ブレスサーモ"]),
  product("WER-012", "THE NORTH FACE Summit L5 ジャケット",           "THE NORTH FACE", "cat-wear", 148000,  4, "アルパイン向けハードシェルジャケット。FUTURELIGHT 搭載の最高峰モデル。",            { waterproof: "FUTURELIGHT", breathability: "FUTURELIGHT", size: "M" }, ["ジャケット","ハードシェル","バックカントリー"]),
  product("WER-013", "THE NORTH FACE Freedom インサレーションジャケット","THE NORTH FACE", "cat-wear",  65000, 14, "保温性の高いインサレーションジャケット。ゲレンデからタウンまで使える。",              { waterproof: "DryVent", breathability: "DryVent", size: "L" }, ["ジャケット","インサレーション"]),
  product("WER-014", "HELLY HANSEN Alpha 4.0 ジャケット",             "HELLY HANSEN",   "cat-wear", 115000,  7, "LIFA INFINITY PRO テクノロジー搭載。PFC フリーの環境配慮型。",                    { waterproof: "LIFA INFINITY", breathability: "LIFA INFINITY", size: "M" }, ["ジャケット","環境配慮","ハイパフォーマンス"]),
  product("WER-015", "HELLY HANSEN Legendary インサレーションパンツ",   "HELLY HANSEN",   "cat-wear",  52000, 16, "保温性と防水性を兼ね備えたスキーパンツ。快適な着心地。",                           { waterproof: "15000mm", breathability: "15000g", size: "M" }, ["パンツ","インサレーション"]),
  product("WER-016", "SPYDER Vanqysh ジャケット",                     "SPYDER",         "cat-wear", 105000,  8, "レーシングインスパイアの本格スキージャケット。高い運動性を確保。",                   { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","レーシング"]),
  product("WER-017", "SPYDER Dare パンツ",                            "SPYDER",         "cat-wear",  62000, 12, "レーシング対応スキーパンツ。スリムフィットで動きやすい。",                          { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["パンツ","レーシング"]),
  product("WER-018", "KJUS Formula ジャケット",                       "KJUS",           "cat-wear", 158000,  3, "スイス発プレミアムスキージャケット。洗練されたデザインと最高品質の素材。",             { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","プレミアム","スイス"]),
  product("WER-019", "SALOMON Brilliant ジャケット",                   "SALOMON",        "cat-wear",  52000, 20, "オールラウンドスキージャケット。コストパフォーマンスに優れた定番モデル。",             { waterproof: "20000mm", breathability: "20000g", size: "L" }, ["ジャケット","定番","コスパ"]),
  product("WER-020", "SALOMON Brilliant パンツ",                       "SALOMON",        "cat-wear",  38000, 22, "定番スキーパンツ。防水透湿性と快適さのバランスが良い。",                           { waterproof: "20000mm", breathability: "20000g", size: "L" }, ["パンツ","定番","コスパ"]),
  product("WER-021", "OAKLEY TNP Lined Shell ジャケット",             "OAKLEY",         "cat-wear",  68000, 13, "フリースタイル向けジャケット。大胆なデザインと高い機能性。",                       { waterproof: "15000mm", breathability: "10000g", size: "L" }, ["ジャケット","フリースタイル"]),
  product("WER-022", "ARC'TERYX Sabre ジャケット",                    "ARC'TERYX",      "cat-wear", 145000,  4, "GORE-TEX 搭載のプレミアムシェルジャケット。バックカントリーに最適。",               { waterproof: "GORE-TEX", breathability: "GORE-TEX", size: "M" }, ["ジャケット","GORE-TEX","バックカントリー"]),
  product("WER-023", "MAMMUT Stoney HS ジャケット",                   "MAMMUT",         "cat-wear",  72000, 11, "ハードシェルスキージャケット。耐久性の高い素材を使用。",                           { waterproof: "15000mm", breathability: "15000g", size: "M" }, ["ジャケット","ハードシェル"]),
  product("WER-024", "NORRONA Lofoten GORE-TEX Pro ジャケット",       "NORRONA",        "cat-wear", 160000,  3, "最高峰のフリーライドジャケット。GORE-TEX Pro で究極の防水透湿。",                  { waterproof: "GORE-TEX Pro", breathability: "GORE-TEX Pro", size: "M" }, ["ジャケット","GORE-TEX Pro","フリーライド"]),
  product("WER-025", "ATOMIC RS ジャケット",                           "ATOMIC",         "cat-wear",  65000, 14, "レーシング向けスリムフィットジャケット。軽量で運動性に優れる。",                    { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","レーシング","軽量"]),
  product("WER-026", "HEAD Rebels ジャケット",                         "HEAD",           "cat-wear",  58000, 16, "レーシングスタイルのスキージャケット。スポーティなデザイン。",                      { waterproof: "20000mm", breathability: "20000g", size: "L" }, ["ジャケット","レーシング"]),
  product("WER-027", "ONYONE ONJ97A00 ジャケット",                     "ONYONE",         "cat-wear",  72000, 12, "日本製高機能スキージャケット。技術選でも愛用者多数。",                            { waterproof: "20000mm", breathability: "40000g", size: "M" }, ["ジャケット","日本製","技術選"]),
  product("WER-028", "ONYONE ONP97A50 パンツ",                         "ONYONE",         "cat-wear",  52000, 14, "日本製高機能スキーパンツ。動きやすさと耐久性を両立。",                            { waterproof: "20000mm", breathability: "40000g", size: "M" }, ["パンツ","日本製","技術選"]),
  product("WER-029", "SCHOFFEL Canazei ジャケット",                    "SCHOFFEL",       "cat-wear",  88000,  9, "オーストリア発のスキージャケット。上質な素材感と機能性。",                         { waterproof: "20000mm", breathability: "20000g", size: "M" }, ["ジャケット","オーストリア"]),
  product("WER-030", "PICTURE Naikoon ジャケット",                     "PICTURE",        "cat-wear",  55000, 16, "エコフレンドリーなスキージャケット。リサイクル素材を積極的に使用。",                 { waterproof: "20000mm", breathability: "15000g", size: "M" }, ["ジャケット","エコ","サステナブル"]),
];

// ---------------------------------------------------------------------------
// 5. グローブ (30 件)
// ---------------------------------------------------------------------------
const gloves = [
  product("GLV-001", "HESTRA Fall Line",                    "HESTRA",         "cat-gloves", 22000, 20, "クラシックレザースキーグローブ。高い耐久性と自然な操作感。",                        { material: "レザー", type: "5本指" }, ["レザー","オールラウンド"]),
  product("GLV-002", "HESTRA Army Leather Heli Ski",        "HESTRA",         "cat-gloves", 25000, 15, "バックカントリー向けグローブ。防水透湿と保温性のバランスが秀逸。",                   { material: "レザー+ナイロン", type: "5本指" }, ["バックカントリー","防水"]),
  product("GLV-003", "HESTRA RSL Comp Vertical Cut",        "HESTRA",         "cat-gloves", 18000, 18, "レーシンググローブ。ポールガード付きで高いプロテクション。",                        { material: "レザー", type: "5本指" }, ["レーシング","プロテクション"]),
  product("GLV-004", "HESTRA Leather Fall Line 3-Finger",   "HESTRA",         "cat-gloves", 24000, 14, "3 フィンガーモデル。保温力と操作性のバランスが良い。",                             { material: "レザー", type: "3本指" }, ["レザー","保温"]),
  product("GLV-005", "REUSCH Worldcup Warrior GS",          "REUSCH",         "cat-gloves", 15000, 22, "GS レーシンググローブ。グリップ力と操作性に優れる。",                              { material: "合成皮革+ナイロン", type: "5本指" }, ["レーシング","GS"]),
  product("GLV-006", "REUSCH Racing Spirit",                "REUSCH",         "cat-gloves", 12000, 25, "レーシング対応グローブ。薄手で高いポールグリップ感。",                             { material: "合成皮革", type: "5本指" }, ["レーシング","薄手"]),
  product("GLV-007", "REUSCH Primus R-TEX XT",              "REUSCH",         "cat-gloves",  9800, 30, "オールラウンドグローブ。R-TEX 防水メンブレン搭載。",                               { material: "ナイロン", type: "5本指" }, ["オールラウンド","防水"]),
  product("GLV-008", "LEKI WCR Flex 3D",                    "LEKI",           "cat-gloves", 16000, 18, "ワールドカップレーシンググローブ。3D フィットで最高の操作性。",                      { material: "レザー+合成", type: "5本指" }, ["レーシング","3Dフィット"]),
  product("GLV-009", "LEKI Griffin Pro 3D",                  "LEKI",           "cat-gloves", 14000, 20, "プロモデルグローブ。トリガーシステムでポール操作が容易。",                          { material: "レザー+合成", type: "5本指" }, ["オールラウンド","トリガーシステム"]),
  product("GLV-010", "LEKI Stratos",                         "LEKI",           "cat-gloves", 11000, 24, "オールマウンテングローブ。保温性と透湿性を兼ね備えた万能モデル。",                   { material: "合成皮革+ナイロン", type: "5本指" }, ["オールマウンテン","保温"]),
  product("GLV-011", "BLACK DIAMOND Guide Finger",           "BLACK DIAMOND",  "cat-gloves", 28000, 10, "バックカントリー向け最高峰グローブ。GORE-TEX 防水メンブレン。",                    { material: "レザー+GORE-TEX", type: "3本指" }, ["バックカントリー","GORE-TEX"]),
  product("GLV-012", "BLACK DIAMOND Mission MX",             "BLACK DIAMOND",  "cat-gloves", 12000, 22, "オールマウンテングローブ。タッチスクリーン対応。",                                 { material: "合成皮革+ナイロン", type: "5本指" }, ["オールマウンテン","タッチスクリーン"]),
  product("GLV-013", "SWANY SX-70 Toaster",                  "SWANY",          "cat-gloves", 15000, 18, "トースター型ミトングローブ。独自のジッパー構造で着脱簡単。",                       { material: "ナイロン+レザー", type: "ミトン" }, ["ミトン","保温","ジッパー"]),
  product("GLV-014", "SWANY SX-42 Light Speed",              "SWANY",          "cat-gloves", 12000, 20, "軽量レーシンググローブ。ドライフィンガーテクノロジー搭載。",                       { material: "合成皮革", type: "5本指" }, ["レーシング","軽量"]),
  product("GLV-015", "SWANY SX-80 Pro 3-Finger",             "SWANY",          "cat-gloves", 18000, 14, "プロ仕様の 3 フィンガーグローブ。極寒環境でも高い保温力。",                        { material: "レザー+ナイロン", type: "3本指" }, ["プロ","保温","極寒"]),
  product("GLV-016", "DESCENTE DGL-7023 レーシンググローブ",    "DESCENTE",       "cat-gloves", 11000, 22, "レーシング対応グローブ。薄手で高いグリップ力。",                                 { material: "合成皮革+ナイロン", type: "5本指" }, ["レーシング","薄手"]),
  product("GLV-017", "PHENIX PFA78GL フィンガーグローブ",       "PHENIX",         "cat-gloves", 13000, 20, "スキーチーム御用達グローブ。高い防水透湿性能。",                                 { material: "合成皮革+ナイロン", type: "5本指" }, ["チーム","防水"]),
  product("GLV-018", "GOLDWIN GR-A106 スキーグローブ",          "GOLDWIN",        "cat-gloves", 16000, 16, "プレミアムスキーグローブ。上質なレザーと高い保温性。",                            { material: "レザー", type: "5本指" }, ["プレミアム","レザー"]),
  product("GLV-019", "SALOMON Force GTX グローブ",              "SALOMON",        "cat-gloves", 12000, 22, "GORE-TEX 搭載オールラウンドグローブ。確実な防水性。",                            { material: "ナイロン+GORE-TEX", type: "5本指" }, ["GORE-TEX","オールラウンド"]),
  product("GLV-020", "SALOMON Propeller Dry グローブ",          "SALOMON",        "cat-gloves",  8500, 28, "コストパフォーマンスに優れたスキーグローブ。防水透湿。",                          { material: "合成皮革+ナイロン", type: "5本指" }, ["コスパ","防水"]),
  product("GLV-021", "ATOMIC Redster レーシンググローブ",        "ATOMIC",         "cat-gloves", 10000, 24, "レーシング対応グローブ。軽量で操作性に優れる。",                                 { material: "合成皮革", type: "5本指" }, ["レーシング","軽量"]),
  product("GLV-022", "HEAD WorldCup レーシンググローブ",         "HEAD",           "cat-gloves", 13000, 20, "ワールドカップ仕様レーシンググローブ。ハードプロテクション付き。",                  { material: "合成皮革+ナイロン", type: "5本指" }, ["レーシング","プロテクション"]),
  product("GLV-023", "ROSSIGNOL Speed IMPR グローブ",           "ROSSIGNOL",      "cat-gloves",  9000, 26, "オールマウンテングローブ。IMPR 防水メンブレンで確実な防水。",                     { material: "合成皮革+ナイロン", type: "5本指" }, ["オールマウンテン","防水"]),
  product("GLV-024", "KOMPERDELL Thermo Glove",               "KOMPERDELL",     "cat-gloves", 20000, 12, "電熱ヒーターグローブ。バッテリー駆動で極寒でも暖かい。",                         { material: "ナイロン+レザー", type: "5本指" }, ["電熱","極寒","バッテリー"]),
  product("GLV-025", "OUTDOOR RESEARCH Revolution II GTX",     "OUTDOOR RESEARCH","cat-gloves", 22000, 14, "GORE-TEX 搭載バックカントリーグローブ。高い防水性と透湿性。",                    { material: "ナイロン+GORE-TEX", type: "5本指" }, ["バックカントリー","GORE-TEX"]),
  product("GLV-026", "DAKINE Titan 3-Finger GORE-TEX",         "DAKINE",         "cat-gloves", 16000, 18, "GORE-TEX 搭載 3 フィンガーグローブ。インナー取り外し可能。",                     { material: "ナイロン+GORE-TEX", type: "3本指" }, ["GORE-TEX","3本指","インナー"]),
  product("GLV-027", "POW Stealth GTX グローブ",                "POW",            "cat-gloves", 14000, 20, "GORE-TEX 搭載フリーライドグローブ。耐久性の高いパームレザー。",                   { material: "レザー+GORE-TEX", type: "5本指" }, ["フリーライド","GORE-TEX"]),
  product("GLV-028", "LEVEL SQ CF ミトン",                      "LEVEL",          "cat-gloves", 18000, 14, "ハイエンドミトン。カーボンファイバー補強で高いプロテクション。",                   { material: "カーボン+ナイロン", type: "ミトン" }, ["ミトン","カーボン","プロテクション"]),
  product("GLV-029", "ZIENER Guard GTX Grip PR グローブ",       "ZIENER",         "cat-gloves", 11000, 22, "GORE-TEX 搭載レーシンググローブ。プロテクター内蔵。",                            { material: "合成皮革+GORE-TEX", type: "5本指" }, ["レーシング","GORE-TEX","プロテクション"]),
  product("GLV-030", "BURTON Gore-Tex Glove",                   "BURTON",         "cat-gloves", 13000, 20, "GORE-TEX 搭載スノーグローブ。スキーにも対応する高い汎用性。",                     { material: "ナイロン+GORE-TEX", type: "5本指" }, ["GORE-TEX","オールラウンド"]),
];

// ---------------------------------------------------------------------------
// 6. ゴーグル (30 件)
// ---------------------------------------------------------------------------
const goggles = [
  product("GGL-001", "OAKLEY Flight Deck L",                "OAKLEY",  "cat-goggles", 32000, 15, "フレームレスデザインの広視野ゴーグル。Prizm レンズで視認性抜群。",              { lens: "Prizm Snow", fit: "ラージ", uvProtection: "UV400" },  ["フレームレス","Prizm","広視野"]),
  product("GGL-002", "OAKLEY Airbrake XL",                   "OAKLEY",  "cat-goggles", 38000, 12, "高速レンズ交換対応のハイエンドゴーグル。Switchlock テクノロジー。",             { lens: "Prizm Snow", fit: "ラージ", uvProtection: "UV400" },  ["レンズ交換","Prizm","ハイエンド"]),
  product("GGL-003", "OAKLEY Line Miner L",                  "OAKLEY",  "cat-goggles", 25000, 18, "シリンドリカルレンズのシンプルデザインゴーグル。ヘルメット対応。",              { lens: "Prizm Snow", fit: "ラージ", uvProtection: "UV400" },  ["シリンドリカル","Prizm"]),
  product("GGL-004", "OAKLEY Flight Tracker L",              "OAKLEY",  "cat-goggles", 22000, 20, "ミディアムフィットの万能ゴーグル。Ridgelock レンズ交換システム。",             { lens: "Prizm Snow", fit: "ミディアム", uvProtection: "UV400" }, ["万能","Prizm"]),
  product("GGL-005", "SMITH I/O Mag",                        "SMITH",   "cat-goggles", 35000, 14, "マグネット式レンズ交換ゴーグル。ChromaPop レンズで鮮やかな視界。",            { lens: "ChromaPop", fit: "ミディアム", uvProtection: "UV400" }, ["マグネット","ChromaPop"]),
  product("GGL-006", "SMITH 4D MAG",                         "SMITH",   "cat-goggles", 42000, 10, "BirdsEye Vision で下方視界を拡大。SMITH 最高峰のゴーグル。",                  { lens: "ChromaPop", fit: "ラージ", uvProtection: "UV400" },  ["BirdsEye","ChromaPop","最高峰"]),
  product("GGL-007", "SMITH Squad MAG",                      "SMITH",   "cat-goggles", 22000, 20, "マグネットレンズ交換対応のエントリーモデル。コスパに優れる。",                { lens: "ChromaPop", fit: "ミディアム", uvProtection: "UV400" }, ["マグネット","コスパ"]),
  product("GGL-008", "SMITH Skyline XL",                     "SMITH",   "cat-goggles", 18000, 22, "大型フレームレスゴーグル。広い視界とフィット感。",                            { lens: "ChromaPop", fit: "ラージ", uvProtection: "UV400" },  ["フレームレス","広視野"]),
  product("GGL-009", "SWANS ROVO-MDH-CU/LI",                "SWANS",   "cat-goggles", 28000, 16, "日本製ハイエンドゴーグル。調光レンズ搭載で天候変化に対応。",                  { lens: "調光 ULTRA レンズ", fit: "アジアンフィット", uvProtection: "UV400" }, ["調光","日本製","アジアンフィット"]),
  product("GGL-010", "SWANS RACAN-MDH-CU",                  "SWANS",   "cat-goggles", 20000, 20, "ミドルレンジゴーグル。ダブルレンズで曇りにくい。",                            { lens: "ULTRA レンズ", fit: "アジアンフィット", uvProtection: "UV400" }, ["日本製","アジアンフィット"]),
  product("GGL-011", "SWANS Ridgeline",                      "SWANS",   "cat-goggles", 24000, 18, "フレームレスデザインのプレミアムゴーグル。広い視界を確保。",                   { lens: "ULTRA レンズ", fit: "アジアンフィット", uvProtection: "UV400" }, ["フレームレス","日本製"]),
  product("GGL-012", "UVEX Downhill 2100 CV",                "UVEX",    "cat-goggles", 22000, 16, "コントラストビュー搭載ゴーグル。悪天候でも路面変化がわかりやすい。",            { lens: "ColorVision", fit: "ミディアム", uvProtection: "UV400" }, ["ColorVision","悪天候"]),
  product("GGL-013", "UVEX Contest CV",                      "UVEX",    "cat-goggles", 18000, 20, "レーシング向けゴーグル。コンパクトなデザインでヘルメットとの相性が良い。",       { lens: "ColorVision", fit: "コンパクト", uvProtection: "UV400" }, ["レーシング","コンパクト"]),
  product("GGL-014", "GIRO Contour RS",                      "GIRO",    "cat-goggles", 30000, 14, "VIVID レンズ搭載のハイエンドゴーグル。クイックチェンジレンズシステム。",         { lens: "VIVID", fit: "ラージ", uvProtection: "UV400" }, ["VIVID","ハイエンド"]),
  product("GGL-015", "GIRO Method",                          "GIRO",    "cat-goggles", 25000, 18, "万能なミディアムフィットゴーグル。VIVID レンズで鮮やかな視界。",               { lens: "VIVID", fit: "ミディアム", uvProtection: "UV400" }, ["VIVID","万能"]),
  product("GGL-016", "GIRO Axis",                            "GIRO",    "cat-goggles", 22000, 20, "マグネットレンズ交換対応ゴーグル。2 枚のレンズ付属でお得。",                   { lens: "VIVID", fit: "ミディアム", uvProtection: "UV400" }, ["マグネット","VIVID"]),
  product("GGL-017", "BOLLE Nevada",                         "BOLLE",   "cat-goggles", 18000, 22, "フレームレスデザインのオールラウンドゴーグル。コスパに優れる。",               { lens: "VOLT", fit: "ミディアム", uvProtection: "UV400" }, ["フレームレス","コスパ"]),
  product("GGL-018", "BOLLE Torus",                          "BOLLE",   "cat-goggles", 16000, 24, "トーリックレンズで歪みの少ないクリアな視界。",                               { lens: "VOLT", fit: "ミディアム", uvProtection: "UV400" }, ["トーリック","クリア"]),
  product("GGL-019", "POC Orb Clarity",                      "POC",     "cat-goggles", 28000, 14, "Clarity レンズ by ZEISS 搭載。コントラスト向上で地形把握が容易。",           { lens: "Clarity by ZEISS", fit: "ラージ", uvProtection: "UV400" }, ["Clarity","ZEISS"]),
  product("GGL-020", "POC Fovea Clarity",                    "POC",     "cat-goggles", 32000, 12, "広視界ゴーグル。ZEISS レンズで最高クラスの光学性能。",                        { lens: "Clarity by ZEISS", fit: "ラージ", uvProtection: "UV400" }, ["広視界","ZEISS","ハイエンド"]),
  product("GGL-021", "DRAGON X2S",                           "DRAGON",  "cat-goggles", 25000, 16, "スウィフトロックレンズ交換対応。コンパクトフレームで小顔にもフィット。",          { lens: "Lumalens", fit: "コンパクト", uvProtection: "UV400" }, ["レンズ交換","コンパクト"]),
  product("GGL-022", "DRAGON NFX2",                          "DRAGON",  "cat-goggles", 28000, 14, "フレームレスデザインのハイエンドゴーグル。広い視界と快適なフィット。",            { lens: "Lumalens", fit: "ミディアム", uvProtection: "UV400" }, ["フレームレス","ハイエンド"]),
  product("GGL-023", "ANON M4 Toric",                        "ANON",    "cat-goggles", 35000, 12, "マグネット式レンズ交換対応。トーリックレンズで歪みの少ない視界。",               { lens: "PERCEIVE", fit: "ミディアム", uvProtection: "UV400" }, ["マグネット","トーリック"]),
  product("GGL-024", "ANON M5S",                             "ANON",    "cat-goggles", 30000, 14, "球面レンズのハイパフォーマンスゴーグル。MFI フェイスマスク対応。",               { lens: "PERCEIVE", fit: "ミディアム", uvProtection: "UV400" }, ["ハイパフォーマンス","MFI"]),
  product("GGL-025", "ELECTRIC Kleveland",                   "ELECTRIC","cat-goggles", 20000, 18, "フレームレスデザインのスタイリッシュゴーグル。広い視野角。",                    { lens: "ゴールド", fit: "ミディアム", uvProtection: "UV400" }, ["フレームレス","スタイリッシュ"]),
  product("GGL-026", "SPY Marauder",                         "SPY",     "cat-goggles", 22000, 16, "Happy レンズ搭載ゴーグル。独自のレンズテクノロジーでコントラスト向上。",        { lens: "Happy Lens", fit: "ミディアム", uvProtection: "UV400" }, ["Happy Lens","コントラスト"]),
  product("GGL-027", "JULBO Aerospace",                      "JULBO",   "cat-goggles", 28000, 12, "ベンチレーション調整機能搭載。登行時と滑走時で切り替え可能。",                 { lens: "REACTIV", fit: "ラージ", uvProtection: "UV400" }, ["ベンチレーション","ツーリング"]),
  product("GGL-028", "SALOMON Radium Pro SIGMA",             "SALOMON", "cat-goggles", 20000, 20, "シグマレンズ搭載のコスパ抜群ゴーグル。オールラウンドに使える。",               { lens: "SIGMA", fit: "ミディアム", uvProtection: "UV400" }, ["コスパ","オールラウンド"]),
  product("GGL-029", "ATOMIC Count 360 HD",                  "ATOMIC",  "cat-goggles", 24000, 16, "HD レンズ搭載のハイパフォーマンスゴーグル。360 度の広い視界。",                { lens: "HD", fit: "ラージ", uvProtection: "UV400" }, ["HD","広視界"]),
  product("GGL-030", "HEAD Magnify 5K",                      "HEAD",    "cat-goggles", 22000, 18, "5K レンズテクノロジー搭載。高いコントラストとクリアな視界を実現。",             { lens: "5K", fit: "ミディアム", uvProtection: "UV400" }, ["5K","コントラスト"]),
];

// ---------------------------------------------------------------------------
// 7. ヘルメット (30 件)
// ---------------------------------------------------------------------------
const helmets = [
  product("HLM-001", "GIRO Strive MIPS",                    "GIRO",              "cat-helmets", 52000,  8, "FIS 対応レーシングヘルメット。MIPS による回転衝撃緩和機能搭載。",                 { certification: "CE/FIS", mips: "あり", ventilation: "固定" },  ["レーシング","MIPS","FIS"]),
  product("HLM-002", "GIRO Neo MIPS",                       "GIRO",              "cat-helmets", 38000, 14, "フリーライド向けヘルメット。MIPS 搭載で高い安全性。",                          { certification: "CE", mips: "あり", ventilation: "調整可" },    ["フリーライド","MIPS"]),
  product("HLM-003", "GIRO Range MIPS",                     "GIRO",              "cat-helmets", 32000, 18, "オールマウンテンヘルメット。バイザー付きでゴーグル不要時も快適。",                 { certification: "CE", mips: "あり", ventilation: "調整可" },    ["オールマウンテン","MIPS","バイザー"]),
  product("HLM-004", "SMITH Vantage MIPS",                  "SMITH",             "cat-helmets", 42000, 12, "ハイブリッド構造のプレミアムヘルメット。Aerocore 構造で軽量かつ高い衝撃吸収。",   { certification: "CE/ASTM", mips: "あり", ventilation: "調整可" }, ["プレミアム","MIPS","軽量"]),
  product("HLM-005", "SMITH Code MIPS",                     "SMITH",             "cat-helmets", 28000, 18, "コンパクトなデザインのフリーライドヘルメット。豊富なベンチレーション。",           { certification: "CE/ASTM", mips: "あり", ventilation: "調整可" }, ["フリーライド","MIPS","コンパクト"]),
  product("HLM-006", "SMITH Level MIPS",                    "SMITH",             "cat-helmets", 22000, 22, "エントリーレベルの MIPS ヘルメット。コスパに優れた安全設計。",                   { certification: "CE/ASTM", mips: "あり", ventilation: "固定" },  ["エントリー","MIPS","コスパ"]),
  product("HLM-007", "ATOMIC Redster CTD",                  "ATOMIC",            "cat-helmets", 35000, 14, "レーシングヘルメット。エアロダイナミクスを追求した形状。",                      { certification: "CE/FIS", mips: "なし", ventilation: "固定" },   ["レーシング","エアロ","FIS"]),
  product("HLM-008", "ATOMIC Count AMID",                   "ATOMIC",            "cat-helmets", 28000, 18, "AMID 構造のオールマウンテンヘルメット。広い衝撃吸収エリア。",                    { certification: "CE", mips: "AMID", ventilation: "調整可" },    ["オールマウンテン","AMID"]),
  product("HLM-009", "SALOMON MTN Lab",                     "SALOMON",           "cat-helmets", 32000, 14, "バックカントリー向け軽量ヘルメット。最小限の重量で高い保護力。",                 { certification: "CE", mips: "あり", ventilation: "調整可" },    ["バックカントリー","軽量","MIPS"]),
  product("HLM-010", "SALOMON Driver Pro Sigma MIPS",       "SALOMON",           "cat-helmets", 38000, 12, "バイザー一体型ヘルメット。ゴーグル不要でスッキリしたシルエット。",                { certification: "CE", mips: "あり", ventilation: "調整可" },    ["バイザー一体型","MIPS"]),
  product("HLM-011", "SALOMON Pioneer LT MIPS",             "SALOMON",           "cat-helmets", 18000, 24, "軽量エントリーヘルメット。MIPS 搭載で安心の安全設計。",                        { certification: "CE", mips: "あり", ventilation: "固定" },      ["エントリー","MIPS","軽量"]),
  product("HLM-012", "SWEET PROTECTION Trooper 2Vi MIPS",   "SWEET PROTECTION", "cat-helmets", 48000, 10, "2Vi MIPS テクノロジー搭載。二重の回転衝撃緩和で最高レベルの保護。",              { certification: "CE", mips: "2Vi MIPS", ventilation: "調整可" },["最高保護","2Vi MIPS"]),
  product("HLM-013", "SWEET PROTECTION Switcher MIPS",      "SWEET PROTECTION", "cat-helmets", 35000, 14, "軽量フリーライドヘルメット。MIPS 搭載で安全性を確保。",                        { certification: "CE", mips: "あり", ventilation: "調整可" },    ["フリーライド","MIPS","軽量"]),
  product("HLM-014", "UVEX Race+",                          "UVEX",              "cat-helmets", 42000, 10, "FIS 認定レーシングヘルメット。エアロダイナミクスと安全性を両立。",               { certification: "CE/FIS", mips: "なし", ventilation: "固定" },   ["レーシング","FIS","エアロ"]),
  product("HLM-015", "UVEX Legend 2.0",                     "UVEX",              "cat-helmets", 18000, 22, "オールラウンドヘルメット。ダイヤル式サイズ調整で快適フィット。",                 { certification: "CE", mips: "なし", ventilation: "調整可" },     ["オールラウンド","ダイヤル調整"]),
  product("HLM-016", "POC Obex MIPS",                       "POC",               "cat-helmets", 30000, 16, "スウェーデン発の安全重視ヘルメット。MIPS 搭載でマルチインパクト対応。",           { certification: "CE", mips: "あり", ventilation: "調整可" },    ["安全重視","MIPS","スウェーデン"]),
  product("HLM-017", "POC Skull Dura X MIPS",               "POC",               "cat-helmets", 55000,  6, "FIS 対応レーシングヘルメット。最高レベルの衝撃保護性能。",                     { certification: "CE/FIS", mips: "あり", ventilation: "固定" },  ["レーシング","FIS","MIPS"]),
  product("HLM-018", "HEAD Radar MIPS",                     "HEAD",              "cat-helmets", 25000, 18, "オールマウンテンヘルメット。MIPS 搭載でコスパに優れる。",                      { certification: "CE", mips: "あり", ventilation: "調整可" },    ["オールマウンテン","MIPS","コスパ"]),
  product("HLM-019", "HEAD Race",                           "HEAD",              "cat-helmets", 38000, 12, "FIS 対応レーシングヘルメット。軽量シェル構造で高速滑走に対応。",                { certification: "CE/FIS", mips: "なし", ventilation: "固定" },   ["レーシング","FIS","軽量"]),
  product("HLM-020", "ROSSIGNOL Hero Giant Impacts FIS",    "ROSSIGNOL",         "cat-helmets", 45000, 10, "FIS 公認レーシングヘルメット。チンガード対応。",                               { certification: "CE/FIS", mips: "なし", ventilation: "固定" },   ["レーシング","FIS","チンガード"]),
  product("HLM-021", "ROSSIGNOL Fit Impacts MIPS",          "ROSSIGNOL",         "cat-helmets", 22000, 20, "オールマウンテン MIPS ヘルメット。調整可能なベンチレーション。",                 { certification: "CE", mips: "あり", ventilation: "調整可" },    ["オールマウンテン","MIPS"]),
  product("HLM-022", "K2 Verdict MIPS",                     "K2",                "cat-helmets", 25000, 18, "フリーライド向けヘルメット。MIPS とパッシブベンチレーション搭載。",               { certification: "CE/ASTM", mips: "あり", ventilation: "固定" }, ["フリーライド","MIPS"]),
  product("HLM-023", "K2 Phase Pro",                        "K2",                "cat-helmets", 18000, 22, "軽量なオールラウンドヘルメット。シンプルなデザインで使いやすい。",                { certification: "CE", mips: "なし", ventilation: "調整可" },     ["オールラウンド","軽量"]),
  product("HLM-024", "BOLLE Atmos MIPS",                    "BOLLE",             "cat-helmets", 22000, 18, "MIPS 搭載のスタイリッシュなヘルメット。優れた通気性。",                        { certification: "CE", mips: "あり", ventilation: "調整可" },    ["MIPS","スタイリッシュ"]),
  product("HLM-025", "BOLLE Medalist Carbon Pro MIPS",      "BOLLE",             "cat-helmets", 55000,  6, "カーボンシェルの FIS レーシングヘルメット。軽量かつ高強度。",                   { certification: "CE/FIS", mips: "あり", ventilation: "固定" },  ["カーボン","FIS","レーシング"]),
  product("HLM-026", "OAKLEY MOD5 MIPS",                    "OAKLEY",            "cat-helmets", 35000, 14, "MIPS 搭載のハイエンドヘルメット。BOA フィットシステム。",                      { certification: "CE/ASTM", mips: "あり", ventilation: "調整可" }, ["MIPS","BOA","ハイエンド"]),
  product("HLM-027", "ANON Merak WaveCel",                  "ANON",              "cat-helmets", 38000, 12, "WaveCel テクノロジー搭載。回転衝撃を効果的に吸収。",                          { certification: "CE/ASTM", mips: "WaveCel", ventilation: "調整可" }, ["WaveCel","ハイエンド"]),
  product("HLM-028", "ALPINA Grand",                        "ALPINA",            "cat-helmets", 15000, 25, "エントリーレベルの快適ヘルメット。ダイヤルフィットで簡単調整。",                 { certification: "CE", mips: "なし", ventilation: "固定" },       ["エントリー","コスパ"]),
  product("HLM-029", "MARKER Ampire 2 MIPS",                "MARKER",            "cat-helmets", 20000, 20, "MIPS 搭載のオールマウンテンヘルメット。優れたコストパフォーマンス。",             { certification: "CE", mips: "あり", ventilation: "調整可" },    ["MIPS","コスパ","オールマウンテン"]),
  product("HLM-030", "SCOTT Symbol 2 Plus MIPS",            "SCOTT",             "cat-helmets", 28000, 16, "MIPS 搭載フリーライドヘルメット。快適なフィットとスタイル。",                   { certification: "CE", mips: "あり", ventilation: "調整可" },    ["フリーライド","MIPS","スタイリッシュ"]),
];

// ---------------------------------------------------------------------------
// 8. ポール (30 件)
// ---------------------------------------------------------------------------
const poles = [
  product("POL-001", "LEKI WCR SL 3D",                 "LEKI",       "cat-poles", 22000, 15, "ワールドカップ SL レーシングポール。トリガー S グリップ搭載。",                     { material: "アルミ", diameter: "16mm", grip: "トリガーS" },       ["レーシング","SL","トリガー"]),
  product("POL-002", "LEKI WCR GS 3D",                 "LEKI",       "cat-poles", 22000, 14, "GS レーシングポール。カーブドシャフトで空気抵抗を軽減。",                           { material: "アルミ", diameter: "18mm", grip: "トリガーS" },       ["レーシング","GS","カーブド"]),
  product("POL-003", "LEKI SpeedS",                    "LEKI",       "cat-poles", 15000, 20, "基礎・デモ向けポール。トリガーシステムで安全な握り。",                              { material: "アルミ", diameter: "16mm", grip: "トリガーS" },       ["基礎","デモ","トリガー"]),
  product("POL-004", "LEKI Carbon 14 3D",              "LEKI",       "cat-poles", 28000, 12, "カーボンシャフトの軽量ポール。振りやすさと耐久性を両立。",                          { material: "カーボン", diameter: "14mm", grip: "トリガーS" },     ["カーボン","軽量","トリガー"]),
  product("POL-005", "LEKI Detect S",                  "LEKI",       "cat-poles", 12000, 22, "フリーライド向けポール。セーフティリリースグリップ付き。",                          { material: "アルミ", diameter: "16mm", grip: "トリガーS" },       ["フリーライド","セーフティ"]),
  product("POL-006", "SINANO CK-GS",                   "SINANO",     "cat-poles", 18000, 16, "日本製 GS レーシングポール。しなりを活かした軽快な操作感。",                        { material: "カーボン", diameter: "16mm", grip: "グリップ" },      ["レーシング","GS","日本製"]),
  product("POL-007", "SINANO CK-SL",                   "SINANO",     "cat-poles", 18000, 15, "日本製 SL レーシングポール。高い剛性で正確なポールプラント。",                      { material: "カーボン", diameter: "14mm", grip: "グリップ" },      ["レーシング","SL","日本製"]),
  product("POL-008", "SINANO Free-K",                  "SINANO",     "cat-poles", 14000, 18, "フリースキー向け軽量カーボンポール。扱いやすいデザイン。",                          { material: "カーボン", diameter: "14mm", grip: "グリップ" },      ["フリースキー","カーボン","軽量"]),
  product("POL-009", "SINANO Eagle R",                 "SINANO",     "cat-poles", 10000, 24, "オールラウンドアルミポール。基礎スキーに最適な汎用モデル。",                        { material: "アルミ", diameter: "16mm", grip: "グリップ" },      ["基礎","オールラウンド","日本製"]),
  product("POL-010", "SWIX WorldCup GS",               "SWIX",       "cat-poles", 20000, 14, "ワールドカップ GS ポール。軽量アルミシャフトで高い強度。",                          { material: "アルミ", diameter: "18mm", grip: "ストラップ" },     ["レーシング","GS"]),
  product("POL-011", "SWIX WorldCup SL",               "SWIX",       "cat-poles", 18000, 16, "SL レーシングポール。テーパードシャフトで振りぬきやすい。",                          { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["レーシング","SL"]),
  product("POL-012", "SWIX Sonic R1",                  "SWIX",       "cat-poles", 10000, 22, "レクリエーションポール。軽量で使いやすい入門モデル。",                               { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["入門","軽量"]),
  product("POL-013", "ATOMIC Redster Carbon",          "ATOMIC",     "cat-poles", 18000, 16, "カーボンレーシングポール。軽量で高い剛性を実現。",                                  { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["レーシング","カーボン","軽量"]),
  product("POL-014", "ATOMIC AMT SQS",                 "ATOMIC",     "cat-poles", 8000,  28, "オールマウンテンアルミポール。SQS セーフティシステム搭載。",                         { material: "アルミ", diameter: "16mm", grip: "SQS" },           ["オールマウンテン","セーフティ"]),
  product("POL-015", "ATOMIC BCT Touring Carbon",      "ATOMIC",     "cat-poles", 22000, 12, "ツーリング向け伸縮カーボンポール。コンパクト収納が可能。",                           { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["ツーリング","伸縮","カーボン"]),
  product("POL-016", "SALOMON X North",                "SALOMON",    "cat-poles", 12000, 20, "オールラウンドアルミポール。快適なグリップと軽量設計。",                              { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["オールラウンド","軽量"]),
  product("POL-017", "SALOMON Arctic S3",              "SALOMON",    "cat-poles", 8000,  26, "入門者向けスキーポール。コスパに優れた定番モデル。",                                  { material: "アルミ", diameter: "18mm", grip: "ストラップ" },     ["入門","コスパ","定番"]),
  product("POL-018", "SALOMON MTN Carbon S3",          "SALOMON",    "cat-poles", 24000, 10, "バックカントリー向けカーボン伸縮ポール。3 段階調整可能。",                            { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["バックカントリー","カーボン","伸縮"]),
  product("POL-019", "ROSSIGNOL Stove",                "ROSSIGNOL",  "cat-poles", 10000, 22, "オールラウンドアルミポール。エルゴノミクスグリップで快適。",                          { material: "アルミ", diameter: "16mm", grip: "エルゴ" },         ["オールラウンド","エルゴ"]),
  product("POL-020", "ROSSIGNOL Hero Carbon Safety",   "ROSSIGNOL",  "cat-poles", 22000, 14, "カーボンレーシングポール。セーフティリリースグリップ搭載。",                          { material: "カーボン", diameter: "14mm", grip: "セーフティ" },   ["レーシング","カーボン","セーフティ"]),
  product("POL-021", "ROSSIGNOL Tactic Carbon Safety", "ROSSIGNOL",  "cat-poles", 18000, 16, "フリーライド向けカーボンポール。軽量で振りやすい。",                                 { material: "カーボン", diameter: "14mm", grip: "セーフティ" },   ["フリーライド","カーボン"]),
  product("POL-022", "KOMPERDELL National Team Carbon","KOMPERDELL", "cat-poles", 25000, 10, "ナショナルチーム仕様カーボンポール。最高峰の軽さと剛性。",                           { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["レーシング","カーボン","チーム"]),
  product("POL-023", "KOMPERDELL Rebel Carbon GS",     "KOMPERDELL", "cat-poles", 20000, 14, "GS レーシング向けカーボンポール。カーブドシャフト。",                                 { material: "カーボン", diameter: "16mm", grip: "ストラップ" },   ["レーシング","GS","カーブド"]),
  product("POL-024", "KOMPERDELL Carbon Champion",     "KOMPERDELL", "cat-poles", 15000, 18, "基礎・デモ向けカーボンポール。スムーズなスウィングが可能。",                          { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["基礎","デモ","カーボン"]),
  product("POL-025", "HEAD WorldCup SL",               "HEAD",       "cat-poles", 16000, 16, "SL レーシングアルミポール。高い強度と軽量性。",                                       { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["レーシング","SL"]),
  product("POL-026", "HEAD Kore",                      "HEAD",       "cat-poles", 12000, 20, "フリーライド向けアルミポール。しっかりとしたグリップ感。",                             { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["フリーライド","オールマウンテン"]),
  product("POL-027", "K2 Power Carbon",                "K2",         "cat-poles", 14000, 18, "カーボンシャフトのオールマウンテンポール。パワフルな操作性。",                         { material: "カーボン", diameter: "14mm", grip: "ストラップ" },   ["カーボン","オールマウンテン"]),
  product("POL-028", "VOLKL Phantastick 18mm",         "VOLKL",      "cat-poles", 8000,  26, "定番アルミポール。シンプルで使いやすいデザイン。",                                    { material: "アルミ", diameter: "18mm", grip: "ストラップ" },     ["定番","アルミ"]),
  product("POL-029", "BLACK DIAMOND Traverse WR 2",    "BLACK DIAMOND","cat-poles",20000,12, "バックカントリー伸縮ポール。ウィペットアタッチメント対応。",                          { material: "アルミ", diameter: "16mm", grip: "ストラップ" },     ["バックカントリー","伸縮"]),
  product("POL-030", "SCOTT Pro Taper SRS",            "SCOTT",      "cat-poles", 10000, 22, "テーパードシャフトのフリーライドポール。SRS グリップで安全。",                          { material: "アルミ", diameter: "16mm", grip: "SRS" },           ["フリーライド","テーパード","セーフティ"]),
];

// ---------------------------------------------------------------------------
// 9. 一括 Insert
// ---------------------------------------------------------------------------
const allProducts = [].concat(skis, boots, wear, gloves, goggles, helmets, poles);

db.products.insertMany(allProducts);
print(">>> Inserted " + allProducts.length + " products (" +
  skis.length + " skis, " +
  boots.length + " boots, " +
  wear.length + " wear, " +
  gloves.length + " gloves, " +
  goggles.length + " goggles, " +
  helmets.length + " helmets, " +
  poles.length + " poles)");

// ---------------------------------------------------------------------------
// 10. インデックス作成
// ---------------------------------------------------------------------------
db.products.createIndex({ sku: 1 }, { unique: true });
db.products.createIndex({ categoryId: 1 });
db.products.createIndex({ status: 1 });
db.products.createIndex({ brand: 1 });
db.products.createIndex({ name: "text", brand: "text", description: "text" });
db.categories.createIndex({ name: 1 }, { unique: true });

print(">>> Indexes created");
print(">>> Seed complete!");
