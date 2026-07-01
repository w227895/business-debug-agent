package com.fr.ai.debugagent.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SimpleChatAgent {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?:我叫|我的名字是|我是)\\s*([\\u4e00-\\u9fa5A-Za-z0-9_-]{1,20})");
    private static final Pattern GOAL_PATTERN = Pattern.compile("(?:目标是|我要做|我想做)\\s*(.+)");
    private static final int MAX_HISTORY_MESSAGES = 30;

    private final ChatSessionMemory memory;
    private final AiModelClient modelClient;
    private final ObjectMapper objectMapper;

    public SimpleChatAgent(
            ChatSessionMemory memory,
            AiModelClient modelClient,
            ObjectMapper objectMapper) {
        this.memory = memory;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public com.fr.ai.debugagent.chat.ChatResponse chat(ChatRequest request) {
        String sessionId = memory.normalizeSessionId(request.sessionId());
        String userMessage = request.message().trim();

        memory.addMessage(sessionId, "user", userMessage);
        captureFacts(sessionId, userMessage);

        AiModelReply aiReply = callModel(sessionId);
        memory.addMessage(sessionId, "assistant", aiReply.content(), aiReply.tokenUsage());

        return new com.fr.ai.debugagent.chat.ChatResponse(
                sessionId,
                aiReply.content(),
                memory.getMessages(sessionId),
                memory.getFacts(sessionId),
                aiReply.tokenUsage(),
                memory.getTotalTokenUsage(sessionId)
        );
    }

    public ChatSessionSnapshot getSession(String sessionId) {
        String normalizedSessionId = memory.normalizeSessionId(sessionId);
        return new ChatSessionSnapshot(
                normalizedSessionId,
                memory.getMessages(normalizedSessionId),
                memory.getFacts(normalizedSessionId),
                memory.getTotalTokenUsage(normalizedSessionId)
        );
    }

    public List<ChatSessionSummary> listSessions() {
        return memory.listSessions();
    }

    public List<ModelCallLog> getModelCallLogs(String sessionId) {
        return memory.getModelCallLogs(memory.normalizeSessionId(sessionId));
    }

    private AiModelReply callModel(String sessionId) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage("""
                你是一个支持上下文记忆的业务对话 Agent。
                要求：
                1. 必须结合本轮会话历史回答。
                2. 如果用户问你记得什么，要基于历史消息总结你记住的信息。
                3. 不要声称自己没有记忆；当前 prompt 中已经包含同一 session 的历史消息。
                4. 回答使用中文，简洁直接。
                """));

        List<ChatMessage> history = memory.getMessages(sessionId);
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (ChatMessage item : history.subList(start, history.size())) {
            if ("assistant".equals(item.role())) {
                promptMessages.add(new AssistantMessage(item.content()));
            } else {
                promptMessages.add(new UserMessage(item.content()));
            }
        }

        String requestMessagesJson = toRequestMessagesJson(promptMessages);
        long startedAt = System.nanoTime();
        try {
            AiModelReply reply = modelClient.call(promptMessages);
            memory.addModelCallLog(
                    sessionId,
                    modelClient.provider(),
                    modelClient.model(),
                    requestMessagesJson,
                    reply.content(),
                    true,
                    null,
                    null,
                    elapsedMillis(startedAt),
                    reply.tokenUsage());
            return reply;
        } catch (RuntimeException ex) {
            memory.addModelCallLog(
                    sessionId,
                    modelClient.provider(),
                    modelClient.model(),
                    requestMessagesJson,
                    null,
                    false,
                    ex.getClass().getName(),
                    rootMessage(ex),
                    elapsedMillis(startedAt),
                    ChatTokenUsage.empty());
            throw ex;
        }
    }

    private void captureFacts(String sessionId, String message) {
        Matcher nameMatcher = NAME_PATTERN.matcher(message);
        if (nameMatcher.find()) {
            memory.remember(sessionId, "name", nameMatcher.group(1));
        }

        Matcher goalMatcher = GOAL_PATTERN.matcher(message);
        if (goalMatcher.find()) {
            memory.remember(sessionId, "goal", goalMatcher.group(1).trim());
        }
    }

    private String toRequestMessagesJson(List<Message> messages) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", roleOf(message));
            item.put("content", message.getText());
            items.add(item);
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            return "[{\"role\":\"system\",\"content\":\"Failed to serialize prompt messages.\"}]";
        }
    }

    private String roleOf(Message message) {
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof AssistantMessage) {
            return "assistant";
        }
        if (message instanceof UserMessage) {
            return "user";
        }
        return message.getClass().getSimpleName();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? throwable.getMessage() : message;
    }
}
