package com.fr.ai.debugagent.oms;

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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class OmsLoginService {

    private final OmsLoginProperties properties;
    private final TotpGenerator totpGenerator;
    private final OmsPasswordEncryptor passwordEncryptor;
    private final OmsCookieStore cookieStore;
    private final HttpClient httpClient;

    public OmsLoginService(
            OmsLoginProperties properties,
            TotpGenerator totpGenerator,
            OmsPasswordEncryptor passwordEncryptor,
            OmsCookieStore cookieStore) {
        this.properties = properties;
        this.totpGenerator = totpGenerator;
        this.passwordEncryptor = passwordEncryptor;
        this.cookieStore = cookieStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public OmsLoginResult login(String environment) {
        String env = normalizeEnv(environment);
        OmsLoginProperties.Account account = properties.getAccounts().get(env);
        if (account == null) {
            return OmsLoginResult.failure(env, "未找到 OMS 环境配置：" + env, "", "", 0);
        }
        if (!account.isEnabled()) {
            return OmsLoginResult.failure(env, "OMS 环境未启用登录：" + env, account.getSsoHost(), account.getOmsHost(), 0);
        }
        String validationError = validateAccount(account);
        if (validationError != null) {
            return OmsLoginResult.failure(env, validationError, account.getSsoHost(), account.getOmsHost(), 0);
        }

        try {
            String totp = totpGenerator.now(account.getTotpSecret());
            String encryptedPassword = passwordEncryptor.encrypt(account.getPassword(), properties.getPublicKey());
            URI loginUri = URI.create("https://" + account.getSsoHost() + "/sso/login.do");
            Map<String, String> formValues = new LinkedHashMap<>();
            formValues.put("username", account.getUsername());
            formValues.put("passwd", encryptedPassword);
            formValues.put("level", String.valueOf(account.getLevel()));
            formValues.put("googleCode", totp);
            formValues.put("browserFingerprint", "");
            formValues.put("url", "https://" + account.getOmsHost());
            String form = formBody(formValues);

            HttpRequest request = HttpRequest.newBuilder(loginUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "business-debug-agent/0.1")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<String> setCookies = response.headers().allValues("set-cookie");
            List<String> cookies = setCookies.stream()
                    .map(this::toCookiePair)
                    .filter(StringUtils::hasText)
                    .toList();
            if (cookies.isEmpty()) {
                return OmsLoginResult.failure(
                        env,
                        "OMS 登录未返回 Cookie，HTTP " + response.statusCode(),
                        account.getSsoHost(),
                        account.getOmsHost(),
                        response.statusCode());
            }

            OmsCookieSession session = new OmsCookieSession(
                    env,
                    account.getSsoHost(),
                    account.getOmsHost(),
                    String.join("; ", cookies),
                    cookies.stream().map(this::cookieName).toList(),
                    response.statusCode(),
                    Instant.now());
            cookieStore.save(session);
            return OmsLoginResult.success(session, "OMS 登录成功，Cookie 已缓存在服务端。");
        } catch (Exception ex) {
            return OmsLoginResult.failure(
                    env,
                    "OMS 登录失败：" + rootMessage(ex),
                    account.getSsoHost(),
                    account.getOmsHost(),
                    0);
        }
    }

    private String validateAccount(OmsLoginProperties.Account account) {
        if (!StringUtils.hasText(account.getSsoHost())) {
            return "OMS SSO host 未配置";
        }
        if (!StringUtils.hasText(account.getOmsHost())) {
            return "OMS host 未配置";
        }
        if (!StringUtils.hasText(account.getUsername())) {
            return "OMS username 未配置";
        }
        if (!StringUtils.hasText(account.getPassword())) {
            return "OMS password 未配置";
        }
        if (!StringUtils.hasText(account.getTotpSecret())) {
            return "OMS TOTP secret 未配置";
        }
        return null;
    }

    private String formBody(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String toCookiePair(String setCookie) {
        int separator = setCookie.indexOf(';');
        return separator >= 0 ? setCookie.substring(0, separator) : setCookie;
    }

    private String cookieName(String cookiePair) {
        int separator = cookiePair.indexOf('=');
        return separator >= 0 ? cookiePair.substring(0, separator) : cookiePair;
    }

    private String normalizeEnv(String environment) {
        if (!StringUtils.hasText(environment)) {
            return "deve";
        }
        return environment.trim().toLowerCase(Locale.ROOT);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }
}
