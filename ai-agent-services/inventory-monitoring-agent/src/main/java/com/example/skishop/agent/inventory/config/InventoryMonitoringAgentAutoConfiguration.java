package com.example.skishop.agent.inventory.config;

import com.example.skishop.agent.inventory.client.InventoryManagementClient;
import com.example.skishop.agent.inventory.controller.InventoryMonitoringController;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import com.example.skishop.agent.inventory.tool.InventoryMonitoringToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    InventoryMonitoringAgentConfig.class,
    InventoryManagementClient.class,
    InventoryMonitoringToolService.class,
    InventoryMonitoringAgentService.class,
    InventoryMonitoringController.class,
    InventoryMonitoringAgentSecurityConfig.class
})
public class InventoryMonitoringAgentAutoConfiguration {
}
