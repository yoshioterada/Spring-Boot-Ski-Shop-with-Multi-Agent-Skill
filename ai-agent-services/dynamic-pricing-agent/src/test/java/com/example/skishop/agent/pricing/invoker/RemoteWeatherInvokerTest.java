package com.example.skishop.agent.pricing.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteWeatherInvokerTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RemoteWeatherInvoker invoker;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8100");
        server = MockRestServiceServer.bindTo(builder).build();
        invoker = new RemoteWeatherInvoker(builder.build());
    }

    @Test
    void getFeasibility_success() {
        server.expect(requestTo("http://localhost:8100/api/v1/agents/weather/feasibility?location=Naeba"))
                .andRespond(withSuccess("""
                        {"feasibility":"HIGH","score":85,"recommendedGearLevel":"COLD",
                        "snowCondition":"POWDER","overallCondition":"EXCELLENT"}
                        """, MediaType.APPLICATION_JSON));
        SkiFeasibilityResult result = invoker.getFeasibility("Naeba");
        assertThat(result.feasibility()).isEqualTo("HIGH");
        assertThat(result.overallCondition()).isEqualTo("EXCELLENT");
    }

    @Test
    void getFeasibility_returns_fallback_on_error() {
        server.expect(requestTo("http://localhost:8100/api/v1/agents/weather/feasibility?location=X"))
                .andRespond(withServerError());
        SkiFeasibilityResult result = invoker.getFeasibility("X");
        assertThat(result.feasibility()).isEqualTo("MEDIUM");
        assertThat(result.overallCondition()).isEqualTo("GOOD");
    }

    @Test
    void getOverallCondition_default_method_works() {
        server.expect(requestTo("http://localhost:8100/api/v1/agents/weather/feasibility?location=Y"))
                .andRespond(withSuccess("""
                        {"feasibility":"LOW","score":30,"recommendedGearLevel":"WARM",
                        "snowCondition":"SLUSH","overallCondition":"POOR"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(invoker.getOverallCondition("Y")).isEqualTo("POOR");
    }

    @Test
    void config_constructor_handles_null_apikey() {
        assertThat(new RemoteWeatherInvoker("http://localhost:8100", null)).isNotNull();
    }
}
