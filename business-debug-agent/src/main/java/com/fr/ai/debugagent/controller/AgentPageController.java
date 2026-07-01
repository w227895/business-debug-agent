package com.fr.ai.debugagent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AgentPageController {

    @GetMapping("/")
    public String index() {
        return "agent";
    }
}
