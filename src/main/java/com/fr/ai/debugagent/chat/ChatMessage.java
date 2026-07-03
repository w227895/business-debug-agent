package com.fr.ai.debugagent.chat;

import com.fr.ai.debugagent.tool.ToolCallSummary;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessage(
        String role,
        String content,
        LocalDateTime createdAt,
        ChatTokenUsage tokenUsage,
        List<ToolCallSummary> toolCalls
) {
    public ChatMessage(String role, String content, LocalDateTime createdAt) {
        this(role, content, createdAt, ChatTokenUsage.empty(), List.of());
    }

    public ChatMessage(String role, String content, LocalDateTime createdAt, ChatTokenUsage tokenUsage) {
        this(role, content, createdAt, tokenUsage, List.of());
    }
}
