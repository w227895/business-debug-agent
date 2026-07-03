package com.fr.ai.debugagent.chat;

import com.fr.ai.debugagent.tool.ToolCallSummary;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String sessionId,
        String reply,
        List<ChatMessage> messages,
        Map<String, String> memory,
        ChatTokenUsage tokenUsage,
        List<ToolCallSummary> toolCalls,
        ChatTokenUsage totalTokenUsage
) {
}
