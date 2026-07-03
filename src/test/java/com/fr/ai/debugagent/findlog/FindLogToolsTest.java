package com.fr.ai.debugagent.findlog;

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

class FindLogToolsTest {

    @Test
    void exposesSearchFindLogLogsAsSpringAiTool() {
        FindLogService findLogService = mock(FindLogService.class);
        when(findLogService.searchLogs(
                "DEV",
                "order_deve#deve",
                "trace-1",
                "2026-07-02 18:00:00",
                "2026-07-02 18:10:00",
                "20"))
                .thenReturn(new FindLogSearchResult(
                        "DEV",
                        true,
                        "FindLog 查询完成，已收集到日志片段",
                        List.of("order_deve#deve"),
                        "trace-1",
                        "2026-07-02 18:00:00",
                        "2026-07-02 18:10:00",
                        1,
                        List.of("matched log line"),
                        Instant.parse("2026-07-02T10:10:00Z")));

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new FindLogTools(findLogService, new ObjectMapper().findAndRegisterModules(), new FindLogRequestContext()))
                .build()
                .getToolCallbacks();
        ToolCallback callback = findCallback(callbacks, "search_findlog_logs");

        String result = callback.call("""
                {
                  "profile": "DEV",
                  "serviceValues": "order_deve#deve",
                  "keyword": "trace-1",
                  "startDate": "2026-07-02 18:00:00",
                  "endDate": "2026-07-02 18:10:00",
                  "contextLines": "20"
                }
                """);

        assertThat(callback.getToolDefinition().name()).isEqualTo("search_findlog_logs");
        assertThat(result).contains("\"success\":true");
        assertThat(result).contains("matched log line");
        verify(findLogService).searchLogs(
                "DEV",
                "order_deve#deve",
                "trace-1",
                "2026-07-02 18:00:00",
                "2026-07-02 18:10:00",
                "20");
    }

    @Test
    void searchUsesCurrentUserLogContextOverModelArguments() {
        FindLogService findLogService = mock(FindLogService.class);
        FindLogRequestContext requestContext = new FindLogRequestContext();
        when(findLogService.searchLogs(
                "DEV",
                "order_kb#devk",
                "web_order-deve_snake-7033111d0279488da8bc4bf4f9918877",
                "",
                "",
                "0"))
                .thenReturn(new FindLogSearchResult(
                        "DEV",
                        true,
                        "FindLog 查询完成，已收集到日志片段",
                        List.of("order_kb#devk"),
                        "web_order-deve_snake-7033111d0279488da8bc4bf4f9918877",
                        "2026-07-03 17:00:00",
                        "2026-07-03 17:30:00",
                        1,
                        List.of("matched new trace"),
                        Instant.parse("2026-07-03T09:30:00Z")));

        FindLogTools tools = new FindLogTools(findLogService, new ObjectMapper().findAndRegisterModules(), requestContext);

        try (FindLogRequestContext.Scope ignored = requestContext.use(
                "DEV",
                "order_kb#devk",
                "web_order-deve_snake-7033111d0279488da8bc4bf4f9918877")) {
            String result = tools.searchFindLogLogs(
                    "DEV",
                    "order_kb#devk",
                    "web_order-deve_snake-a0f0484dc7364435a8a04a15a8b4dab2",
                    "2026-07-03 17:00:00",
                    "2026-07-03 17:30:00",
                    "80");

            assertThat(result).contains("matched new trace");
        }

        verify(findLogService).searchLogs(
                "DEV",
                "order_kb#devk",
                "web_order-deve_snake-7033111d0279488da8bc4bf4f9918877",
                "",
                "",
                "0");
    }

    @Test
    void keepsExplicitUserTimeRangeFromModelArguments() {
        FindLogService findLogService = mock(FindLogService.class);
        FindLogRequestContext requestContext = new FindLogRequestContext();
        when(findLogService.searchLogs(
                "DEV",
                "order_kb#devk",
                "trace-1",
                "2026-07-03 10:00:00",
                "2026-07-03 11:00:00",
                "0"))
                .thenReturn(new FindLogSearchResult(
                        "DEV",
                        true,
                        "FindLog 查询完成，已收集到日志片段",
                        List.of("order_kb#devk"),
                        "trace-1",
                        "2026-07-03 10:00:00",
                        "2026-07-03 11:00:00",
                        1,
                        List.of("matched explicit time"),
                        Instant.parse("2026-07-03T03:00:00Z")));

        FindLogTools tools = new FindLogTools(findLogService, new ObjectMapper().findAndRegisterModules(), requestContext);

        try (FindLogRequestContext.Scope ignored = requestContext.use("DEV", "order_kb#devk", "trace-1", false)) {
            String result = tools.searchFindLogLogs(
                    "DEV",
                    "order_kb#devk",
                    "trace-1",
                    "2026-07-03 10:00:00",
                    "2026-07-03 11:00:00",
                    "0");

            assertThat(result).contains("matched explicit time");
        }

        verify(findLogService).searchLogs(
                "DEV",
                "order_kb#devk",
                "trace-1",
                "2026-07-03 10:00:00",
                "2026-07-03 11:00:00",
                "0");
    }

    private ToolCallback findCallback(ToolCallback[] callbacks, String name) {
        for (ToolCallback callback : callbacks) {
            if (name.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        throw new AssertionError("Tool callback not found: " + name);
    }
}
