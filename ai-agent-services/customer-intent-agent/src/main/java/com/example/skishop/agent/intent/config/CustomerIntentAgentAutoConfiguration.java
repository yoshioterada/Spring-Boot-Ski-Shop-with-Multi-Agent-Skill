package com.example.skishop.agent.intent.config;

import com.example.skishop.agent.intent.client.UserProfileClient;
import com.example.skishop.agent.intent.controller.CustomerIntentController;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import com.example.skishop.agent.intent.tool.CustomerIntentToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    CustomerIntentAgentConfig.class,
    UserProfileClient.class,
    CustomerIntentToolService.class,
    CustomerIntentAgentService.class,
    CustomerIntentController.class,
    CustomerIntentAgentSecurityConfig.class
})
public class CustomerIntentAgentAutoConfiguration {
}
