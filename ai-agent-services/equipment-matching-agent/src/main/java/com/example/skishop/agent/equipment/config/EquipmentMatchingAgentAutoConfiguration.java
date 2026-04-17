package com.example.skishop.agent.equipment.config;

import com.example.skishop.agent.equipment.client.InventoryClient;
import com.example.skishop.agent.equipment.controller.EquipmentMatchingController;
import com.example.skishop.agent.equipment.invoker.RemoteWeatherInvoker;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import com.example.skishop.agent.equipment.tool.EquipmentMatchingToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    EquipmentMatchingAgentConfig.class,
    InventoryClient.class,
    EquipmentMatchingToolService.class,
    EquipmentMatchingAgentService.class,
    RemoteWeatherInvoker.class,
    EquipmentMatchingController.class,
    EquipmentMatchingAgentSecurityConfig.class
})
public class EquipmentMatchingAgentAutoConfiguration {
}
