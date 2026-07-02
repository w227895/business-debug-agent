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
                .toolObjects(new FindLogTools(findLogService, new ObjectMapper().findAndRegisterModules()))
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

    private ToolCallback findCallback(ToolCallback[] callbacks, String name) {
        for (ToolCallback callback : callbacks) {
            if (name.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        throw new AssertionError("Tool callback not found: " + name);
    }
}
