package com.fr.ai.debugagent.gitlab;

import java.time.Instant;
import java.util.List;

public record GitLabCodeLookupResult(
        boolean success,
        String message,
        String serviceHint,
        String ref,
        List<String> keywords,
        List<String> searchedProjects,
        List<GitLabCodeMatch> matches,
        Instant queriedAt) {

    static GitLabCodeLookupResult failure(
            String message,
            String serviceHint,
            String ref,
            List<String> keywords,
            List<String> searchedProjects) {
        return new GitLabCodeLookupResult(
                false,
                message,
                serviceHint,
                ref,
                keywords,
                searchedProjects,
                List.of(),
                Instant.now());
    }
}
