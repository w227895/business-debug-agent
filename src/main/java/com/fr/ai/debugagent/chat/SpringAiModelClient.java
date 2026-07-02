package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class SpringAiModelClient implements AiModelClient {

    private final ChatModel chatModel;
    private final AiModelConfigStore modelConfigStore;
    private final List<ToolCallback> toolCallbacks;

    public SpringAiModelClient(
            ChatModel chatModel,
            AiModelConfigStore modelConfigStore,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        this.chatModel = chatModel;
        this.modelConfigStore = modelConfigStore;
        this.toolCallbacks = toolCallbackProviders.orderedStream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toList();
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

    private DeepSeekChatOptions buildOptions(AiModelConfig modelConfig) {
        DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder().model(modelConfig.model());
        if (modelConfig.temperature() != null) {
            builder.temperature(modelConfig.temperature());
        }
        if (!toolCallbacks.isEmpty() && !modelConfig.model().contains("reasoner")) {
            builder.toolCallbacks(toolCallbacks);
            builder.internalToolExecutionEnabled(true);
        }
        return builder.build();
    }
}
