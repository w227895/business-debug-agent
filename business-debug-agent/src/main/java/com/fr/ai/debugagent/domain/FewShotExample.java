package com.fr.ai.debugagent.domain;

public record FewShotExample(
        String id,
        String promptId,
        String title,
        String inputText,
        String expectedOutputJson,
        boolean enabled
) {
}
