package com.fr.ai.debugagent.oms;

import com.fr.ai.debugagent.api.OrderTraceTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OmsToolConfiguration {

    @Bean
    public ToolCallbackProvider omsToolCallbackProvider(
            OmsLoginTools omsLoginTools,
            OrderTraceTools orderTraceTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(omsLoginTools, orderTraceTools)
                .build();
    }
}
