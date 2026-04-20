package com.example.skishop.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * F5 ゼロヒット dismiss 状態管理 (spec § 20.10.2).
 * TTL 90 日で自動削除 → 集計対象に自動復帰 (D-F5-05).
 */
@Document("zero_hit_dismissals")
public class ZeroHitDismissal {

    @Id
    private String id;

    @Indexed(unique = true)
    private String normalizedKeyword;

    private String reason; // ALREADY_PROCURING | NOT_OUR_TARGET | OTHER

    private String memo;

    private String dismissedBy;

    private Instant dismissedAt;

    @Indexed(name = "ttl_dismissals", expireAfterSeconds = 0)
    private Instant expiresAt;

    public ZeroHitDismissal() {}

    public ZeroHitDismissal(String normalizedKeyword, String reason, String memo, String dismissedBy, int ttlDays) {
        this.normalizedKeyword = normalizedKeyword;
        this.reason = reason;
        this.memo = memo;
        this.dismissedBy = dismissedBy;
        this.dismissedAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds((long) ttlDays * 86400);
    }

    public String getId() { return id; }
    public String getNormalizedKeyword() { return normalizedKeyword; }
    public String getReason() { return reason; }
    public String getMemo() { return memo; }
    public String getDismissedBy() { return dismissedBy; }
    public Instant getDismissedAt() { return dismissedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
