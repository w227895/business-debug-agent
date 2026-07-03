package com.fr.ai.debugagent.chat;

import com.fr.ai.debugagent.tool.ToolCallSummary;

import java.util.List;

public record AiModelReply(
        String content,
        ChatTokenUsage tokenUsage,
        String provider,
        String model,
        List<ToolCallSummary> toolCalls
) {
}
