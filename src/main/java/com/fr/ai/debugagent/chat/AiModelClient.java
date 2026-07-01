package com.fr.ai.debugagent.chat;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface AiModelClient {

    AiModelReply call(List<Message> messages);

    AiModelConfig currentModel();
}
