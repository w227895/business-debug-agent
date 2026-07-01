package com.fr.ai.debugagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fr.ai.debugagent.domain.ParseResult;
import com.fr.ai.debugagent.domain.PromptAsset;
import com.fr.ai.debugagent.domain.Scenario;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ParseService {

    private static final Pattern ORDER_PATTERN = Pattern.compile("(FR\\d+|订单\\s*[:：]?\\s*([A-Z0-9]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLIGHT_PATTERN = Pattern.compile("([A-Z]{2}\\d{3,5})");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2})");

    private final ObjectMapper objectMapper;

    public ParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParseResult parse(Scenario scenario, PromptAsset prompt, String businessText) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("orderNo", extractOrderNo(businessText));
        json.put("changeType", "TIME_CHANGE");
        json.put("originalFlightNo", extractFirstFlightNo(businessText));
        json.put("newFlightNo", extractLastFlightNo(businessText));

        String dateTime = extractDateTime(businessText);
        if (!dateTime.isBlank()) {
            json.put("newDepartureTime", dateTime.replace("/", "-"));
        }

        String output = json.toPrettyString();
        return new ParseResult("local-heuristic-spring-ai-placeholder", output, output);
    }

    private String extractOrderNo(String text) {
        Matcher matcher = ORDER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        if (matcher.group(1).toUpperCase().startsWith("FR")) {
            return matcher.group(1).toUpperCase();
        }
        return matcher.group(2) == null ? matcher.group(1).trim() : matcher.group(2).trim();
    }

    private String extractFirstFlightNo(String text) {
        Matcher matcher = FLIGHT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase() : "";
    }

    private String extractLastFlightNo(String text) {
        Matcher matcher = FLIGHT_PATTERN.matcher(text);
        String value = "";
        while (matcher.find()) {
            value = matcher.group(1).toUpperCase();
        }
        return value;
    }

    private String extractDateTime(String text) {
        Matcher matcher = DATE_TIME_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
