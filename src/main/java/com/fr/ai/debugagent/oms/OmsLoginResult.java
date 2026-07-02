package com.fr.ai.debugagent.oms;

import java.time.Instant;
import java.util.List;

public record OmsLoginResult(
        String environment,
        boolean success,
        String message,
        String ssoHost,
        String omsHost,
        int httpStatus,
        List<String> cookieNames,
        String maskedCookieHeader,
        Instant createdAt) {

    public static OmsLoginResult failure(String environment, String message, String ssoHost, String omsHost, int httpStatus) {
        return new OmsLoginResult(environment, false, message, ssoHost, omsHost, httpStatus, List.of(), "", Instant.now());
    }

    public static OmsLoginResult success(OmsCookieSession session, String message) {
        return new OmsLoginResult(
                session.environment(),
                true,
                message,
                session.ssoHost(),
                session.omsHost(),
                session.httpStatus(),
                session.cookieNames(),
                session.maskedCookieHeader(),
                session.createdAt());
    }
}
