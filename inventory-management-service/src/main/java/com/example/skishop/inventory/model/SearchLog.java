package com.example.skishop.inventory.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 商品検索ログ。
 * 管理画面の検索分析タブで「人気検索キーワード」「ヒット率」「平均応答時間」等を集計するために蓄積する。
 *
 * <p>パフォーマンス考慮:</p>
 * <ul>
 *   <li>個人を特定する情報 (userId, IP) は格納しない (匿名ログ)</li>
 *   <li>TTL インデックスで 90 日経過後に自動削除 (PII 保護＋ストレージ管理)</li>
 *   <li>検索パスから書き込みは fire-and-forget (非同期) で行う</li>
 * </ul>
 */
@Document(collection = "search_logs")
public class SearchLog {

    /** TTL: 90 日 (秒)。Mongo の TTL モニタは createdAt が経過時刻を超えたドキュメントを削除する。 */
    public static final int TTL_SECONDS = 60 * 60 * 24 * 90;

    @Id
    private String id;

    /** 正規化された検索キーワード (lower-case + trim)。 */
    @Indexed
    private String keyword;

    /** 検索ヒット件数 (Page.totalElements)。0 のときはノーヒット検索。 */
    private long hitCount;

    /** 検索処理に要した時間 (ミリ秒)。 */
    private long durationMs;

    /** 検索実行日時。TTL インデックス対象。 */
    @Indexed(name = "search_logs_createdAt_ttl", expireAfterSeconds = TTL_SECONDS)
    @CreatedDate
    private Instant createdAt;

    public SearchLog() {}

    public SearchLog(String keyword, long hitCount, long durationMs) {
        this.keyword = keyword;
        this.hitCount = hitCount;
        this.durationMs = durationMs;
    }

    public String getId() { return id; }
    public String getKeyword() { return keyword; }
    public long getHitCount() { return hitCount; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public void setHitCount(long hitCount) { this.hitCount = hitCount; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
