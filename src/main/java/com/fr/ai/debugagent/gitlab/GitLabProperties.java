package com.fr.ai.debugagent.gitlab;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    private String baseUrl = "https://gitlab.flightroutes24.com";
    private String token;
    private String defaultRef = "master";
    private Map<String, String> projects = new LinkedHashMap<>();
    private int maxResults = 12;
    private int maxFileChars = 12000;
    private int maxProjects = 3;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDefaultRef() {
        return defaultRef;
    }

    public void setDefaultRef(String defaultRef) {
        this.defaultRef = defaultRef;
    }

    public Map<String, String> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, String> projects) {
        this.projects = projects;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMaxFileChars() {
        return maxFileChars;
    }

    public void setMaxFileChars(int maxFileChars) {
        this.maxFileChars = maxFileChars;
    }

    public int getMaxProjects() {
        return maxProjects;
    }

    public void setMaxProjects(int maxProjects) {
        this.maxProjects = maxProjects;
    }
}
