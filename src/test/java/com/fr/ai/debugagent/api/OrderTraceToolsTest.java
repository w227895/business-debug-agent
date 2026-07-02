package com.fr.ai.debugagent.api;

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

class OrderTraceToolsTest {

    @Test
    void exposesExtractOrderTraceIdsAsSpringAiTool() {
        OrderTraceService traceService = mock(OrderTraceService.class);
        when(traceService.lookupTraceIds("17428182283024", "deve")).thenReturn(new OrderTraceLookupResult(
                "deve",
                true,
                "已从 order 状态日志中提取 traceId",
                "https://order-deve.flightroutes24.com/order/geAllStatusOrdertLogs.do",
                200,
                List.of("web_order_394298d795814bbb8f197bc48e8a4224.478.17822884429362143"),
                "{\"traceId\":\"web_order_394298d795814bbb8f197bc48e8a4224.478.17822884429362143\"}",
                Instant.parse("2026-07-02T00:00:00Z")));

        ToolCallback callback = MethodToolCallbackProvider.builder()
                .toolObjects(new OrderTraceTools(traceService, new ObjectMapper().findAndRegisterModules()))
                .build()
                .getToolCallbacks()[0];

        String result = callback.call("{\"parentId\":\"17428182283024\",\"environment\":\"deve\"}");

        assertThat(callback.getToolDefinition().name()).isEqualTo("extract_order_trace_ids");
        assertThat(result).contains("\"success\":true");
        assertThat(result).contains("web_order_394298d795814bbb8f197bc48e8a4224.478.17822884429362143");
        verify(traceService).lookupTraceIds("17428182283024", "deve");
    }
}
