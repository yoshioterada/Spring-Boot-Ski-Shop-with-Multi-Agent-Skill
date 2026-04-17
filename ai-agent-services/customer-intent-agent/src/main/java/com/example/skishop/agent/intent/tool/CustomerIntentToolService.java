package com.example.skishop.agent.intent.tool;

import com.example.skishop.agent.common.dto.ExtractedConstraints;
import com.example.skishop.agent.common.dto.UserPurchaseHistory;
import com.example.skishop.agent.intent.client.UserProfileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class CustomerIntentToolService {

    private static final Logger log = LoggerFactory.getLogger(CustomerIntentToolService.class);
    private final UserProfileClient userProfileClient;

    public CustomerIntentToolService(UserProfileClient userProfileClient) {
        this.userProfileClient = userProfileClient;
    }

    @Tool(description = """
            顧客の自然言語メッセージを解析し、インテントカテゴリ
            (PURCHASE/RENTAL/ADVICE/SUPPORT) と主要な製品カテゴリを返す。最初に呼ぶこと。
            """)
    public String classifyIntent(
            @ToolParam(description = "顧客の自然言語メッセージ") String message) {
        log.info("Tool classifyIntent called, messageLength={}", message == null ? 0 : message.length());
        return message == null ? "" : message;
    }

    @Tool(description = """
            指定ユーザーの購入・レンタル履歴、スキルレベル、顧客ティアを取得する。
            userId が空文字の場合はフォールバック値を返す。
            """)
    public UserPurchaseHistory getUserPurchaseHistory(
            @ToolParam(description = "ユーザー ID") String userId) {
        log.info("Tool getUserPurchaseHistory called: userId={}", userId);
        if (userId == null || userId.isBlank()) {
            return UserProfileClient.fallback("anonymous");
        }
        return userProfileClient.getPurchaseHistory(userId);
    }

    @Tool(description = """
            抽出した制約情報の欠損を補完する。
            スキルレベル未指定 → BEGINNER、グループ人数未指定 → 1。
            """)
    public ExtractedConstraints validateAndFillConstraints(
            @ToolParam(description = "場所") @Nullable String destination,
            @ToolParam(description = "旅行開始日 ISO-8601") @Nullable String startDate,
            @ToolParam(description = "旅行終了日 ISO-8601") @Nullable String endDate,
            @ToolParam(description = "グループ人数") @Nullable Integer groupSize,
            @ToolParam(description = "スキルレベル") @Nullable String skillLevel,
            @ToolParam(description = "予算（円）") @Nullable Integer budgetYen,
            @ToolParam(description = "希望商品カテゴリ（カンマ区切り）") @Nullable String categories) {

        log.info("Tool validateAndFillConstraints called: destination={}", destination);

        LocalDate parsedStart = parseDate(startDate);
        LocalDate parsedEnd = parseDate(endDate);
        String resolvedSkill = (skillLevel != null && !skillLevel.isBlank())
                ? skillLevel.toUpperCase() : "BEGINNER";
        int resolvedGroupSize = (groupSize != null && groupSize > 0) ? groupSize : 1;
        List<String> categoryList = (categories != null && !categories.isBlank())
                ? Arrays.stream(categories.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        boolean hasCategory = !categoryList.isEmpty();

        return new ExtractedConstraints(
                destination, parsedStart, parsedEnd, resolvedGroupSize,
                resolvedSkill, budgetYen,
                /* includesRental */ !hasCategory,
                /* includesPurchase */ hasCategory,
                categoryList);
    }

    static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("日付パース失敗: {}", dateStr);
            return null;
        }
    }
}
