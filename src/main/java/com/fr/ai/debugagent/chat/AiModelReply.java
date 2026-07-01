package com.fr.ai.debugagent.chat;

public record AiModelReply(
        String content,
        ChatTokenUsage tokenUsage
) {
}
