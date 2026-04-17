package com.example.skishop.agent.orchestrator.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrchestratorControllerTest {

    @Test
    void extractBearerToken_success() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");
        assertThat(OrchestratorController.extractBearerToken(req)).isEqualTo("abc.def.ghi");
    }

    @Test
    void extractBearerToken_returns_empty_when_missing() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        assertThat(OrchestratorController.extractBearerToken(req)).isEmpty();
    }

    @Test
    void extractBearerToken_returns_empty_when_not_bearer() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Basic xyz");
        assertThat(OrchestratorController.extractBearerToken(req)).isEmpty();
    }
}
