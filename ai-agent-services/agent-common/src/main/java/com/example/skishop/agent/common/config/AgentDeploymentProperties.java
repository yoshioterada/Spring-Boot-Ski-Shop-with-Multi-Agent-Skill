package com.example.skishop.agent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-Agent System の動作モードを表現するプロパティ。
 * すべての Agent モジュール / Runtime から参照される。
 *
 * <p>例:
 * <pre>
 *   agents.deployment.mode=monolith   # or "distributed"
 *   agents.web.enabled=true
 * </pre>
 *
 * @param deployment 動作モード設定
 * @param web        Controller 公開 ON/OFF
 */
@ConfigurationProperties(prefix = "agents")
public record AgentDeploymentProperties(
        Deployment deployment,
        Web web
) {
    public AgentDeploymentProperties {
        if (deployment == null) deployment = new Deployment("monolith");
        if (web == null)        web = new Web(true);
    }

    public record Deployment(String mode) {
        public Deployment {
            if (mode == null || mode.isBlank()) mode = "monolith";
        }
        public boolean isMonolith()    { return "monolith".equalsIgnoreCase(mode); }
        public boolean isDistributed() { return "distributed".equalsIgnoreCase(mode); }
    }

    public record Web(Boolean enabled) {
        public Web { if (enabled == null) enabled = Boolean.TRUE; }
    }
}
