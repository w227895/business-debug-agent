package com.fr.ai.debugagent.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String sessionId,

        @NotBlank(message = "消息不能为空")
        String message
) {
}