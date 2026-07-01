package com.fr.ai.debugagent.domain;

public record TestCase(
        String id,
        String scenarioCode,
        String title,
        String inputText,
        String expectedOutputJson,
        boolean enabled
) {
}
