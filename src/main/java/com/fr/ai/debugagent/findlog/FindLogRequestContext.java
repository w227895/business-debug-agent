package com.fr.ai.debugagent.findlog;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FindLogRequestContext {

    private static final ThreadLocal<Hints> CURRENT = new ThreadLocal<>();

    public Scope use(String profile, String serviceValues, String keyword) {
        return use(profile, serviceValues, keyword, true);
    }

    public Scope use(String profile, String serviceValues, String keyword, boolean forceSystemTimeRange) {
        if (!StringUtils.hasText(profile) && !StringUtils.hasText(serviceValues) && !StringUtils.hasText(keyword) && !forceSystemTimeRange) {
            CURRENT.remove();
            return CURRENT::remove;
        }
        CURRENT.set(new Hints(blankToNull(profile), blankToNull(serviceValues), blankToNull(keyword), forceSystemTimeRange));
        return CURRENT::remove;
    }

    public Hints current() {
        return CURRENT.get();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }

    public record Hints(String profile, String serviceValues, String keyword, boolean forceSystemTimeRange) {
    }
}
