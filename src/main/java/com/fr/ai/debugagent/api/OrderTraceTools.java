package com.fr.ai.debugagent.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTraceTools {

    private final OrderTraceService traceService;
    private final ObjectMapper objectMapper;

    public OrderTraceTools(OrderTraceService traceService, ObjectMapper objectMapper) {
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "extract_order_trace_ids",
            description = """
                    Query the FR order status-log endpoint and extract traceId values.
                    Use this when the user provides an OMS order parentId and asks for traceId, order logs, or follow-up log diagnosis.
                    The endpoint is /order/geAllStatusOrdertLogs.do and the environment supports devb, deve, and prod.
                    If environment is omitted, use the current page-selected environment from the system prompt.
                    Requires an OMS Cookie already cached for the same environment.
                    """)
    public String extractOrderTraceIds(
            @ToolParam(description = "OMS order parentId, for example 17428182283024") String parentId,
            @ToolParam(description = "Environment code: devb, deve, or prod") String environment) {
        OrderTraceLookupResult result = traceService.lookupTraceIds(parentId, environment);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"message\":\"traceId 查询结果序列化失败\"}";
        }
    }
}
