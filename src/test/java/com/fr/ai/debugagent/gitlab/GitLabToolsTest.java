package com.fr.ai.debugagent.gitlab;

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

class GitLabToolsTest {

    @Test
    void exposesFindCodeByLogAsSpringAiTool() {
        GitLabService gitLabService = mock(GitLabService.class);
        when(gitLabService.findCodeByLog("OrderController exception", "order", "master"))
                .thenReturn(new GitLabCodeLookupResult(
                        true,
                        "已根据日志找到候选代码，请结合 filePath、keyword 和 excerpt 给出定位结论",
                        "order",
                        "master",
                        List.of("OrderController"),
                        List.of("group/order"),
                        List.of(new GitLabCodeMatch(
                                "order",
                                "group/order",
                                "src/main/java/com/fr/order/OrderController.java",
                                "master",
                                "OrderController",
                                60,
                                false,
                                "class OrderController {}")),
                        Instant.parse("2026-07-03T10:00:00Z")));

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new GitLabTools(gitLabService, new ObjectMapper().findAndRegisterModules()))
                .build()
                .getToolCallbacks();
        ToolCallback callback = findCallback(callbacks, "find_code_by_log");

        String result = callback.call("""
                {
                  "logText": "OrderController exception",
                  "serviceHint": "order",
                  "ref": "master"
                }
                """);

        assertThat(callback.getToolDefinition().name()).isEqualTo("find_code_by_log");
        assertThat(result).contains("\"success\":true");
        assertThat(result).contains("OrderController.java");
        verify(gitLabService).findCodeByLog("OrderController exception", "order", "master");
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
