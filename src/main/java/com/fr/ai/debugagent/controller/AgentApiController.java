package com.fr.ai.debugagent.controller;

import com.fr.ai.debugagent.agent.AgentDebugRequest;
import com.fr.ai.debugagent.agent.AgentDebugResponse;
import com.fr.ai.debugagent.agent.AgentOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentApiController {

    private final AgentOrchestrator orchestrator;

    public AgentApiController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/debug", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentDebugResponse debug(@Valid @RequestBody AgentDebugRequest request) {
        return orchestrator.debug(request);
    }
}
