package com.fr.ai.debugagent.oms;

import java.time.Instant;
import java.util.List;

public record OmsCookieSession(
        String environment,
        String ssoHost,
        String omsHost,
        String cookieHeader,
        List<String> cookieNames,
        int httpStatus,
        Instant createdAt) {

    public String maskedCookieHeader() {
        return String.join("; ", cookieNames.stream()
                .map(name -> name + "=***")
                .toList());
    }
}
