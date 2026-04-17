package com.example.skishop.agent.orchestrator.config;

import com.example.skishop.agent.orchestrator.client.PaymentCartClient;
import com.example.skishop.agent.orchestrator.client.UserManagementClient;
import com.example.skishop.agent.orchestrator.client.WorkerAgentRestClient;
import com.example.skishop.agent.orchestrator.controller.OrchestratorController;
import com.example.skishop.agent.orchestrator.invoker.LocalWorkerAgentInvoker;
import com.example.skishop.agent.orchestrator.invoker.RemoteWorkerAgentInvoker;
import com.example.skishop.agent.orchestrator.service.OrchestratorAgentService;
import com.example.skishop.agent.orchestrator.tool.OrchestratorWorkerTools;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    OrchestratorAgentConfig.class,
    OrchestratorWorkerTools.class,
    OrchestratorAgentService.class,
    UserManagementClient.class,
    PaymentCartClient.class,
    LocalWorkerAgentInvoker.class,
    RemoteWorkerAgentInvoker.class,
    WorkerAgentRestClient.class,
    OrchestratorController.class,
    OrchestratorSecurityConfig.class
})
public class OrchestratorAgentAutoConfiguration {
}
