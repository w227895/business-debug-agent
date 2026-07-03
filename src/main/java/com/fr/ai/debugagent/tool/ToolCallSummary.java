package com.fr.ai.debugagent.tool;

public record ToolCallSummary(
        String name,
        boolean success,
        long durationMillis
) {
}
