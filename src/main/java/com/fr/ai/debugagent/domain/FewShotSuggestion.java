package com.fr.ai.debugagent.domain;

public record FewShotSuggestion(
        String title,
        String reason,
        String inputText,
        String expectedOutputJson
) {
}
