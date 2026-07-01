package com.fr.ai.debugagent.domain;

public record Scenario(
        String code,
        String name,
        double confidence,
        String description
) {
}
