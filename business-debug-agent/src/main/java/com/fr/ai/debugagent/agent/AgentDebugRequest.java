package com.fr.ai.debugagent.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentDebugRequest(
        @NotBlank(message = "请描述要调试的问题")
        String userMessage,

        @NotBlank(message = "请粘贴业务文本或邮件正文")
        String businessText,

        String expectedJson
) {
}
