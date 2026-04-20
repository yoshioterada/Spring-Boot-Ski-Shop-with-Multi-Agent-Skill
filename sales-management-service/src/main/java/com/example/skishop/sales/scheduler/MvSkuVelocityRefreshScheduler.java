package com.example.skishop.sales.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI Analyzer F4 — mv_sku_velocity マテリアライズドビューの定期 REFRESH.
 * <p>
 * spec § 19.8.1 + impl-plan P0.2.2: 1 時間に 1 回、CONCURRENTLY オプションで再構築.
 * UNIQUE INDEX が事前に存在することが CONCURRENTLY の前提（V8 で作成済み）.
 */
@Component
public class MvSkuVelocityRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MvSkuVelocityRefreshScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public MvSkuVelocityRefreshScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 起動 5 分後 + 1 時間ごとに mv_sku_velocity を REFRESH.
     */
    @Scheduled(initialDelay = 5 * 60 * 1000L, fixedRate = 60 * 60 * 1000L)
    public void refresh() {
        long started = System.currentTimeMillis();
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_sku_velocity");
            log.info("Refreshed mv_sku_velocity in {} ms", System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("Failed to refresh mv_sku_velocity: {}", e.getMessage(), e);
        }
    }
}
