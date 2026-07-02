package com.fr.ai.debugagent.oms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "oms.login")
public class OmsLoginProperties {

    private String publicKey;
    private Map<String, Account> accounts = new LinkedHashMap<>();

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, Account> accounts) {
        this.accounts = accounts;
    }

    public static class Account {

        private boolean enabled = true;
        private boolean production = false;
        private String ssoHost;
        private String omsHost;
        private String username;
        private String password;
        private String totpSecret;
        private int level = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isProduction() {
            return production;
        }

        public void setProduction(boolean production) {
            this.production = production;
        }

        public String getSsoHost() {
            return ssoHost;
        }

        public void setSsoHost(String ssoHost) {
            this.ssoHost = ssoHost;
        }

        public String getOmsHost() {
            return omsHost;
        }

        public void setOmsHost(String omsHost) {
            this.omsHost = omsHost;
        }

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

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }
}
