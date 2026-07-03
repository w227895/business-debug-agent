package com.fr.ai.debugagent.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record LogRequestHints(List<String> traceIds, String profile, String serviceValues, boolean explicitTimeRange) {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?<![A-Za-z0-9_.-])(web_[A-Za-z0-9][A-Za-z0-9_.-]{8,})(?![A-Za-z0-9_.-])");
    private static final Pattern DIRECT_SERVICE_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9_#-])([a-z][a-z0-9_-]*#[a-z][a-z0-9_-]*)(?![A-Za-z0-9_#-])");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9_#-])([a-z][a-z0-9_-]*)(?![A-Za-z0-9_#-])");
    private static final Pattern SERVICE_SUFFIX_PATTERN = Pattern.compile("(?i)^([a-z][a-z0-9]*)[_-]([a-z][a-z0-9]*)$");
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?i)(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}:\\d{2}(?::\\d{2})?|\\d{1,2}\\s*(?:小时|分钟|点|时)|今天|昨天|前天|最近|近\\s*\\d+|上午|下午|晚上|早上|中午|凌晨)");

    static LogRequestHints fromUserMessage(String message) {
        if (message == null || message.isBlank()) {
            return empty();
        }

        List<String> traceIds = extractTraceIds(message);
        String scrubbedMessage = scrubTraceIds(message, traceIds);
        String directService = firstMatch(DIRECT_SERVICE_PATTERN, scrubbedMessage);
        List<String> tokens = extractTokens(scrubbedMessage);

        String env = null;
        String baseService = null;
        String gray = null;
        String serviceToken = null;

        for (String token : tokens) {
            if (isEnvironment(token)) {
                env = env == null ? token : env;
                continue;
            }

            Matcher suffixMatcher = SERVICE_SUFFIX_PATTERN.matcher(token);
            if (suffixMatcher.matches()) {
                String base = suffixMatcher.group(1).toLowerCase(Locale.ROOT);
                String suffix = suffixMatcher.group(2).toLowerCase(Locale.ROOT);
                if (isEnvironment(suffix)) {
                    baseService = baseService == null ? base : baseService;
                    env = env == null ? suffix : env;
                } else {
                    baseService = base;
                    gray = suffix;
                    serviceToken = base + "_" + suffix;
                }
                continue;
            }

            if (gray == null && isLikelyGray(token)) {
                gray = token;
            }
        }

        String serviceValues = "";
        if (directService != null) {
            serviceValues = directService.toLowerCase(Locale.ROOT);
        } else if (serviceToken != null && env != null) {
            serviceValues = serviceToken + "#" + env;
        } else if (baseService != null && gray != null && env != null) {
            serviceValues = baseService + "_" + gray + "#" + env;
        }

        String profile = env != null && env.startsWith("prod") ? "PROD" : (traceIds.isEmpty() && serviceValues.isBlank() ? "" : "DEV");
        return new LogRequestHints(List.copyOf(traceIds), profile, serviceValues, hasExplicitTimeRange(message));
    }

    static LogRequestHints empty() {
        return new LogRequestHints(List.of(), "", "", false);
    }

    boolean hasAny() {
        return !traceIds.isEmpty() || !profile.isBlank() || !serviceValues.isBlank();
    }

    String primaryKeyword() {
        return traceIds.isEmpty() ? "" : traceIds.get(0);
    }

    String toSystemInstruction() {
        if (!hasAny()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("本轮用户消息中检测到的日志定位信息：\n");
        if (!traceIds.isEmpty()) {
            builder.append("- traceId/查询关键词：").append(String.join(", ", traceIds)).append('\n');
        }
        if (!profile.isBlank()) {
            builder.append("- FindLog profile：").append(profile).append('\n');
        }
        if (!serviceValues.isBlank()) {
            builder.append("- 用户显式指定的 service#machine：").append(serviceValues).append('\n');
        }
        builder.append("""
                日志查询规则：
                1. traceId 只作为 search_findlog_logs 的 keyword，不要根据 traceId 前缀推断服务、环境或机器。
                2. 如果本轮已经检测到用户显式指定的 service#machine，必须优先直接使用该值调用 search_findlog_logs。
                3. 只有用户没有给出具体 service#machine 时，才调用 list_findlog_services 查询候选机器。
                4. traceId 精确查询默认使用 contextLines=0，让 FindLog 执行不带 -C 的普通 grep。
                5. 如果用户本轮没有明确指定查询时间，不要自行生成 startDate/endDate；后端会用系统时间默认查询当前时间往前 12 小时到当前时间。
                """);
        return builder.toString();
    }

    private static List<String> extractTraceIds(String message) {
        Matcher matcher = TRACE_ID_PATTERN.matcher(message);
        Set<String> traceIds = new LinkedHashSet<>();
        while (matcher.find()) {
            traceIds.add(matcher.group());
        }
        return new ArrayList<>(traceIds);
    }

    private static String scrubTraceIds(String message, List<String> traceIds) {
        String result = message;
        for (String traceId : traceIds) {
            result = result.replace(traceId, " ");
        }
        return result;
    }

    private static String firstMatch(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> extractTokens(String message) {
        Matcher matcher = TOKEN_PATTERN.matcher(message);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private static boolean isEnvironment(String token) {
        return token != null && (token.matches("dev[a-z0-9]*") || token.matches("prod[a-z0-9]*"));
    }

    private static boolean hasExplicitTimeRange(String message) {
        return message != null && TIME_PATTERN.matcher(message).find();
    }

    private static boolean isLikelyGray(String token) {
        return token != null
                && token.length() >= 2
                && token.length() <= 8
                && !isEnvironment(token)
                && !"traceid".equals(token)
                && !"trace".equals(token)
                && !"web".equals(token)
                && !"log".equals(token);
    }
}
