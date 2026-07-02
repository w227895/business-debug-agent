package com.fr.ai.debugagent.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "business-api")
public class BusinessApiProperties {

    private Map<String, ServiceConfig> services = new LinkedHashMap<>();

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services;
    }

    public String baseUrl(String service, String environment) {
        ServiceConfig config = services.get(normalize(service));
        if (config == null) {
            return "";
        }
        return config.getBaseUrls().getOrDefault(normalize(environment), "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static class ServiceConfig {

        private Map<String, String> baseUrls = new LinkedHashMap<>();

        public Map<String, String> getBaseUrls() {
            return baseUrls;
        }

        public void setBaseUrls(Map<String, String> baseUrls) {
            this.baseUrls = baseUrls;
        }
    }
}
