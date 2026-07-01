package com.fr.ai.debugagent.chat;

import java.time.LocalDateTime;

public record ChatSessionSummary(
        String sessionId,
        String title,
        LocalDateTime updatedAt
) {
}
