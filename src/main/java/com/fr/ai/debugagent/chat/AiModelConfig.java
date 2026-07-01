package com.fr.ai.debugagent.chat;

import java.time.LocalDateTime;

public record AiModelConfig(
        long id,
        String provider,
        String model,
        String displayName,
        Double temperature,
        boolean enabled,
        boolean active,
        LocalDateTime updatedAt
) {
}
