package com.example.skishop.agent.runtime;

import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import com.example.skishop.agent.equipment.service.EquipmentMatchingAgentService;
import com.example.skishop.agent.intent.service.CustomerIntentAgentService;
import com.example.skishop.agent.inventory.service.InventoryMonitoringAgentService;
import com.example.skishop.agent.orchestrator.invoker.LocalWorkerAgentInvoker;
import com.example.skishop.agent.orchestrator.invoker.RemoteWorkerAgentInvoker;
import com.example.skishop.agent.orchestrator.invoker.WorkerAgentInvoker;
import com.example.skishop.agent.orchestrator.service.OrchestratorAgentService;
import com.example.skishop.agent.pricing.service.DynamicPricingAgentService;
import com.example.skishop.agent.weather.service.WeatherAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * モノリス起動コンテキストの最小検証:
 * <ol>
 *   <li>Spring Context が立ち上がること</li>
 *   <li>7 Worker AgentService Bean がすべて登録されていること（Phase 6 P1 落とし穴チェック）</li>
 *   <li>OrchestratorAgentService Bean が登録されていること</li>
 *   <li>{@link LocalWorkerAgentInvoker} が登録され、{@link RemoteWorkerAgentInvoker} が登録されないこと
 *       （`agents.deployment.mode=monolith` モード判定の検証）</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestChatClientConfig.class)
class AgentRuntimeApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void context_loads() {
        assertThat(context).isNotNull();
    }

    @Test
    void all_seven_worker_agent_services_are_registered() {
        assertThat(context.getBean(WeatherAgentService.class)).isNotNull();
        assertThat(context.getBean(CustomerIntentAgentService.class)).isNotNull();
        assertThat(context.getBean(EquipmentMatchingAgentService.class)).isNotNull();
        assertThat(context.getBean(InventoryMonitoringAgentService.class)).isNotNull();
        assertThat(context.getBean(DynamicPricingAgentService.class)).isNotNull();
        assertThat(context.getBean(CouponOptimizationAgentService.class)).isNotNull();
        assertThat(context.getBean(OrchestratorAgentService.class)).isNotNull();
    }

    @Test
    void monolith_mode_registers_local_invoker_only() {
        WorkerAgentInvoker invoker = context.getBean(WorkerAgentInvoker.class);
        assertThat(invoker).isInstanceOf(LocalWorkerAgentInvoker.class);
        assertThatThrownBy(() -> context.getBean(RemoteWorkerAgentInvoker.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
