package com.fr.ai.debugagent.domain;

public record PromptAsset(
        String id,
        String scenarioCode,
        String name,
        String version,
        String systemPrompt,
        String userPromptTemplate,
        String status
) {
}
