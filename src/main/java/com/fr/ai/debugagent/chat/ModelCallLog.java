package com.fr.ai.debugagent.chat;

import java.time.LocalDateTime;

public record ModelCallLog(
        long id,
        String sessionId,
        String provider,
        String model,
        String requestMessagesJson,
        String responseText,
        boolean success,
        String errorType,
        String errorMessage,
        long durationMillis,
        ChatTokenUsage tokenUsage,
        LocalDateTime createdAt
) {
}
