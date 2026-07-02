package com.fr.ai.debugagent.oms;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OmsCookieStore {

    private final ConcurrentMap<String, OmsCookieSession> sessions = new ConcurrentHashMap<>();

    public void save(OmsCookieSession session) {
        sessions.put(normalize(session.environment()), session);
    }

    public Optional<OmsCookieSession> get(String environment) {
        return Optional.ofNullable(sessions.get(normalize(environment)));
    }

    private String normalize(String environment) {
        return environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
    }
}
