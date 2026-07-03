package com.fr.ai.debugagent.findlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FindLogTools {

    private final FindLogService findLogService;
    private final ObjectMapper objectMapper;
    private final FindLogRequestContext requestContext;

    public FindLogTools(FindLogService findLogService, ObjectMapper objectMapper, FindLogRequestContext requestContext) {
        this.findLogService = findLogService;
        this.objectMapper = objectMapper;
        this.requestContext = requestContext;
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
                    Use contextLines=0 for normal traceId or exact keyword searches so FindLog runs plain grep without -C. Only use context lines when the user explicitly asks for surrounding logs.
                    Keep time ranges narrow. Return summarized excerpts only.
                    """)
    public String searchFindLogLogs(
            @ToolParam(description = "FindLog profile: DEV for test/dev logs, PROD for production logs") String profile,
            @ToolParam(description = "Concrete service#machine values, comma or whitespace separated, maximum 3 machines") String serviceValues,
            @ToolParam(description = "Keyword, traceId, order id, or exception text to search") String keyword,
            @ToolParam(description = "Start time, format yyyy-MM-dd HH:mm:ss. Defaults to current time minus 12 hours if omitted") String startDate,
            @ToolParam(description = "End time, format yyyy-MM-dd HH:mm:ss. Defaults to now if omitted") String endDate,
            @ToolParam(description = "Context lines for grep -C, 0-80. Defaults to 0 for plain grep without -C") String contextLines) {
        FindLogRequestContext.Hints hints = requestContext.current();
        FindLogSearchResult result = findLogService.searchLogs(
                override(profile, hints == null ? null : hints.profile()),
                override(serviceValues, hints == null ? null : hints.serviceValues()),
                override(keyword, hints == null ? null : hints.keyword()),
                overrideDate(startDate, hints),
                overrideDate(endDate, hints),
                overrideContextLines(contextLines, hints));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"message\":\"FindLog 查询结果序列化失败\"}";
        }
    }

    private String override(String value, String forcedValue) {
        return StringUtils.hasText(forcedValue) ? forcedValue : value;
    }

    private String overrideContextLines(String contextLines, FindLogRequestContext.Hints hints) {
        return hints != null && StringUtils.hasText(hints.keyword()) ? "0" : contextLines;
    }

    private String overrideDate(String date, FindLogRequestContext.Hints hints) {
        return hints != null && hints.forceSystemTimeRange() ? "" : date;
    }
}
