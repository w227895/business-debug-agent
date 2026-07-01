package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiModelClient implements AiModelClient {

    private final ChatModel chatModel;
    private final AiModelConfigStore modelConfigStore;

    public SpringAiModelClient(
            ChatModel chatModel,
            AiModelConfigStore modelConfigStore) {
        this.chatModel = chatModel;
        this.modelConfigStore = modelConfigStore;
    }

    @Override
    public AiModelReply call(List<Message> messages) {
        AiModelConfig modelConfig = modelConfigStore.getActiveModel();
        if (!"deepseek".equals(modelConfig.provider())) {
            throw new IllegalStateException("当前运行时仅支持 DeepSeek 模型：" + modelConfig.provider());
        }

        ChatResponse response = chatModel.call(new Prompt(messages, buildOptions(modelConfig)));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException(modelConfig.provider() + " 返回为空");
        }
        return new AiModelReply(
                response.getResult().getOutput().getText(),
                toTokenUsage(response),
                modelConfig.provider(),
                modelConfig.model());
    }

    @Override
    public AiModelConfig currentModel() {
        return modelConfigStore.getActiveModel();
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

    private ChatOptions buildOptions(AiModelConfig modelConfig) {
        ChatOptions.Builder builder = ChatOptions.builder().model(modelConfig.model());
        if (modelConfig.temperature() != null) {
            builder.temperature(modelConfig.temperature());
        }
        return builder.build();
    }
}
