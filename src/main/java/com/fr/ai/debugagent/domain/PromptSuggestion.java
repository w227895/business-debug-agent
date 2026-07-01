package com.fr.ai.debugagent.domain;

public record PromptSuggestion(
        String title,
        String reason,
        String suggestedRule,
        String draftPromptFragment
) {
}
