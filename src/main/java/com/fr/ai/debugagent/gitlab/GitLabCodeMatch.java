package com.fr.ai.debugagent.gitlab;

public record GitLabCodeMatch(
        String projectAlias,
        String project,
        String filePath,
        String ref,
        String keyword,
        int score,
        boolean contentTruncated,
        String excerpt) {
}
