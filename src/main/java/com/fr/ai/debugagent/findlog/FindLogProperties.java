package com.fr.ai.debugagent.findlog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "findlog")
public class FindLogProperties {

    private String baseUrl = "https://devtool.flightroutes24.com";

    private Map<String, Account> accounts = new LinkedHashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    public Account account(String profile) {
        return accounts.get(normalizeProfile(profile));
    }

    static String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "dev";
        }
        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "prod", "production" -> "prod";
            default -> "dev";
        };
    }

    public static class Account {

        private String username;

        private String password;

        private String totpSecret;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getTotpSecret() {
            return totpSecret;
        }

        public void setTotpSecret(String totpSecret) {
            this.totpSecret = totpSecret;
        }
    }
}
