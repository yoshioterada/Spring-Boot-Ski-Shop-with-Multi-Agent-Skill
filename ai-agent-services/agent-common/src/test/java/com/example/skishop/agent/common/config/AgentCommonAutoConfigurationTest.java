package com.example.skishop.agent.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCommonAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentCommonAutoConfiguration.class));

    @Test
    void default_props_loaded_as_monolith() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AgentDeploymentProperties.class);
            var p = ctx.getBean(AgentDeploymentProperties.class);
            assertThat(p.deployment().isMonolith()).isTrue();
            assertThat(p.web().enabled()).isTrue();
        });
    }

    @Test
    void explicit_distributed_mode() {
        runner.withPropertyValues("agents.deployment.mode=distributed",
                                   "agents.web.enabled=false")
                .run(ctx -> {
                    var p = ctx.getBean(AgentDeploymentProperties.class);
                    assertThat(p.deployment().isDistributed()).isTrue();
                    assertThat(p.deployment().isMonolith()).isFalse();
                    assertThat(p.web().enabled()).isFalse();
                });
    }

    @Test
    void direct_constructor_null_args_apply_defaults() {
        var p = new AgentDeploymentProperties(null, null);
        assertThat(p.deployment()).isNotNull();
        assertThat(p.web()).isNotNull();
        assertThat(p.deployment().isMonolith()).isTrue();
        assertThat(p.web().enabled()).isTrue();
    }

    @Test
    void deployment_blank_mode_defaults_to_monolith() {
        var d1 = new AgentDeploymentProperties.Deployment(null);
        assertThat(d1.isMonolith()).isTrue();
        var d2 = new AgentDeploymentProperties.Deployment("");
        assertThat(d2.isMonolith()).isTrue();
    }

    @Test
    void web_null_enabled_defaults_to_true() {
        var w = new AgentDeploymentProperties.Web(null);
        assertThat(w.enabled()).isTrue();
    }
}
