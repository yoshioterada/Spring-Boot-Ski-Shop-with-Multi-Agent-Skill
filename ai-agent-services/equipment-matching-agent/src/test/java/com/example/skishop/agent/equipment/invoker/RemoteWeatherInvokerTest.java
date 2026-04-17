package com.example.skishop.agent.equipment.invoker;

import com.example.skishop.agent.common.dto.SkiFeasibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteWeatherInvokerTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:8100");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void getFeasibility_success() {
        server.expect(requestTo("http://localhost:8100/api/v1/agents/weather/feasibility?location=Naeba"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"feasibility":"HIGH","score":80,"recommendedGearLevel":"COLD",
                         "snowCondition":"POWDER","overallCondition":"EXCELLENT"}
                        """, MediaType.APPLICATION_JSON));

        var invoker = new RemoteWeatherInvoker(builder.build());
        SkiFeasibilityResult r = invoker.getFeasibility("Naeba");
        assertThat(r.feasibility()).isEqualTo("HIGH");
        assertThat(r.snowCondition()).isEqualTo("POWDER");
    }

    @Test
    void getFeasibility_returns_fallback_on_error() {
        server.expect(requestTo("http://localhost:8100/api/v1/agents/weather/feasibility?location=X"))
                .andRespond(withServerError());

        var invoker = new RemoteWeatherInvoker(builder.build());
        SkiFeasibilityResult r = invoker.getFeasibility("X");
        assertThat(r.feasibility()).isEqualTo("MEDIUM");
        assertThat(r.snowCondition()).isEqualTo("ALL_CONDITIONS");
    }

    @Test
    void config_constructor_creates_client_with_null_apikey() {
        var invoker = new RemoteWeatherInvoker("http://localhost:8100", null);
        assertThat(invoker).isNotNull();
    }

    @Test
    void config_constructor_creates_client_with_apikey() {
        var invoker = new RemoteWeatherInvoker("http://localhost:8100", "key");
        assertThat(invoker).isNotNull();
    }
}
