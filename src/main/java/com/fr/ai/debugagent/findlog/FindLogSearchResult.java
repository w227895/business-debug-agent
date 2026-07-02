package com.fr.ai.debugagent.findlog;

import java.time.Instant;
import java.util.List;

public record FindLogSearchResult(
        String profile,
        boolean success,
        String message,
        List<String> services,
        String keyword,
        String startDate,
        String endDate,
        int taskCount,
        List<String> excerpts,
        Instant searchedAt
) {

    public static FindLogSearchResult failure(
            String profile,
            String message,
            List<String> services,
            String keyword,
            String startDate,
            String endDate) {
        return new FindLogSearchResult(
                profile,
                false,
                message,
                services == null ? List.of() : services,
                keyword,
                startDate,
                endDate,
                0,
                List.of(),
                Instant.now());
    }
}
