package com.fr.ai.debugagent.domain;

import java.util.List;

public record JsonValidationResult(
        boolean valid,
        boolean jsonSyntaxValid,
        List<String> missingFields,
        List<String> typeErrors,
        List<String> enumErrors,
        String summary
) {
}
