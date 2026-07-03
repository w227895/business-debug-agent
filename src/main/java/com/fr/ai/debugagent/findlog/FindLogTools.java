package com.fr.ai.debugagent.findlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class FindLogTools {

    private final FindLogService findLogService;
    private final ObjectMapper objectMapper;

    public FindLogTools(FindLogService findLogService, ObjectMapper objectMapper) {
        this.findLogService = findLogService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "list_findlog_services",
            description = """
                    List concrete FindLog service#machine values through devtool.flightroutes24.com.
                    Use this before search_findlog_logs when the user names a service, environment, machine, or gray lane but does not provide exact service#machine values.
                    The profile argument supports DEV and PROD. The keyword argument can be a service name such as order, account, qim, a machine such as deve/devk, or a gray lane name.
                    Return at most 30 concrete machine values.
                    """)
    public String listFindLogServices(
            @ToolParam(description = "FindLog profile: DEV for test/dev logs, PROD for production logs") String profile,
            @ToolParam(description = "Service, machine, or gray lane keyword to match") String keyword) {
        try {
            return objectMapper.writeValueAsString(findLogService.findServices(profile, keyword));
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    @Tool(
            name = "search_findlog_logs",
            description = """
                    Search FR FindLog logs through devtool.flightroutes24.com.
                    Use this when the user provides a traceId, keyword, exception, order id, or business symptom and asks to query dev/test or production logs.
                    The serviceValues argument must contain concrete machine-level values like order_deve#deve, separated by comma or whitespace. Maximum 3 machines per call.
                    If the user explicitly names a service, machine, environment, or gray lane, use that explicit target and do not infer serviceValues from traceId prefixes.
                    The profile argument supports DEV and PROD. If omitted, use DEV for test environment logs.
                    Keep time ranges narrow. Return summarized excerpts only.
                    """)
    public String searchFindLogLogs(
            @ToolParam(description = "FindLog profile: DEV for test/dev logs, PROD for production logs") String profile,
            @ToolParam(description = "Concrete service#machine values, comma or whitespace separated, maximum 3 machines") String serviceValues,
            @ToolParam(description = "Keyword, traceId, order id, or exception text to search") String keyword,
            @ToolParam(description = "Start time, format yyyy-MM-dd HH:mm:ss. Defaults to last 30 minutes if omitted") String startDate,
            @ToolParam(description = "End time, format yyyy-MM-dd HH:mm:ss. Defaults to now if omitted") String endDate,
            @ToolParam(description = "Context lines for grep -C, 0-80. Defaults to 20") String contextLines) {
        FindLogSearchResult result = findLogService.searchLogs(profile, serviceValues, keyword, startDate, endDate, contextLines);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"message\":\"FindLog 查询结果序列化失败\"}";
        }
    }
}
