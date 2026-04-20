// ============================================================
// MongoDB Seed Script — Zero-hit search logs (skishop_inventory)
// F5 機会発見レーダー用デモデータ
// 実際には ProductController.searchProducts() から logSearchAsync() 経由で蓄積されるが、
// 初期状態でゼロヒット検索ログがないと F5 機能が動作しないため、代表的なゼロヒットクエリをシードする。
// ============================================================

db = db.getSiblingDB("skishop_inventory");

// 既にゼロヒットデータが存在する場合はスキップ
if (db.search_logs.countDocuments({ hitCount: 0 }) > 0) {
    print("[Seed] Zero-hit search_logs already exist – skipping.");
    quit();
}

var TTL_SECONDS = 60 * 60 * 24 * 90; // 90日
var now = new Date();

// ゼロヒットキーワード定義 (検索されたが在庫がなかったキーワード)
var zeroHitKeywords = [
    { kw: "サロモン スキー板",     count: 12 },
    { kw: "バートン スノーボード", count: 9  },
    { kw: "子供用スキー板",        count: 8  },
    { kw: "k2 スキー",            count: 7  },
    { kw: "フィッシャー ブーツ",   count: 6  },
    { kw: "アトミック ポール",     count: 5  },
    { kw: "スノーボードブーツ",    count: 4  },
    { kw: "フリースタイルスキー",  count: 3  }
];

var docs = [];
var rng = 0; // 擬似乱数シード
function nextInt(max) {
    rng = (rng * 1103515245 + 12345) & 0x7fffffff;
    return rng % max;
}

zeroHitKeywords.forEach(function(entry) {
    for (var i = 0; i < entry.count; i++) {
        var daysAgo = nextInt(30);
        var hoursAgo = nextInt(24);
        var minutesAgo = nextInt(60);
        var ts = new Date(now.getTime()
            - daysAgo * 86400000
            - hoursAgo * 3600000
            - minutesAgo * 60000);
        var expireAt = new Date(ts.getTime() + TTL_SECONDS * 1000);
        docs.push({
            keyword:    entry.kw,
            hitCount:   NumberLong("0"),
            durationMs: NumberLong(String(20 + nextInt(80))),
            createdAt:  ts,
            expiresAt:  expireAt,
            _class:     "com.example.skishop.inventory.model.SearchLog"
        });
    }
});

var result = db.search_logs.insertMany(docs);
print("[Seed] Inserted zero-hit search_logs:", Object.keys(result.insertedIds).length, "documents");
print("[Seed] Total search_logs:", db.search_logs.countDocuments());
print("[Seed] Zero-hit logs:", db.search_logs.countDocuments({ hitCount: 0 }));
