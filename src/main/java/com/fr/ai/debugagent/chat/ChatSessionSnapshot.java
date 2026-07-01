package com.fr.ai.debugagent.chat;

import java.util.List;
import java.util.Map;

public record ChatSessionSnapshot(
        String sessionId,
        List<ChatMessage> messages,
        Map<String, String> memory
) {
}
