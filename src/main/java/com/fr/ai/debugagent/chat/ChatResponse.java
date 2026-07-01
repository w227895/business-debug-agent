package com.fr.ai.debugagent.chat;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String sessionId,
        String reply,
        List<ChatMessage> messages,
        Map<String, String> memory
) {
}