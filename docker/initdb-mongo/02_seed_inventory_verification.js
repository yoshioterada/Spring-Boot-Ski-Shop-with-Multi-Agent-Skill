// =============================================================================
// MongoDB Verification Seed — InventoryMonitoringAgent 動作検証用
// =============================================================================
// 既存の 01_seed_inventory.js (210 商品) を前提に、検証シナリオ
// (Routing: AVAILABLE / LOW_STOCK / OUT_OF_STOCK) を確実に再現できるよう、
// 一部商品の stockQuantity を上書きする。
//
// 実行: docker compose down -v && docker compose up -d mongodb
//        （初期化時に initdb-mongo の全 .js が実行される）
// 個別実行: docker exec -i mongodb mongosh < docker/initdb-mongo/02_seed_inventory_verification.js
// =============================================================================

const db = db.getSiblingDB("skishop_inventory");

if (db.products.countDocuments() === 0) {
  print(">>> products collection is empty. 01_seed_inventory.js を先に実行してください。");
  quit();
}

const now = new Date();

// --- 1. OUT_OF_STOCK 候補（stockQuantity=0）------------------------------
//   - 在庫切れシナリオ / 代替提案シナリオで利用
const outOfStockSkus = [
  "SKI-003",   // ATOMIC Bent Chetler 100   (フリーライド)  → 代替: SKI-005, SKI-009, SKI-015
  "SKI-008",   // HEAD World Cup Rebels e-GS (GS レーシング) → 代替: SKI-002, SKI-004, SKI-013
  "BTS-010",   // ROSSIGNOL Hero World Cup ZJ+ (FIS)       → 代替: BTS-001, BTS-007, BTS-019
  "WER-024",   // NORRONA Lofoten GORE-TEX Pro (GORE-TEX)   → 代替: WER-007, WER-009, WER-022
  "GGL-006",   // SMITH 4D MAG (ハイエンドゴーグル)         → 代替: GGL-002, GGL-005, GGL-014
];

// --- 2. LOW_STOCK 候補（stockQuantity 1〜4、閾値 5 未満）------------------
const lowStockUpdates = [
  { sku: "SKI-001", stock: 4 },  // ATOMIC Redster S9i
  { sku: "SKI-021", stock: 3 },  // NORDICA Dobermann SLR
  { sku: "BTS-007", stock: 2 },  // HEAD Raptor 140 RS (FIS 上級)
  { sku: "BTS-016", stock: 4 },  // TECNICA Mach1 LV 130
  { sku: "WER-007", stock: 3 },  // GOLDWIN G-Bliss ジャケット (GORE-TEX)
  { sku: "WER-018", stock: 2 },  // KJUS Formula ジャケット
  { sku: "GGL-020", stock: 4 },  // POC Fovea Clarity
  { sku: "HLM-017", stock: 1 },  // POC Skull Dura X MIPS (FIS)
  { sku: "HLM-025", stock: 2 },  // BOLLE Medalist Carbon Pro MIPS
  { sku: "POL-022", stock: 3 },  // KOMPERDELL National Team Carbon
];

// --- 3. AVAILABLE を確実に保証する代表 SKU（stockQuantity=20）------------
const availableUpdates = [
  { sku: "SKI-005", stock: 20 }, // SALOMON QST 98 (オールマウンテン代替候補)
  { sku: "SKI-015", stock: 20 }, // VOLKL Mantra M6
  { sku: "BTS-001", stock: 25 }, // ATOMIC Redster CS 130
  { sku: "BTS-019", stock: 20 }, // LANGE RS 130
  { sku: "WER-009", stock: 18 }, // GOLDWIN G-Bliss パンツ
  { sku: "WER-022", stock: 15 }, // ARC'TERYX Sabre
  { sku: "GGL-005", stock: 20 }, // SMITH I/O Mag
  { sku: "GGL-014", stock: 18 }, // GIRO Contour RS
  { sku: "HLM-004", stock: 22 }, // SMITH Vantage MIPS
  { sku: "POL-001", stock: 25 }, // LEKI WCR SL 3D
];

// --- 4. 代替商品マッピング（attributes に埋め込み: alternativeSkus）------
//   inventory-management-service 側の /alternatives 実装に合わせて
//   tags/attributes を補強。実装が categoryId + tags ベースで類似品を返す
//   ようにするための補助情報。
const alternativeMappings = [
  { sku: "SKI-003", alts: ["SKI-005", "SKI-009", "SKI-015"] },
  { sku: "SKI-008", alts: ["SKI-002", "SKI-004", "SKI-013"] },
  { sku: "BTS-010", alts: ["BTS-001", "BTS-007", "BTS-019"] },
  { sku: "WER-024", alts: ["WER-007", "WER-009", "WER-022"] },
  { sku: "GGL-006", alts: ["GGL-002", "GGL-005", "GGL-014"] },
];

// --- 5. 適用 -------------------------------------------------------------
let outCount = 0;
outOfStockSkus.forEach(function (sku) {
  const r = db.products.updateOne(
    { sku: sku },
    { $set: { stockQuantity: 0, reservedQuantity: 0, updatedAt: now } }
  );
  outCount += r.modifiedCount;
});
print(">>> OUT_OF_STOCK 設定: " + outCount + " 件");

let lowCount = 0;
lowStockUpdates.forEach(function (u) {
  const r = db.products.updateOne(
    { sku: u.sku },
    { $set: { stockQuantity: u.stock, reservedQuantity: 0, updatedAt: now } }
  );
  lowCount += r.modifiedCount;
});
print(">>> LOW_STOCK 設定: " + lowCount + " 件");

let availCount = 0;
availableUpdates.forEach(function (u) {
  const r = db.products.updateOne(
    { sku: u.sku },
    { $set: { stockQuantity: u.stock, reservedQuantity: 0, updatedAt: now } }
  );
  availCount += r.modifiedCount;
});
print(">>> AVAILABLE 補強: " + availCount + " 件");

let altCount = 0;
alternativeMappings.forEach(function (m) {
  const r = db.products.updateOne(
    { sku: m.sku },
    { $set: { "attributes.alternativeSkus": m.alts.join(","), updatedAt: now } }
  );
  altCount += r.modifiedCount;
});
print(">>> 代替商品メタデータ付与: " + altCount + " 件");

// --- 6. 検証用サマリ -----------------------------------------------------
const total       = db.products.countDocuments();
const outOfStock  = db.products.countDocuments({ stockQuantity: 0 });
const lowStock    = db.products.countDocuments({ stockQuantity: { $gt: 0, $lt: 5 } });
const available   = db.products.countDocuments({ stockQuantity: { $gte: 5 } });

print("=================================================");
print(">>> 検証用シード適用完了");
print("    - 商品総数         : " + total);
print("    - OUT_OF_STOCK (0) : " + outOfStock);
print("    - LOW_STOCK  (1-4) : " + lowStock);
print("    - AVAILABLE  (>=5) : " + available);
print("=================================================");
