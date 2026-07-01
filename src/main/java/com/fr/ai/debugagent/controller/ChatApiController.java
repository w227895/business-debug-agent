package com.fr.ai.debugagent.controller;

import com.fr.ai.debugagent.chat.ChatErrorResponse;
import com.fr.ai.debugagent.chat.ChatRequest;
import com.fr.ai.debugagent.chat.AiModelSwitchRequest;
import com.fr.ai.debugagent.chat.SimpleChatAgent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping(value = "/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSession(@PathVariable("sessionId") String sessionId) {
        try {
            return ResponseEntity.ok(chatAgent.getSession(sessionId));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("会话读取失败：" + rootMessage(ex)));
        }
    }

    @GetMapping(value = "/{sessionId}/model-calls", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getModelCalls(@PathVariable("sessionId") String sessionId) {
        try {
            return ResponseEntity.ok(chatAgent.getModelCallLogs(sessionId));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("模型调用记录读取失败：" + rootMessage(ex)));
        }
    }

    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listSessions() {
        try {
            return ResponseEntity.ok(chatAgent.listSessions());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("会话列表读取失败：" + rootMessage(ex)));
        }
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listModels() {
        try {
            return ResponseEntity.ok(chatAgent.listModels());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("模型配置读取失败：" + rootMessage(ex)));
        }
    }

    @PostMapping(value = "/models/active", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> switchModel(@Valid @RequestBody AiModelSwitchRequest request) {
        try {
            return ResponseEntity.ok(chatAgent.switchModel(request.modelId()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ChatErrorResponse("模型切换失败：" + rootMessage(ex)));
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
