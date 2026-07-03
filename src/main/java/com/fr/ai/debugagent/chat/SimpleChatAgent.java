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
import java.util.Locale;
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
    private final AiModelConfigStore modelConfigStore;
    private final ObjectMapper objectMapper;

    public SimpleChatAgent(
            ChatSessionMemory memory,
            AiModelClient modelClient,
            AiModelConfigStore modelConfigStore,
            ObjectMapper objectMapper) {
        this.memory = memory;
        this.modelClient = modelClient;
        this.modelConfigStore = modelConfigStore;
        this.objectMapper = objectMapper;
    }

    public com.fr.ai.debugagent.chat.ChatResponse chat(ChatRequest request) {
        String sessionId = memory.normalizeSessionId(request.sessionId());
        String userMessage = request.message().trim();
        String environment = normalizeEnvironment(request.environment());

        memory.addMessage(sessionId, "user", userMessage);
        memory.remember(sessionId, "environment", environment);
        captureFacts(sessionId, userMessage);

        AiModelReply aiReply = callModel(sessionId, environment, userMessage);
        memory.addMessage(sessionId, "assistant", aiReply.content(), aiReply.tokenUsage(), aiReply.toolCalls());

        return new com.fr.ai.debugagent.chat.ChatResponse(
                sessionId,
                aiReply.content(),
                memory.getMessages(sessionId),
                memory.getFacts(sessionId),
                aiReply.tokenUsage(),
                aiReply.toolCalls(),
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

    public void deleteSession(String sessionId) {
        memory.deleteSession(memory.normalizeSessionId(sessionId));
    }

    public List<AiModelConfig> listModels() {
        return modelConfigStore.listModels();
    }

    public AiModelConfig switchModel(long modelId) {
        return modelConfigStore.activateModel(modelId);
    }

    private AiModelReply callModel(String sessionId, String environment, String latestUserMessage) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage("""
                你是一个支持上下文记忆的业务对话 Agent。
                当前页面选择的 OMS/API 环境是：%s。
                要求：
                1. 必须结合本轮会话历史回答。
                2. 如果用户问你记得什么，要基于历史消息总结你记住的信息。
                3. 不要声称自己没有记忆；当前 prompt 中已经包含同一 session 的历史消息。
                4. 当用户要求登录 OMS、获取 OMS Cookie、准备测试环境或生产环境 OMS 接口验证时，优先调用可用工具完成登录。
                5. 工具返回的 Cookie 只允许用于服务端缓存和后续工具调用，不要在回答里输出完整 Cookie、密码、TOTP secret 或原始登录材料。
                6. 当用户提供 parentId 并要求提取 traceId、查询 order 状态日志或继续查日志定位时，优先调用 extract_order_trace_ids 工具。
                7. 如果用户没有明确指定环境，OMS 登录和后续 API/日志排查默认使用当前页面选择的环境；如果用户明确指定 devb、deve 或 prod，则以用户本轮指定为准。
                8. 当用户提供 traceId、关键词、异常、订单号并要求查询测试环境或生产日志时，优先调用 search_findlog_logs 工具；必须使用具体 service#machine，最多 3 台机器，时间范围尽量收窄。若用户没有给出具体 service#machine，先调用 list_findlog_services 查候选机器。
                如果用户本轮明确提供了服务、机器、环境或灰度泳道，以用户显式信息为准；不要根据 traceId 前缀猜测或覆盖服务/机器。
                9. 回答使用中文，简洁直接。
                """.formatted(environment)));

        LogRequestHints logRequestHints = LogRequestHints.fromUserMessage(latestUserMessage);
        if (logRequestHints.hasAny()) {
            promptMessages.add(new SystemMessage(logRequestHints.toSystemInstruction()));
        }

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
                    reply.provider(),
                    reply.model(),
                    requestMessagesJson,
                    reply.content(),
                    true,
                    null,
                    null,
                    elapsedMillis(startedAt),
                    reply.tokenUsage());
            return reply;
        } catch (RuntimeException ex) {
            AiModelConfig activeModel = modelClient.currentModel();
            memory.addModelCallLog(
                    sessionId,
                    activeModel.provider(),
                    activeModel.model(),
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

    private String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return "deve";
        }
        String normalized = environment.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "devb", "deve", "prod" -> normalized;
            default -> "deve";
        };
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
