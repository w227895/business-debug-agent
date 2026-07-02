package com.fr.ai.debugagent.api;

import com.fr.ai.debugagent.oms.OmsCookieSession;
import com.fr.ai.debugagent.oms.OmsCookieStore;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderTraceService {

    private static final String ORDER_SERVICE = "order";
    private static final String TRACE_LOG_PATH = "/order/geAllStatusOrdertLogs.do";
    private static final Pattern JSON_TRACE_ID_PATTERN = Pattern.compile(
            "\"(?:traceId|traceID|trace_id)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FR_TRACE_ID_PATTERN = Pattern.compile(
            "\\b[A-Za-z][A-Za-z0-9_-]*_[0-9a-fA-F]{16,}(?:\\.[0-9]+){1,3}\\b");
    private static final int MAX_TRACE_IDS = 20;
    private static final int RESPONSE_PREVIEW_LENGTH = 1800;

    private final BusinessApiProperties apiProperties;
    private final OmsCookieStore cookieStore;
    private final HttpClient httpClient;

    public OrderTraceService(BusinessApiProperties apiProperties, OmsCookieStore cookieStore) {
        this.apiProperties = apiProperties;
        this.cookieStore = cookieStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public OrderTraceLookupResult lookupTraceIds(String parentId, String environment) {
        String env = normalizeEnvironment(environment);
        String baseUrl = trimTrailingSlash(apiProperties.baseUrl(ORDER_SERVICE, env));
        String requestUrl = StringUtils.hasText(baseUrl) ? baseUrl + TRACE_LOG_PATH : "";
        if (!StringUtils.hasText(parentId)) {
            return OrderTraceLookupResult.failure(env, "parentId 不能为空", requestUrl, 0);
        }
        if (!StringUtils.hasText(baseUrl)) {
            return OrderTraceLookupResult.failure(env, "未配置 order 服务环境 URL：" + env, requestUrl, 0);
        }

        OmsCookieSession session = cookieStore.get(env).orElse(null);
        if (session == null || !StringUtils.hasText(session.cookieHeader())) {
            return OrderTraceLookupResult.failure(env, "当前环境没有已缓存的 OMS Cookie，请先调用 login_to_oms 登录：" + env, requestUrl, 0);
        }

        try {
            String body = "parentId=" + URLEncoder.encode(parentId.trim(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Cookie", session.cookieHeader())
                    .header("User-Agent", "business-debug-agent/0.1")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            List<String> traceIds = extractTraceIds(responseBody);
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300 && !traceIds.isEmpty();
            String message = success
                    ? "已从 order 状态日志中提取 traceId"
                    : "接口已返回，但没有识别到 traceId";
            return new OrderTraceLookupResult(
                    env,
                    success,
                    message,
                    requestUrl,
                    response.statusCode(),
                    traceIds,
                    preview(responseBody),
                    Instant.now());
        } catch (Exception ex) {
            return OrderTraceLookupResult.failure(env, "查询 order 状态日志失败：" + rootMessage(ex), requestUrl, 0);
        }
    }

    List<String> extractTraceIds(String value) {
        Set<String> traceIds = new LinkedHashSet<>();
        collectMatches(JSON_TRACE_ID_PATTERN, value, traceIds, true);
        collectMatches(FR_TRACE_ID_PATTERN, value, traceIds, false);
        return new ArrayList<>(traceIds);
    }

    private void collectMatches(Pattern pattern, String value, Set<String> traceIds, boolean useGroup) {
        if (!StringUtils.hasText(value) || traceIds.size() >= MAX_TRACE_IDS) {
            return;
        }
        Matcher matcher = pattern.matcher(value);
        while (matcher.find() && traceIds.size() < MAX_TRACE_IDS) {
            String traceId = useGroup ? matcher.group(1) : matcher.group();
            if (StringUtils.hasText(traceId)) {
                traceIds.add(traceId.trim());
            }
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= RESPONSE_PREVIEW_LENGTH) {
            return compact;
        }
        return compact.substring(0, RESPONSE_PREVIEW_LENGTH) + "...";
    }

    private String normalizeEnvironment(String environment) {
        if (!StringUtils.hasText(environment)) {
            return "deve";
        }
        String normalized = environment.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "devb", "deve", "prod" -> normalized;
            default -> "deve";
        };
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
        String message = current.getMessage();
        return message == null ? throwable.getMessage() : message;
    }
}
