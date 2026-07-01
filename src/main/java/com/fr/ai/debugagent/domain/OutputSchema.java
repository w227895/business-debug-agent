package com.fr.ai.debugagent.domain;

import java.util.List;
import java.util.Map;

public record OutputSchema(
        String id,
        String promptId,
        String name,
        List<String> requiredFields,
        Map<String, String> fieldTypes,
        String exampleJson
) {
}
