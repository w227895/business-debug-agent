package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.function.Consumer;

public interface AiModelClient {

    AiModelReply call(List<Message> messages);

    AiModelReply stream(List<Message> messages, Consumer<String> chunkConsumer);

    AiModelConfig currentModel();
}
