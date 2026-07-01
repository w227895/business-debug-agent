package com.fr.ai.debugagent.chat;

import java.time.LocalDateTime;

public record ChatMessage(
        String role,
        String content,
        LocalDateTime createdAt,
        ChatTokenUsage tokenUsage
) {
    public ChatMessage(String role, String content, LocalDateTime createdAt) {
        this(role, content, createdAt, ChatTokenUsage.empty());
    }
}
