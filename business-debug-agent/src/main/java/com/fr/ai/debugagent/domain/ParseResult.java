package com.fr.ai.debugagent.domain;

public record ParseResult(
        String provider,
        String rawOutput,
        String outputJson
) {
}
