package com.fr.ai.debugagent.chat;

public record ChatTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public static ChatTokenUsage empty() {
        return new ChatTokenUsage(0, 0, 0);
    }
}
