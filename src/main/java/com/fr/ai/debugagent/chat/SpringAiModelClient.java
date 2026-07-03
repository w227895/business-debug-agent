package com.fr.ai.debugagent.chat;

import com.fr.ai.debugagent.tool.ToolCallLoggingAspect;
import com.fr.ai.debugagent.tool.ToolCallSummary;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class SpringAiModelClient implements AiModelClient {

    private final ChatModel chatModel;
    private final AiModelConfigStore modelConfigStore;
    private final List<ToolCallback> toolCallbacks;
    private final ToolCallLoggingAspect toolCallLoggingAspect;

    public SpringAiModelClient(
            ChatModel chatModel,
            AiModelConfigStore modelConfigStore,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
            ToolCallLoggingAspect toolCallLoggingAspect) {
        this.chatModel = chatModel;
        this.modelConfigStore = modelConfigStore;
        this.toolCallLoggingAspect = toolCallLoggingAspect;
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

        toolCallLoggingAspect.beginCapture();
        ChatResponse response;
        List<ToolCallSummary> toolCalls;
        try {
            response = chatModel.call(new Prompt(messages, buildOptions(modelConfig)));
        } finally {
            toolCalls = toolCallLoggingAspect.endCapture();
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException(modelConfig.provider() + " 返回为空");
        }
        return new AiModelReply(
                response.getResult().getOutput().getText(),
                toTokenUsage(response),
                modelConfig.provider(),
                modelConfig.model(),
                toolCalls);
    }

    @Override
    public AiModelReply stream(List<Message> messages, Consumer<String> chunkConsumer) {
        AiModelConfig modelConfig = modelConfigStore.getActiveModel();
        if (!"deepseek".equals(modelConfig.provider())) {
            throw new IllegalStateException("当前运行时仅支持 DeepSeek 模型：" + modelConfig.provider());
        }

        toolCallLoggingAspect.beginCapture();
        StringBuilder content = new StringBuilder();
        AtomicReference<ChatTokenUsage> tokenUsage = new AtomicReference<>(ChatTokenUsage.empty());
        List<ToolCallSummary> toolCalls;
        try {
            chatModel.stream(new Prompt(messages, buildOptions(modelConfig)))
                    .doOnNext(response -> handleStreamResponse(response, content, tokenUsage, chunkConsumer))
                    .blockLast();
        } finally {
            toolCalls = toolCallLoggingAspect.endCapture();
        }
        return new AiModelReply(
                content.toString(),
                tokenUsage.get(),
                modelConfig.provider(),
                modelConfig.model(),
                toolCalls);
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

    private void handleStreamResponse(
            ChatResponse response,
            StringBuilder content,
            AtomicReference<ChatTokenUsage> tokenUsage,
            Consumer<String> chunkConsumer) {
        ChatTokenUsage usage = toTokenUsage(response);
        if (hasTokens(usage)) {
            tokenUsage.set(usage);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return;
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        content.append(text);
        if (chunkConsumer != null) {
            chunkConsumer.accept(text);
        }
    }

    private boolean hasTokens(ChatTokenUsage tokenUsage) {
        return tokenUsage.totalTokens() > 0 || tokenUsage.promptTokens() > 0 || tokenUsage.completionTokens() > 0;
    }
}
