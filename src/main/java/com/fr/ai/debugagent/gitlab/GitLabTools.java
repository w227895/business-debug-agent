package com.fr.ai.debugagent.gitlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitLabTools {

    private final GitLabService gitLabService;
    private final ObjectMapper objectMapper;

    public GitLabTools(GitLabService gitLabService, ObjectMapper objectMapper) {
        this.gitLabService = gitLabService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "find_code_by_log",
            description = """
                    Find likely related GitLab source code from a log excerpt and return compact evidence.
                    Use this when the user pastes logs, stack traces, exception messages, interface paths, or error text and asks where the code problem is.
                    The tool extracts class names, methods, exceptions, and URL paths from logText, searches configured GitLab projects, reads the best matching source snippets, and returns a bounded JSON result.
                    serviceHint can be a service alias such as order, account, qim, or msg_center. If omitted, the tool tries to infer it from logText and configured projects.
                    ref defaults to the configured default branch. This tool is read-only and never modifies GitLab.
                    """)
    public String findCodeByLog(
            @ToolParam(description = "Log excerpt, stack trace, exception text, or interface path from the user") String logText,
            @ToolParam(description = "Optional service alias such as order, account, qim, or msg_center") String serviceHint,
            @ToolParam(description = "Optional branch, tag, or commit ref. Defaults to configured gitlab.default-ref") String ref) {
        GitLabCodeLookupResult result = gitLabService.findCodeByLog(logText, serviceHint, ref);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"message\":\"GitLab 代码定位结果序列化失败\"}";
        }
    }
}
