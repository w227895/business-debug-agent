package com.fr.ai.debugagent.api;

import java.time.Instant;
import java.util.List;

public record OrderTraceLookupResult(
        String environment,
        boolean success,
        String message,
        String requestUrl,
        int httpStatus,
        List<String> traceIds,
        String responsePreview,
        Instant createdAt) {

    public static OrderTraceLookupResult failure(
            String environment,
            String message,
            String requestUrl,
            int httpStatus) {
        return new OrderTraceLookupResult(
                environment,
                false,
                message,
                requestUrl,
                httpStatus,
                List.of(),
                "",
                Instant.now());
    }
}
