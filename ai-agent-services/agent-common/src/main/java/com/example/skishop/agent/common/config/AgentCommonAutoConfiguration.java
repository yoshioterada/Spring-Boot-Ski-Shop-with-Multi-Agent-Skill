package com.example.skishop.agent.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * agent-common 用 AutoConfiguration。
 * {@link AgentDeploymentProperties} を Bean として登録する。
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentDeploymentProperties.class)
public class AgentCommonAutoConfiguration {
}
