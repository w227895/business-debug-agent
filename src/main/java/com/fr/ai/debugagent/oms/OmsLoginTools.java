package com.fr.ai.debugagent.oms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OmsLoginTools {

    private final OmsLoginService loginService;
    private final ObjectMapper objectMapper;

    public OmsLoginTools(OmsLoginService loginService, ObjectMapper objectMapper) {
        this.loginService = loginService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "login_to_oms",
            description = """
                    Log in to FR OMS and cache the OMS Cookie on the server.
                    Use this when the user asks to log into OMS, get OMS Cookie, authenticate OMS test/prod environment, or prepare for authenticated OMS API checks.
                    The environment argument supports devb, deve, and prod. Return only masked cookie information.
                    """)
    public String loginToOms(
            @ToolParam(description = "OMS environment code, for example deve, devb, or prod") String environment) {
        OmsLoginResult result = loginService.login(environment);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"message\":\"OMS 登录结果序列化失败\"}";
        }
    }
}
