package com.example.skishop.agent.inventory.service;

import com.example.skishop.agent.common.dto.InventoryAnalysisResult;
import com.example.skishop.agent.common.dto.InventoryCheckRequest;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.inventory.client.InventoryManagementClient;
import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InventoryMonitoringAgentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitoringAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたは在庫管理 AI エージェントです。
            提供ツールを使い、在庫状況確認と代替提案を行ってください。
            """;

    private static final String ANALYZE_SYSTEM_PROMPT = """
            あなたはスキー用品店の在庫監視 AI エージェントです。
            管理者から提示された商品リストの在庫状況を分析し、構造化 JSON で回答してください。

            **手順**
            1. 各商品について severity を判定し alert を生成する
               - OUT_OF_STOCK → severity=CRITICAL
               - LOW_STOCK    → severity=WARNING
               - AVAILABLE で stock <= 必要数量 * 2 → severity=INFO
               - それ以外      → alert は null
            2. OUT_OF_STOCK / LOW_STOCK の商品については、必ず getAlternativeProducts ツールを呼び、
               alternativeProductIds に最大3件設定する。
               ※ category / skillLevel が不明な場合は空文字列で呼び出してよい。
            3. 全体傾向を 100〜200 文字の日本語で overallSummary に書く。
               例: 「上級者向け商品（ATOMIC Redster 系）に在庫切れが集中しています。週末の需要増を控え、
               早期発注または代替商品（SALOMON S/Race 系）への誘導が有効です。」
            4. 管理者が即座に取るべきアクションを 1〜2 文で recommendedAction に書く。
               例: 「在庫切れ 2 商品の発注を即時起票し、代替商品を商品ページのおすすめ枠に表示してください。」

            **重要な制約**
            ・items 配列の productId / productName / stockQuantity / availabilityStatus / isReservable /
              estimatedRestockDate は入力値をそのままコピーすること（改変禁止）
            ・JSON 以外の出力（前置き・後書き・コードフェンス）は禁止
            """;

    private final ChatClient chatClient;
    private final InventoryMonitoringToolService toolService;
    private final InventoryManagementClient inventoryClient;

    public InventoryMonitoringAgentService(
            @Qualifier("inventoryAgentChatClient") ChatClient chatClient,
            InventoryMonitoringToolService toolService,
            InventoryManagementClient inventoryClient) {
        this.chatClient = chatClient;
        this.toolService = toolService;
        this.inventoryClient = inventoryClient;
    }

    public List<InventoryStatus> checkAndRoute(InventoryCheckRequest request) {
        log.info("InventoryMonitoringAgent checkAndRoute: {} products", request.productIds().size());
        return toolService.checkInventoryAvailability(request.productIds(), request.requiredQuantity());
    }

    /**
     * AI エージェントによる在庫分析。
     * <p>
     * 1) ツールで決定論的に在庫データを取得 → 2) ChatClient (LLM) に渡してアラート分類・代替提案・サマリ生成。
     * LLM 呼び出しに失敗した場合は {@link #fallbackAnalysis(List)} で決定論的結果を返す（運用継続性確保）。
     * </p>
     */
    public InventoryAnalysisResult analyzeInventory(InventoryCheckRequest request) {
        log.info("InventoryMonitoringAgent analyzeInventory: {} products, requiredQty={}",
                request.productIds().size(), request.requiredQuantity());

        var rawStatuses = toolService.checkInventoryAvailability(
                request.productIds(), request.requiredQuantity());

        if (rawStatuses.isEmpty()) {
            return new InventoryAnalysisResult(List.of(), "対象商品が指定されていません。", "", Map.of());
        }

        InventoryAnalysisResult llmResult = null;
        try {
            llmResult = chatClient.prompt()
                    .system(ANALYZE_SYSTEM_PROMPT)
                    .user(buildAnalyzePrompt(rawStatuses, request.requiredQuantity()))
                    .tools(toolService)
                    .call()
                    .entity(InventoryAnalysisResult.class);
        } catch (RuntimeException e) {
            log.warn("LLM analyzeInventory failed ({}); falling back to deterministic output",
                    e.getMessage());
        }

        InventoryAnalysisResult base;
        if (llmResult == null || llmResult.items() == null || llmResult.items().isEmpty()) {
            log.warn("LLM returned empty/null analysis; using fallback");
            base = fallbackAnalysis(rawStatuses);
        } else {
            base = llmResult;
        }

        // 全 ID（選択商品 + 代替商品）の名前ルックアップマップを構築
        Map<String, String> productNames = resolveProductNames(base.items(), rawStatuses);
        log.info("InventoryMonitoringAgent analyzeInventory completed: items={}, names={}",
                base.items().size(), productNames.size());
        return new InventoryAnalysisResult(
                base.items(), base.overallSummary(), base.recommendedAction(), productNames);
    }

    public ReservationResult reserve(ReservationRequest request) {
        log.info("InventoryMonitoringAgent reserve: orderId={}", request.orderId());
        return toolService.reserveInventory(
                request.orderId(), request.userId(), request.items(), request.reservationTtlMinutes());
    }

    public String suggestAlternatives(List<InventoryStatus> unavailableItems) {
        log.info("InventoryMonitoringAgent suggestAlternatives: {} items", unavailableItems.size());
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("以下の製品が在庫不足です。代替品を提案してください: " + unavailableItems)
                .tools(toolService)
                .call()
                .content();
    }

    /**
     * LLM が利用できない場合のフォールバック。決定論的な短いサマリのみ返す。
     */
    static InventoryAnalysisResult fallbackAnalysis(List<InventoryStatus> rawStatuses) {
        long out = rawStatuses.stream().filter(s -> "OUT_OF_STOCK".equals(s.availabilityStatus())).count();
        long low = rawStatuses.stream().filter(s -> "LOW_STOCK".equals(s.availabilityStatus())).count();
        String summary = "対象 %d 商品のうち、在庫切れ %d 件、在庫僅少 %d 件です。"
                .formatted(rawStatuses.size(), out, low);
        String action;
        if (out > 0) {
            action = "在庫切れ商品の補充発注を最優先で実施してください。";
        } else if (low > 0) {
            action = "在庫僅少商品の入荷予定を確認し、必要に応じて発注してください。";
        } else {
            action = "全商品で十分な在庫があります。継続監視のみで問題ありません。";
        }
        return new InventoryAnalysisResult(rawStatuses, summary, action, Map.of());
    }

    /**
     * items の productId と各 item の alternativeProductIds に含まれる全 ID について、
     * 商品名を inventory-management-service から取得しマップとして返す。
     * <p>
     * 既に rawStatuses に含まれる商品はそこから productName を流用してリモート呼び出しを節約する。
     * 名前解決に失敗した場合（取得失敗・null 名）はマップに含めない（フロントは ID 表示にフォールバック）。
     * </p>
     */
    private Map<String, String> resolveProductNames(
            List<InventoryStatus> items, List<InventoryStatus> rawStatuses) {
        Map<String, String> names = new HashMap<>();

        // 既知の名前（rawStatuses）を登録
        for (InventoryStatus s : rawStatuses) {
            if (s.productName() != null && !s.productName().isBlank()) {
                names.put(s.productId(), s.productName());
            }
        }

        // 解決対象 ID 収集
        Set<String> toResolve = new HashSet<>();
        for (InventoryStatus item : items) {
            if (item.productId() != null && !names.containsKey(item.productId())) {
                toResolve.add(item.productId());
            }
            if (item.alternativeProductIds() != null) {
                for (String altId : item.alternativeProductIds()) {
                    if (altId != null && !altId.isBlank() && !names.containsKey(altId)) {
                        toResolve.add(altId);
                    }
                }
            }
        }

        // 未解決 ID を inventory client で解決
        for (String id : toResolve) {
            try {
                InventoryStatus s = inventoryClient.getStock(id);
                if (s != null && s.productName() != null && !s.productName().isBlank()
                        && !"(unknown)".equals(s.productName())) {
                    names.put(id, s.productName());
                }
            } catch (RuntimeException e) {
                log.debug("商品名解決失敗 productId={}: {}", id, e.getMessage());
            }
        }
        return names;
    }

    private static String buildAnalyzePrompt(List<InventoryStatus> rawStatuses, int requiredQuantity) {
        String table = rawStatuses.stream()
                .map(s -> "- productId=%s | productName=%s | stock=%d | status=%s | isReservable=%s | restock=%s"
                        .formatted(
                                s.productId(),
                                s.productName(),
                                s.stockQuantity(),
                                s.availabilityStatus(),
                                s.isReservable(),
                                s.estimatedRestockDate() == null ? "未定" : s.estimatedRestockDate()))
                .collect(Collectors.joining("\n"));

        return """
                必要数量: %d

                対象商品:
                %s

                上記を分析して、定義済み JSON スキーマで結果を返してください。
                """.formatted(requiredQuantity, table);
    }
}
