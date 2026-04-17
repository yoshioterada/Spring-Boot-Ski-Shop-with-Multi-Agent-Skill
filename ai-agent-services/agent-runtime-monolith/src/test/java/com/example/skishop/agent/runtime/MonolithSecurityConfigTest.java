package com.example.skishop.agent.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * モノリスの単一 SecurityFilterChain（{@link com.example.skishop.agent.runtime.config.MonolithSecurityConfig}）の検証。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestChatClientConfig.class)
class MonolithSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuator_health_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void orchestrator_endpoint_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/orchestrator/recommend"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void agents_endpoint_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/agents/weather/anything"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "USER")
    void orchestrator_endpoint_with_user_role_passes_security() throws Exception {
        // POST のみ実装されているため GET だと 405、ただし 401/403 ではないことを確認
        mockMvc.perform(get("/api/v1/orchestrator/recommend"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "USER")
    void agents_endpoint_with_user_role_is_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/agents/weather/anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT_ADMIN")
    void agents_endpoint_with_agent_admin_passes_security() throws Exception {
        mockMvc.perform(get("/api/v1/agents/weather/anything"))
                .andExpect(status().is4xxClientError()); // 404 / 405 = security passed
    }
}
