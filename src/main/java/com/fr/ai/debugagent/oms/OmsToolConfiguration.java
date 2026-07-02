package com.fr.ai.debugagent.oms;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OmsToolConfiguration {

    @Bean
    public ToolCallbackProvider omsToolCallbackProvider(OmsLoginTools omsLoginTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(omsLoginTools)
                .build();
    }
}
