package com.fr.ai.debugagent.chat;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionMemory {

    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> facts = new ConcurrentHashMap<>();

    public String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    public void addMessage(String sessionId, String role, String content) {
        messages.computeIfAbsent(sessionId, key -> new ArrayList<>())
                .add(new ChatMessage(role, content, LocalDateTime.now()));
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return List.copyOf(messages.getOrDefault(sessionId, List.of()));
    }

    public void remember(String sessionId, String key, String value) {
        facts.computeIfAbsent(sessionId, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    public Map<String, String> getFacts(String sessionId) {
        return Map.copyOf(facts.getOrDefault(sessionId, Map.of()));
    }
}