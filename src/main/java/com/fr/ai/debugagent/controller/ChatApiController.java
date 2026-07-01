package com.fr.ai.debugagent.controller;

import com.fr.ai.debugagent.chat.ChatErrorResponse;
import com.fr.ai.debugagent.chat.ChatRequest;
import com.fr.ai.debugagent.chat.SimpleChatAgent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final SimpleChatAgent chatAgent;

    public ChatApiController(SimpleChatAgent chatAgent) {
        this.chatAgent = chatAgent;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chat(@Valid @RequestBody ChatRequest request) {
        try {
            return ResponseEntity.ok(chatAgent.chat(request));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("DeepSeek 调用失败：" + rootMessage(ex)));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }
}