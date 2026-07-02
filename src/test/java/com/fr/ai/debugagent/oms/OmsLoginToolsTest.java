package com.fr.ai.debugagent.oms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OmsLoginToolsTest {

    @Test
    void exposesLoginToOmsAsSpringAiTool() {
        OmsLoginService loginService = mock(OmsLoginService.class);
        when(loginService.login("deve")).thenReturn(new OmsLoginResult(
                "deve",
                true,
                "OMS 登录成功，Cookie 已缓存在服务端。",
                "sso-deve.flightroutes24.com",
                "oms-deve.flightroutes24.com",
                200,
                List.of("OMSTGC_deve", "UID_OMSTGC_deve", "JSESSIONID"),
                "OMSTGC_deve=***; UID_OMSTGC_deve=***; JSESSIONID=***",
                Instant.parse("2026-07-02T00:00:00Z")));

        ToolCallback callback = MethodToolCallbackProvider.builder()
                .toolObjects(new OmsLoginTools(loginService, new ObjectMapper().findAndRegisterModules()))
                .build()
                .getToolCallbacks()[0];

        String result = callback.call("{\"environment\":\"deve\"}");

        assertThat(callback.getToolDefinition().name()).isEqualTo("login_to_oms");
        assertThat(result).contains("\"success\":true");
        assertThat(result).contains("OMSTGC_deve=***");
        verify(loginService).login("deve");
    }
}
