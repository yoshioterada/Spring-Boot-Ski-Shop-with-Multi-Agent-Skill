package com.example.skishop.inventory.service;

import com.example.skishop.inventory.dto.SearchAnalyticsDto.DailySearchPoint;
import com.example.skishop.inventory.dto.SearchAnalyticsDto.PopularKeyword;
import com.example.skishop.inventory.dto.SearchAnalyticsDto.SearchAnalyticsSummary;
import com.example.skishop.inventory.model.SearchLog;
import com.example.skishop.inventory.repository.SearchLogRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 検索ログを集計し、管理画面の検索分析タブ用 DTO を生成する。
 *
 * <p>書き込み: {@link #logSearchAsync(String, long, long)} - 商品検索パスから fire-and-forget で呼び出す。</p>
 * <p>読み出し: {@link #getSummary(int, int)} - 集計クエリは MongoDB Aggregation で実行する。</p>
 */
@Service
public class SearchAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(SearchAnalyticsService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final int MAX_KEYWORD_LENGTH = 200;

    private final SearchLogRepository repository;
    private final MongoTemplate mongoTemplate;

    public SearchAnalyticsService(SearchLogRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 検索ログを非同期で記録する。失敗してもユーザー検索パスを止めない。
     */
    @Async
    public void logSearchAsync(String rawKeyword, long hitCount, long durationMs) {
        logSearchAsync(rawKeyword, null, "inventory-product-search", hitCount, durationMs);
    }

    @Async
    public void logSearchAsync(String rawKeyword, String enhancedKeyword, String source, long hitCount, long durationMs) {
        try {
            String normalized = normalize(rawKeyword);
            if (normalized.isEmpty()) {
                return;
            }
            String normalizedEnhanced = normalize(enhancedKeyword);
            String normalizedSource = normalizeSource(source);
            repository.save(new SearchLog(normalized, normalizedEnhanced, normalizedSource,
                    hitCount, Math.max(0L, durationMs)));
        } catch (RuntimeException ex) {
            log.warn("Failed to persist search log for keyword={}: {}", rawKeyword, ex.getMessage());
        }
    }

    public SearchAnalyticsSummary getSummary(int days, int popularLimit) {
        int safeDays = clampDays(days);
        int limit = popularLimit <= 0 ? 10 : Math.min(popularLimit, 50);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        Criteria sinceCriteria = Criteria.where("createdAt").gte(since);

        // ----- 全体集計 -----
        Aggregation totalsAgg = Aggregation.newAggregation(
                Aggregation.match(sinceCriteria),
                Aggregation.group()
                        .count().as("totalSearches")
                        .addToSet("keyword").as("keywords")
                        .sum(org.springframework.data.mongodb.core.aggregation.ConditionalOperators.when(
                                Criteria.where("hitCount").lte(0)).then(1).otherwise(0)).as("zeroHits")
                        .avg("durationMs").as("avgDuration")
        );
        Document totalsDoc = firstResult(mongoTemplate.aggregate(totalsAgg, "search_logs", Document.class));

        long totalSearches = totalsDoc != null ? toLong(totalsDoc.get("totalSearches")) : 0L;
        long uniqueKeywords = 0L;
        if (totalsDoc != null && totalsDoc.get("keywords") instanceof List<?> keywords) {
            uniqueKeywords = keywords.size();
        }
        long zeroHits = totalsDoc != null ? toLong(totalsDoc.get("zeroHits")) : 0L;
        double avgDuration = totalsDoc != null ? toDouble(totalsDoc.get("avgDuration")) : 0.0;
        double zeroHitRatio = totalSearches > 0 ? (double) zeroHits / (double) totalSearches : 0.0;

        // ----- 人気キーワード Top N -----
        Aggregation popularAgg = Aggregation.newAggregation(
                Aggregation.match(sinceCriteria),
                Aggregation.group("keyword")
                        .count().as("searches")
                        .sum("hitCount").as("totalHits")
                        .avg("hitCount").as("avgHits"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "searches")),
                Aggregation.limit(limit)
        );
        AggregationResults<Document> popularResults = mongoTemplate.aggregate(popularAgg, "search_logs", Document.class);
        List<PopularKeyword> popular = new ArrayList<>(limit);
        for (Document d : popularResults.getMappedResults()) {
            String keyword = String.valueOf(d.get("_id"));
            long searches = toLong(d.get("searches"));
            long totalHits = toLong(d.get("totalHits"));
            double avgHits = toDouble(d.get("avgHits"));
            popular.add(new PopularKeyword(keyword, searches, totalHits, avgHits));
        }

        // ----- 日別トレンド -----
        Aggregation trendAgg = Aggregation.newAggregation(
                Aggregation.match(sinceCriteria),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf("createdAt").toString("%Y-%m-%d")).as("date")
                        .and("hitCount").as("hitCount")
                        .and("durationMs").as("durationMs"),
                Aggregation.group("date")
                        .count().as("searches")
                        .sum(org.springframework.data.mongodb.core.aggregation.ConditionalOperators.when(
                                Criteria.where("hitCount").lte(0)).then(1).otherwise(0)).as("zeroHits")
                        .avg("durationMs").as("avgDuration"),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "_id"))
        );
        AggregationResults<Document> trendResults = mongoTemplate.aggregate(trendAgg, "search_logs", Document.class);
        Map<LocalDate, DailySearchPoint> map = new HashMap<>();
        for (Document d : trendResults.getMappedResults()) {
            LocalDate date = LocalDate.parse(String.valueOf(d.get("_id")));
            map.put(date, new DailySearchPoint(date, toLong(d.get("searches")),
                    toLong(d.get("zeroHits")), toDouble(d.get("avgDuration"))));
        }
        LocalDate today = LocalDate.now(ZONE);
        List<DailySearchPoint> trend = new ArrayList<>(safeDays);
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate dt = today.minusDays(i);
            trend.add(map.getOrDefault(dt, new DailySearchPoint(dt, 0L, 0L, 0.0)));
        }

        return new SearchAnalyticsSummary(safeDays, totalSearches, uniqueKeywords,
                zeroHitRatio, avgDuration, popular, trend);
    }

    // ---------------------- helpers ----------------------

    /**
     * F5 機会発見レーダー用: ゼロヒット (hitCount == 0) のキーワードを集約して返す。
     */
    public List<Map<String, Object>> getZeroHitAggregation(int days, int minCount, int limit) {
        int safeDays = clampDays(days);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(since).and("hitCount").lte(0)),
                Aggregation.group("keyword")
                        .count().as("searchCount")
                        .max("createdAt").as("lastSearchedAt"),
                Aggregation.match(Criteria.where("searchCount").gte(minCount)),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "searchCount")),
                Aggregation.limit(limit)
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "search_logs", Document.class);
        List<Map<String, Object>> response = new ArrayList<>(results.getMappedResults().size());
        for (Document doc : results.getMappedResults()) {
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", doc.get("_id"));
            item.put("searchCount", toLong(doc.get("searchCount")));
            Object lastSearched = doc.get("lastSearchedAt");
            if (lastSearched instanceof java.util.Date date) {
                item.put("lastSearchedAt", date.toInstant().toString());
            } else if (lastSearched instanceof Number number) {
                // MongoDB aggregation の max() が BsonDateTime → Long (エポックミリ秒) で返る場合に対応
                item.put("lastSearchedAt", Instant.ofEpochMilli(number.longValue()).toString());
            } else {
                item.put("lastSearchedAt", lastSearched != null ? lastSearched.toString() : Instant.now().toString());
            }
            response.add(item);
        }
        return response;
    }

    private String normalize(String rawKeyword) {
        if (rawKeyword == null) return "";
        String trimmed = rawKeyword.trim().toLowerCase();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            trimmed = trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed;
    }

    private String normalizeSource(String rawSource) {
        String normalized = normalize(rawSource);
        return normalized.isEmpty() ? "inventory-product-search" : normalized;
    }

    private int clampDays(int days) {
        if (days <= 0) return 30;
        return Math.min(days, 365);
    }

    private static Document firstResult(AggregationResults<Document> results) {
        List<Document> mapped = results.getMappedResults();
        return mapped.isEmpty() ? null : mapped.get(0);
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}
