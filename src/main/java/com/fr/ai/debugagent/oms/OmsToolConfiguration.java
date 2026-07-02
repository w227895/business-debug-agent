package com.fr.ai.debugagent.oms;

import com.fr.ai.debugagent.api.OrderTraceTools;
import com.fr.ai.debugagent.findlog.FindLogTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OmsToolConfiguration {

    @Bean
    public ToolCallbackProvider omsToolCallbackProvider(
            OmsLoginTools omsLoginTools,
            OrderTraceTools orderTraceTools,
            FindLogTools findLogTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(omsLoginTools, orderTraceTools, findLogTools)
                .build();
    }
}
