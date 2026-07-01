package com.fr.ai.debugagent.chat;

import jakarta.validation.constraints.NotNull;

public record AiModelSwitchRequest(
        @NotNull(message = "modelId 不能为空")
        Long modelId
) {
}
