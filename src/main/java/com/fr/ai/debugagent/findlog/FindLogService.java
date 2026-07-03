package com.fr.ai.debugagent.findlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fr.ai.debugagent.oms.TotpGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FindLogService {

    private static final int MAX_MACHINES = 3;
    private static final int MAX_EXCERPTS = 8;
    private static final int EXCERPT_LENGTH = 1200;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FindLogProperties properties;
    private final TotpGenerator totpGenerator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FindLogService(FindLogProperties properties, TotpGenerator totpGenerator, ObjectMapper objectMapper) {
        this.properties = properties;
        this.totpGenerator = totpGenerator;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public FindLogSearchResult searchLogs(
            String profile,
            String serviceValues,
            String keyword,
            String startDate,
            String endDate,
            String contextLines) {
        String normalizedProfile = normalizeProfile(profile);
        List<String> services = parseServices(serviceValues);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStartDate = StringUtils.hasText(startDate) ? startDate.trim() : defaultStartDate();
        String normalizedEndDate = StringUtils.hasText(endDate) ? endDate.trim() : nowDate();

        if (!StringUtils.hasText(normalizedKeyword)) {
            return FindLogSearchResult.failure(normalizedProfile, "FindLog 查询关键词不能为空", services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
        }
        if (services.isEmpty()) {
            return FindLogSearchResult.failure(normalizedProfile, "FindLog 查询必须指定 service#machine，例如 order_deve#deve", services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
        }
        if (services.size() > MAX_MACHINES) {
            return FindLogSearchResult.failure(normalizedProfile, "FindLog 单次查询最多允许 3 台机器，请缩小 service#machine 范围", services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
        }
        if (services.stream().anyMatch(item -> !item.contains("#"))) {
            return FindLogSearchResult.failure(normalizedProfile, "请传入具体机器级 service 值，格式为 service#machine，避免父服务展开超过 3 台机器", services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
        }

        FindLogLogin login = login(normalizedProfile);
        if (!login.success()) {
            return FindLogSearchResult.failure(normalizedProfile, login.message(), services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
        }

        String executeId = UUID.randomUUID().toString();
        try {
            Map<String, Object> searchBody = new LinkedHashMap<>();
            searchBody.put("text", quoteKeyword(normalizedKeyword));
            searchBody.put("option", contextOption(contextLines));
            searchBody.put("pipelineHandle", "");
            searchBody.put("startDate", normalizedStartDate);
            searchBody.put("endDate", normalizedEndDate);
            searchBody.put("service", services);
            searchBody.put("executeId", executeId);
            searchBody.put("executeTime", nowDate());
            searchBody.put("concurrentSearch", true);
            searchBody.put("sshToken", login.token());

            HttpResponse<String> searchResponse = httpClient.send(
                    jsonRequest("/sse/searchLog", normalizedProfile, login.token(), searchBody),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (searchResponse.body() != null && searchResponse.body().contains("请登录后再操作")) {
                return FindLogSearchResult.failure(normalizedProfile, "FindLog 登录态无效，请刷新凭据后重试", services, normalizedKeyword, normalizedStartDate, normalizedEndDate);
            }

            int taskCount = parseTaskCount(searchResponse.body());
            List<String> excerpts = readSseExcerpts(normalizedProfile, login, executeId);
            boolean success = searchResponse.statusCode() >= 200 && searchResponse.statusCode() < 300;
            String message = excerpts.isEmpty()
                    ? "FindLog 查询完成，但没有收集到匹配日志片段"
                    : "FindLog 查询完成，已收集到日志片段";
            return new FindLogSearchResult(
                    normalizedProfile,
                    success,
                    message,
                    services,
                    normalizedKeyword,
                    normalizedStartDate,
                    normalizedEndDate,
                    taskCount,
                    excerpts,
                    Instant.now());
        } catch (Exception ex) {
            return FindLogSearchResult.failure(
                    normalizedProfile,
                    "FindLog 查询失败：" + rootMessage(ex),
                    services,
                    normalizedKeyword,
                    normalizedStartDate,
                    normalizedEndDate);
        }
    }

    public List<Map<String, String>> findServices(String profile, String keyword) {
        String normalizedProfile = normalizeProfile(profile);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        try {
            HttpResponse<String> response = httpClient.send(
                    getRequest("/ssh/searchService", normalizedProfile, ""),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !StringUtils.hasText(response.body())) {
                return List.of();
            }
            List<Map<String, String>> result = new ArrayList<>();
            collectServiceMatches(objectMapper.readTree(response.body()), normalizedKeyword, result);
            return List.copyOf(result);
        } catch (Exception ex) {
            return List.of();
        }
    }

    List<String> parseServices(String serviceValues) {
        if (!StringUtils.hasText(serviceValues)) {
            return List.of();
        }
        List<String> services = new ArrayList<>();
        for (String item : serviceValues.split("[,;\\s]+")) {
            if (StringUtils.hasText(item)) {
                services.add(item.trim());
            }
        }
        return List.copyOf(services);
    }

    List<String> extractExcerptsFromSseData(String data) {
        if (!StringUtils.hasText(data)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode result = root.get("result");
            if (result == null || result.isNull()) {
                return List.of(preview(root.toString()));
            }
            List<String> excerpts = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    String text = item.has("result") ? item.get("result").toString() : item.toString();
                    excerpts.add(preview(text));
                }
            } else {
                excerpts.add(preview(result.toString()));
            }
            return excerpts;
        } catch (JsonProcessingException ex) {
            return List.of(preview(data));
        }
    }

    private FindLogLogin login(String profile) {
        FindLogProperties.Account account = properties.account(profile);
        if (account == null || !StringUtils.hasText(account.getUsername()) || !StringUtils.hasText(account.getPassword())) {
            return FindLogLogin.failure("FindLog " + profile + " 账号未配置，请设置 FINDLOG_* 环境变量");
        }
        try {
            String totpCode = StringUtils.hasText(account.getTotpSecret())
                    ? totpGenerator.now(account.getTotpSecret())
                    : "000000";
            Map<String, String> body = new LinkedHashMap<>();
            body.put("username", base64(account.getUsername()));
            body.put("password", base64(account.getPassword()));
            body.put("totpCode", base64(totpCode));
            HttpResponse<String> response = httpClient.send(
                    jsonRequest("/sse/login", profile, "", body),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseText = response.body() == null ? "" : response.body().trim();
            if (!responseText.contains("success")) {
                return FindLogLogin.failure("FindLog " + profile + " 登录失败：" + preview(responseText));
            }
            return FindLogLogin.success(account.getUsername(), extractToken(responseText));
        } catch (Exception ex) {
            return FindLogLogin.failure("FindLog " + profile + " 登录异常：" + rootMessage(ex));
        }
    }

    private HttpRequest jsonRequest(String path, String profile, String token, Object body) throws JsonProcessingException {
        return HttpRequest.newBuilder(URI.create(trimTrailingSlash(properties.getBaseUrl()) + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("profile", profile.toUpperCase(Locale.ROOT))
                .header("user", token == null ? "" : token)
                .header("webVersion", "1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest getRequest(String path, String profile, String token) {
        return HttpRequest.newBuilder(URI.create(trimTrailingSlash(properties.getBaseUrl()) + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("profile", profile.toUpperCase(Locale.ROOT))
                .header("user", token == null ? "" : token)
                .header("webVersion", "1.0.0")
                .GET()
                .build();
    }

    private void collectServiceMatches(JsonNode node, String keyword, List<Map<String, String>> result) {
        if (node == null || result.size() >= 30) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectServiceMatches(item, keyword, result);
                if (result.size() >= 30) {
                    return;
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String value = text(node, "value");
        String label = text(node, "label");
        String ip = text(node, "ip");
        boolean concreteMachine = value.contains("#");
        boolean matched = !StringUtils.hasText(keyword)
                || value.toLowerCase(Locale.ROOT).contains(keyword)
                || label.toLowerCase(Locale.ROOT).contains(keyword)
                || ip.toLowerCase(Locale.ROOT).contains(keyword);
        if (concreteMachine && matched) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("value", value);
            item.put("ip", ip);
            result.add(item);
        }

        collectServiceMatches(node.get("children"), keyword, result);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private List<String> readSseExcerpts(String profile, FindLogLogin login, String executeId) throws Exception {
        String emitterId = login.username() + "-" + executeId;
        URI uri = URI.create(trimTrailingSlash(properties.getBaseUrl())
                + "/sse/connectLogEmitter?executeId="
                + URLEncoder.encode(emitterId, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("profile", profile.toUpperCase(Locale.ROOT))
                .header("user", login.token())
                .header("webVersion", "1.0.0")
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        List<String> excerpts = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:") && line.contains("finish")) {
                    break;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                for (String excerpt : extractExcerptsFromSseData(data)) {
                    if (excerpts.size() >= MAX_EXCERPTS) {
                        return List.copyOf(excerpts);
                    }
                    excerpts.add(excerpt);
                }
            }
        }
        return List.copyOf(excerpts);
    }

    private int parseTaskCount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node.isArray() ? node.size() : 0;
        } catch (JsonProcessingException ex) {
            return 0;
        }
    }

    private String normalizeProfile(String profile) {
        return "prod".equals(FindLogProperties.normalizeProfile(profile)) ? "PROD" : "DEV";
    }

    String contextOption(String contextLines) {
        int lines = 0;
        if (StringUtils.hasText(contextLines)) {
            try {
                lines = Math.max(0, Math.min(80, Integer.parseInt(contextLines.trim())));
            } catch (NumberFormatException ignored) {
                lines = 0;
            }
        }
        return lines == 0 ? "" : "-C " + lines;
    }

    private String quoteKeyword(String keyword) {
        String trimmed = keyword.trim();
        return trimmed.startsWith("\"") && trimmed.endsWith("\"") ? trimmed : "\"" + trimmed + "\"";
    }

    String defaultStartDate() {
        return systemNow().minusHours(12).format(DATE_TIME_FORMATTER);
    }

    private String nowDate() {
        return systemNow().format(DATE_TIME_FORMATTER);
    }

    private LocalDateTime systemNow() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String extractToken(String responseText) {
        return responseText.replaceFirst("^\"?success", "").replaceAll("\"$", "").trim();
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= EXCERPT_LENGTH) {
            return compact;
        }
        return compact.substring(0, EXCERPT_LENGTH) + "...";
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

    private record FindLogLogin(boolean success, String username, String token, String message) {

        static FindLogLogin success(String username, String token) {
            return new FindLogLogin(true, username, token, "success");
        }

        static FindLogLogin failure(String message) {
            return new FindLogLogin(false, "", "", message);
        }
    }
}
