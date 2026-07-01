package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SimpleChatAgent {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?:我叫|我的名字是|我是)\\s*([\\u4e00-\\u9fa5A-Za-z0-9_-]{1,20})");
    private static final Pattern GOAL_PATTERN = Pattern.compile("(?:目标是|我要做|我想做)\\s*(.+)");
    private static final int MAX_HISTORY_MESSAGES = 30;

    private final ChatSessionMemory memory;
    private final ChatModel chatModel;

    public SimpleChatAgent(ChatSessionMemory memory, ChatModel chatModel) {
        this.memory = memory;
        this.chatModel = chatModel;
    }

    public com.fr.ai.debugagent.chat.ChatResponse chat(ChatRequest request) {
        String sessionId = memory.normalizeSessionId(request.sessionId());
        String userMessage = request.message().trim();

        memory.addMessage(sessionId, "user", userMessage);
        captureFacts(sessionId, userMessage);

        AiReply aiReply = callDeepSeek(sessionId);
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

    private AiReply callDeepSeek(String sessionId) {
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

        ChatResponse response = chatModel.call(new Prompt(promptMessages));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("DeepSeek 返回为空");
        }
        return new AiReply(response.getResult().getOutput().getText(), toTokenUsage(response));
    }

    private ChatTokenUsage toTokenUsage(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return ChatTokenUsage.empty();
        }

        Usage usage = response.getMetadata().getUsage();
        int promptTokens = positiveOrZero(usage.getPromptTokens());
        int completionTokens = positiveOrZero(usage.getCompletionTokens());
        int totalTokens = positiveOrZero(usage.getTotalTokens());
        if (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0)) {
            totalTokens = promptTokens + completionTokens;
        }
        return new ChatTokenUsage(promptTokens, completionTokens, totalTokens);
    }

    private int positiveOrZero(Integer value) {
        return value == null || value < 0 ? 0 : value;
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

    private record AiReply(String content, ChatTokenUsage tokenUsage) {
    }
}
