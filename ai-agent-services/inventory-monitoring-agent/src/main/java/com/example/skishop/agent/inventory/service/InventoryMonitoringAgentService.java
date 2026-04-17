package com.example.skishop.agent.inventory.service;

import com.example.skishop.agent.common.dto.InventoryCheckRequest;
import com.example.skishop.agent.common.dto.InventoryStatus;
import com.example.skishop.agent.common.dto.ReservationRequest;
import com.example.skishop.agent.common.dto.ReservationResult;
import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

public class InventoryMonitoringAgentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryMonitoringAgentService.class);

    private static final String SYSTEM_PROMPT = """
            あなたは在庫管理 AI エージェントです。
            提供ツールを使い、在庫状況確認と代替提案を行ってください。
            """;

    private final ChatClient chatClient;
    private final InventoryMonitoringToolService toolService;

    public InventoryMonitoringAgentService(
            @Qualifier("inventoryAgentChatClient") ChatClient chatClient,
            InventoryMonitoringToolService toolService) {
        this.chatClient = chatClient;
        this.toolService = toolService;
    }

    public List<InventoryStatus> checkAndRoute(InventoryCheckRequest request) {
        log.info("InventoryMonitoringAgent checkAndRoute: {} products", request.productIds().size());
        return toolService.checkInventoryAvailability(request.productIds(), request.requiredQuantity());
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
}
