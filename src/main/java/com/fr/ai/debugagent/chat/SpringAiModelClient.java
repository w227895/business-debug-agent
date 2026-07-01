package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiModelClient implements AiModelClient {

    private final ChatModel chatModel;
    private final String provider;
    private final String model;

    public SpringAiModelClient(
            ChatModel chatModel,
            @Value("${agent.ai.provider:deepseek}") String provider,
            @Value("${agent.ai.model:deepseek-chat}") String model) {
        this.chatModel = chatModel;
        this.provider = provider;
        this.model = model;
    }

    @Override
    public AiModelReply call(List<Message> messages) {
        ChatResponse response = chatModel.call(new Prompt(messages));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException(provider + " 返回为空");
        }
        return new AiModelReply(response.getResult().getOutput().getText(), toTokenUsage(response));
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String model() {
        return model;
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
}
