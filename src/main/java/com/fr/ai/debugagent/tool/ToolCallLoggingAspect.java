package com.fr.ai.debugagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Aspect
@Component
public class ToolCallLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallLoggingAspect.class);
    private static final int MAX_ARGS_LENGTH = 2_000;
    private static final int MAX_RESULT_LENGTH = 4_000;
    private static final Pattern JSON_SECRET_FIELD = Pattern.compile(
            "(?i)(\"(?:password|passwd|cookie|cookieHeader|maskedCookieHeader|totpSecret|totp-secret|secret|apiKey|api-key|token)\"\\s*:\\s*\")([^\"]*)(\")");
    private static final Pattern INLINE_SECRET_FIELD = Pattern.compile(
            "(?i)\\b(password|passwd|cookie|cookieHeader|maskedCookieHeader|totpSecret|totp-secret|secret|apiKey|api-key|token)\\s*=\\s*([^;\\s,}]+)");
    private static final Pattern COOKIE_PAIR = Pattern.compile(
            "(?i)\\b([A-Z0-9_]*(?:COOKIE|SESSION|TOKEN)[A-Z0-9_]*=)[^;\\s,}]+");

    private final ObjectMapper objectMapper;

    public ToolCallLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint, Tool tool) throws Throwable {
        String toolName = StringUtils.hasText(tool.name()) ? tool.name() : joinPoint.getSignature().getName();
        String methodName = joinPoint.getSignature().toShortString();
        String args = formatArgs(joinPoint);
        long startedAt = System.nanoTime();

        logger.info("AI tool call start: tool={}, method={}, args={}", toolName, methodName, args);
        try {
            Object result = joinPoint.proceed();
            long elapsedMs = elapsedMillis(startedAt);
            logger.info(
                    "AI tool call success: tool={}, method={}, elapsedMs={}, result={}",
                    toolName,
                    methodName,
                    elapsedMs,
                    formatValue(result, MAX_RESULT_LENGTH));
            return result;
        } catch (Throwable ex) {
            long elapsedMs = elapsedMillis(startedAt);
            logger.warn(
                    "AI tool call failed: tool={}, method={}, elapsedMs={}, error={}: {}",
                    toolName,
                    methodName,
                    elapsedMs,
                    ex.getClass().getName(),
                    rootMessage(ex));
            throw ex;
        }
    }

    private String formatArgs(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String name = names != null && i < names.length && StringUtils.hasText(names[i]) ? names[i] : "arg" + i;
            args.put(name, values[i]);
        }
        return formatValue(args, MAX_ARGS_LENGTH);
    }

    private String formatValue(Object value, int maxLength) {
        String raw;
        if (value == null) {
            raw = "null";
        } else if (value instanceof CharSequence sequence) {
            raw = sequence.toString();
        } else {
            try {
                raw = objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                raw = String.valueOf(value);
            }
        }
        return truncate(maskSecrets(raw), maxLength);
    }

    private String maskSecrets(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = JSON_SECRET_FIELD.matcher(value).replaceAll("$1***$3");
        masked = INLINE_SECRET_FIELD.matcher(masked).replaceAll("$1=***");
        return COOKIE_PAIR.matcher(masked).replaceAll("$1***");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated, length=" + value.length() + ")";
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
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
