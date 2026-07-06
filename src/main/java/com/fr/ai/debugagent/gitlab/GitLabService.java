package com.fr.ai.debugagent.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitLabService {

    private static final int MAX_KEYWORDS = 8;
    private static final int MAX_MATCHES = 5;
    private static final int MAX_SEARCH_PER_KEYWORD = 8;
    private static final int MAX_EXCERPT_CHARS = 3000;
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "\\bat\\s+(([a-zA-Z_$][\\w$]*\\.)+[A-Z][\\w$]*\\.([a-zA-Z_$][\\w$]*))\\(");
    private static final Pattern FQCN_PATTERN = Pattern.compile("\\b([a-z][\\w$]*\\.){2,}[A-Z][\\w$]*\\b");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([A-Z][\\w$]*(?:Exception|Error))\\b");
    private static final Pattern URL_PATH_PATTERN = Pattern.compile("(?<![\\w])/(?:[A-Za-z0-9._{}-]+/)*[A-Za-z0-9._{}-]+(?:\\.do|\\.json|\\.html)?");
    private static final Pattern PACKAGE_SERVICE_PATTERN = Pattern.compile("\\bcom\\.fr\\.([a-z][a-z0-9_-]{2,})\\.");
    private static final Pattern TRACE_SERVICE_PATTERN = Pattern.compile("\\b[a-z]+_([a-z][a-z0-9_-]{2,})(?:[-_](?:dev|prod|deve|devb|kb|gray)[a-z0-9_-]*)?\\b");
    private static final Pattern SENSITIVE_FILE_PATTERN = Pattern.compile(
            "(?i)(^|/)(\\.env|id_rsa|.*\\.(pem|key|p12|jks)|application-prod\\.(yml|yaml|properties)|bootstrap-prod\\.(yml|yaml|properties))$");

    private final GitLabProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitLabService(GitLabProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public GitLabCodeLookupResult findCodeByLog(String logText, String serviceHint, String ref) {
        String normalizedLog = logText == null ? "" : logText.trim();
        String normalizedRef = StringUtils.hasText(ref) ? ref.trim() : defaultRef();
        String normalizedServiceHint = normalize(serviceHint);
        List<String> keywords = extractKeywords(normalizedLog);

        if (!StringUtils.hasText(properties.getToken())) {
            return GitLabCodeLookupResult.failure(
                    "GitLab token 未配置，请设置 GITLAB_TOKEN 或 gitlab.token",
                    normalizedServiceHint,
                    normalizedRef,
                    keywords,
                    List.of());
        }
        if (!StringUtils.hasText(normalizedLog)) {
            return GitLabCodeLookupResult.failure("日志内容不能为空", normalizedServiceHint, normalizedRef, keywords, List.of());
        }
        if (keywords.isEmpty()) {
            return GitLabCodeLookupResult.failure(
                    "日志里没有提取到可用于代码搜索的类名、方法名、异常名或接口路径",
                    normalizedServiceHint,
                    normalizedRef,
                    keywords,
                    List.of());
        }

        List<ProjectTarget> projects = resolveProjects(normalizedLog, normalizedServiceHint);
        if (projects.isEmpty()) {
            return GitLabCodeLookupResult.failure(
                    "未能从日志或 serviceHint 找到 GitLab 项目，请检查日志里是否包含服务名，或补充 serviceHint",
                    normalizedServiceHint,
                    normalizedRef,
                    keywords,
                    List.of());
        }

        try {
            List<SearchHit> hits = new ArrayList<>();
            for (ProjectTarget project : projects) {
                for (String keyword : keywords) {
                    hits.addAll(searchProject(project, keyword, normalizedRef));
                    if (hits.size() >= maxResults() * 2) {
                        break;
                    }
                }
            }
            List<GitLabCodeMatch> matches = buildMatches(hits, normalizedRef);
            if (matches.isEmpty()) {
                return new GitLabCodeLookupResult(
                        false,
                        "GitLab 搜索完成，但没有找到相关代码命中",
                        normalizedServiceHint,
                        normalizedRef,
                        keywords,
                        projectNames(projects),
                        List.of(),
                        Instant.now());
            }
            return new GitLabCodeLookupResult(
                    true,
                    "已根据日志找到候选代码，请结合 filePath、keyword 和 excerpt 给出定位结论",
                    normalizedServiceHint,
                    normalizedRef,
                    keywords,
                    projectNames(projects),
                    matches,
                    Instant.now());
        } catch (Exception ex) {
            return GitLabCodeLookupResult.failure(
                    "GitLab 代码定位失败：" + rootMessage(ex),
                    normalizedServiceHint,
                    normalizedRef,
                    keywords,
                    projectNames(projects));
        }
    }

    List<String> extractKeywords(String logText) {
        if (!StringUtils.hasText(logText)) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        Matcher stackTraceMatcher = STACK_TRACE_PATTERN.matcher(logText);
        while (stackTraceMatcher.find() && keywords.size() < MAX_KEYWORDS) {
            String fullMethod = stackTraceMatcher.group(1);
            String className = fullMethod.substring(0, fullMethod.lastIndexOf('.'));
            String methodName = stackTraceMatcher.group(3);
            addKeyword(keywords, simpleName(className));
            addKeyword(keywords, methodName);
        }

        Matcher fqcnMatcher = FQCN_PATTERN.matcher(logText);
        while (fqcnMatcher.find() && keywords.size() < MAX_KEYWORDS) {
            addKeyword(keywords, simpleName(fqcnMatcher.group()));
        }

        Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(logText);
        while (exceptionMatcher.find() && keywords.size() < MAX_KEYWORDS) {
            addKeyword(keywords, exceptionMatcher.group(1));
        }

        Matcher pathMatcher = URL_PATH_PATTERN.matcher(logText);
        while (pathMatcher.find() && keywords.size() < MAX_KEYWORDS) {
            String path = trimPath(pathMatcher.group());
            addKeyword(keywords, path);
            String last = path.substring(path.lastIndexOf('/') + 1);
            addKeyword(keywords, last);
        }

        return keywords.stream().limit(MAX_KEYWORDS).toList();
    }

    private List<ProjectTarget> resolveProjects(String logText, String serviceHint) {
        Map<String, String> configuredProjects = configuredProjects();
        List<ProjectTarget> result = new ArrayList<>();

        for (String keyword : inferProjectSearchKeywords(logText, serviceHint)) {
            addConfiguredProject(result, configuredProjects, keyword);
            if (result.size() < maxProjects()) {
                result.addAll(searchProjects(keyword));
            }
            if (result.size() >= maxProjects()) {
                break;
            }
        }

        if (result.isEmpty() && configuredProjects.size() == 1) {
            Map.Entry<String, String> item = configuredProjects.entrySet().iterator().next();
            result.add(new ProjectTarget(item.getKey(), item.getValue()));
        }

        return result.stream()
                .distinct()
                .limit(maxProjects())
                .toList();
    }

    List<String> inferProjectSearchKeywords(String logText, String serviceHint) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addProjectKeyword(keywords, serviceHint);

        String log = logText == null ? "" : logText;
        Matcher packageMatcher = PACKAGE_SERVICE_PATTERN.matcher(log);
        while (packageMatcher.find() && keywords.size() < maxProjects() * 3) {
            addProjectKeyword(keywords, packageMatcher.group(1));
        }

        Matcher pathMatcher = URL_PATH_PATTERN.matcher(log);
        while (pathMatcher.find() && keywords.size() < maxProjects() * 3) {
            String path = trimPath(pathMatcher.group());
            String[] segments = path.split("/");
            if (segments.length > 1) {
                addProjectKeyword(keywords, segments[1]);
            }
        }

        Matcher traceMatcher = TRACE_SERVICE_PATTERN.matcher(log);
        while (traceMatcher.find() && keywords.size() < maxProjects() * 3) {
            addProjectKeyword(keywords, traceMatcher.group(1));
        }

        for (String alias : configuredProjects().keySet()) {
            if (log.toLowerCase(Locale.ROOT).contains(alias.toLowerCase(Locale.ROOT))) {
                addProjectKeyword(keywords, alias);
            }
        }

        return keywords.stream().limit(maxProjects() * 3L).toList();
    }

    private void addProjectKeyword(Set<String> keywords, String value) {
        String keyword = normalize(value);
        if (keyword.length() >= 3 && keyword.length() <= 80 && !isNoisyProjectKeyword(keyword)) {
            keywords.add(keyword);
        }
    }

    private boolean isNoisyProjectKeyword(String keyword) {
        return switch (keyword) {
            case "api", "app", "com", "controller", "service", "http", "https", "java", "json", "null", "error", "exception" -> true;
            default -> false;
        };
    }

    private void addConfiguredProject(List<ProjectTarget> result, Map<String, String> projects, String alias) {
        String project = projects.get(normalize(alias));
        if (StringUtils.hasText(project)) {
            result.add(new ProjectTarget(normalize(alias), project.trim()));
        }
    }

    private List<ProjectTarget> searchProjects(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        try {
            URI uri = apiUri("/projects?simple=true&per_page=" + maxProjects() + "&search=" + query(keyword));
            HttpResponse<String> response = httpClient.send(get(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!success(response)) {
                return List.of();
            }
            List<ProjectTarget> result = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String pathWithNamespace = text(item, "path_with_namespace");
                    if (StringUtils.hasText(pathWithNamespace)) {
                        result.add(new ProjectTarget(normalize(keyword), pathWithNamespace));
                    }
                    if (result.size() >= maxProjects()) {
                        break;
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<SearchHit> searchProject(ProjectTarget project, String keyword, String ref) throws Exception {
        StringBuilder path = new StringBuilder()
                .append("/projects/")
                .append(pathSegment(project.project()))
                .append("/search?scope=blobs&per_page=")
                .append(MAX_SEARCH_PER_KEYWORD)
                .append("&search=")
                .append(query(keyword));
        if (StringUtils.hasText(ref)) {
            path.append("&ref=").append(query(ref));
        }

        HttpResponse<String> response = httpClient.send(get(apiUri(path.toString())), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!success(response) || !StringUtils.hasText(response.body())) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode item : root) {
            String filePath = text(item, "path");
            if (!StringUtils.hasText(filePath)) {
                filePath = text(item, "filename");
            }
            if (!StringUtils.hasText(filePath)) {
                continue;
            }
            String data = text(item, "data");
            hits.add(new SearchHit(project, filePath, keyword, data, score(filePath, keyword, data)));
        }
        return hits;
    }

    private List<GitLabCodeMatch> buildMatches(List<SearchHit> hits, String ref) {
        Map<String, SearchHit> bestByFile = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            String key = hit.project().project() + ":" + hit.filePath();
            SearchHit existing = bestByFile.get(key);
            if (existing == null || hit.score() > existing.score()) {
                bestByFile.put(key, hit);
            }
        }

        return bestByFile.values().stream()
                .sorted(Comparator.comparingInt(SearchHit::score).reversed())
                .limit(Math.min(MAX_MATCHES, maxResults()))
                .map(hit -> toMatch(hit, ref))
                .toList();
    }

    private GitLabCodeMatch toMatch(SearchHit hit, String ref) {
        if (isSensitiveFile(hit.filePath())) {
            return new GitLabCodeMatch(
                    hit.project().alias(),
                    hit.project().project(),
                    hit.filePath(),
                    ref,
                    hit.keyword(),
                    hit.score(),
                    true,
                    "命中敏感配置类文件，已拒绝读取原始内容，仅保留搜索命中：" + preview(hit.data(), MAX_EXCERPT_CHARS));
        }
        FileRead fileRead = readFile(hit.project().project(), hit.filePath(), ref);
        String excerpt = StringUtils.hasText(fileRead.content())
                ? excerptAround(fileRead.content(), hit.keyword())
                : preview(hit.data(), MAX_EXCERPT_CHARS);
        return new GitLabCodeMatch(
                hit.project().alias(),
                hit.project().project(),
                hit.filePath(),
                ref,
                hit.keyword(),
                hit.score(),
                fileRead.truncated(),
                excerpt);
    }

    private FileRead readFile(String project, String filePath, String ref) {
        try {
            URI uri = apiUri("/projects/"
                    + pathSegment(project)
                    + "/repository/files/"
                    + pathSegment(filePath)
                    + "/raw?ref="
                    + query(ref));
            HttpResponse<String> response = httpClient.send(get(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!success(response)) {
                return new FileRead("", false);
            }
            String content = response.body() == null ? "" : response.body();
            int limit = maxFileChars();
            if (content.length() <= limit) {
                return new FileRead(content, false);
            }
            return new FileRead(content.substring(0, limit), true);
        } catch (Exception ex) {
            return new FileRead("", false);
        }
    }

    private String excerptAround(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        int index = StringUtils.hasText(lowerKeyword) ? lowerContent.indexOf(lowerKeyword) : -1;
        if (index < 0) {
            return preview(content, MAX_EXCERPT_CHARS);
        }

        int start = Math.max(0, index - 1200);
        int end = Math.min(content.length(), index + lowerKeyword.length() + 1600);
        int lineStart = content.lastIndexOf('\n', start);
        int lineEnd = content.indexOf('\n', end);
        if (lineStart >= 0) {
            start = lineStart + 1;
        }
        if (lineEnd > 0) {
            end = lineEnd;
        }
        String excerpt = content.substring(start, end);
        return preview((start > 0 ? "...\\n" : "") + excerpt + (end < content.length() ? "\\n..." : ""), MAX_EXCERPT_CHARS);
    }

    private int score(String filePath, String keyword, String data) {
        int score = 0;
        String lowerPath = filePath.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        String lowerData = data == null ? "" : data.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".java")) {
            score += 20;
        } else if (lowerPath.endsWith(".xml") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml")) {
            score += 8;
        }
        if (lowerPath.contains("/controller/")) {
            score += 8;
        }
        if (lowerPath.contains("/service/")) {
            score += 6;
        }
        if (lowerPath.contains(lowerKeyword.toLowerCase(Locale.ROOT))) {
            score += 20;
        }
        if (lowerData.contains(lowerKeyword)) {
            score += 12;
        }
        return score;
    }

    private List<String> projectNames(List<ProjectTarget> projects) {
        return projects.stream().map(ProjectTarget::project).toList();
    }

    private Map<String, String> configuredProjects() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> item : properties.getProjects().entrySet()) {
            if (StringUtils.hasText(item.getKey()) && StringUtils.hasText(item.getValue())) {
                result.put(normalize(item.getKey()), item.getValue().trim());
            }
        }
        return result;
    }

    private HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("PRIVATE-TOKEN", properties.getToken().trim())
                .GET()
                .build();
    }

    private boolean success(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private URI apiUri(String path) {
        return URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/api/v4" + path);
    }

    private void addKeyword(Set<String> keywords, String value) {
        String keyword = value == null ? "" : value.trim();
        if (keyword.length() >= 3 && keyword.length() <= 120) {
            keywords.add(keyword);
        }
    }

    private String trimPath(String value) {
        String path = value == null ? "" : value.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        return path;
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        int index = normalized.lastIndexOf('.');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String defaultRef() {
        return StringUtils.hasText(properties.getDefaultRef()) ? properties.getDefaultRef().trim() : "master";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String query(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean isSensitiveFile(String filePath) {
        return SENSITIVE_FILE_PATTERN.matcher(filePath == null ? "" : filePath).find();
    }

    private int maxResults() {
        return Math.max(1, Math.min(20, properties.getMaxResults()));
    }

    private int maxFileChars() {
        return Math.max(1000, Math.min(30000, properties.getMaxFileChars()));
    }

    private int maxProjects() {
        return Math.max(1, Math.min(5, properties.getMaxProjects()));
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replace("\r\n", "\n").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    private record ProjectTarget(String alias, String project) {
    }

    private record SearchHit(ProjectTarget project, String filePath, String keyword, String data, int score) {
    }

    private record FileRead(String content, boolean truncated) {
    }
}
