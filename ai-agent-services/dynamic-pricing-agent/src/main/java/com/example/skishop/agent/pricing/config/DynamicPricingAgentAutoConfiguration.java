package com.example.skishop.agent.pricing.config;

import com.example.skishop.agent.pricing.client.ProductCatalogClient;
import com.example.skishop.agent.pricing.client.SalesManagementClient;
import com.example.skishop.agent.pricing.controller.DynamicPricingController;
import com.example.skishop.agent.pricing.invoker.RemoteWeatherInvoker;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import com.example.skishop.agent.pricing.tool.DynamicPricingToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    DynamicPricingAgentConfig.class,
    ProductCatalogClient.class,
    SalesManagementClient.class,
    DynamicPricingToolService.class,
    DynamicPricingAgentService.class,
    RemoteWeatherInvoker.class,
    DynamicPricingController.class,
    DynamicPricingAgentSecurityConfig.class
})
public class DynamicPricingAgentAutoConfiguration {
}
