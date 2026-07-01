package com.fr.ai.debugagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fr.ai.debugagent.domain.JsonValidationResult;
import com.fr.ai.debugagent.domain.OutputSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JsonValidationService {

    private final ObjectMapper objectMapper;
    private final InMemoryAgentRepository repository;

    public JsonValidationService(ObjectMapper objectMapper, InMemoryAgentRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public JsonValidationResult validate(String promptId, String outputJson) {
        OutputSchema schema = repository.findOutputSchema(promptId);
        JsonNode node;
        try {
            node = objectMapper.readTree(outputJson);
        } catch (Exception ex) {
            return new JsonValidationResult(false, false, schema.requiredFields(), List.of(), List.of(), "模型输出不是合法 JSON");
        }

        List<String> missingFields = new ArrayList<>();
        List<String> typeErrors = new ArrayList<>();
        for (String field : schema.requiredFields()) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                missingFields.add(field);
            }
        }

        schema.fieldTypes().forEach((field, type) -> {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return;
            }
            if ("string".equals(type) && !value.isTextual()) {
                typeErrors.add(field + " 应为 string");
            }
        });

        boolean valid = missingFields.isEmpty() && typeErrors.isEmpty();
        String summary = valid ? "JSON 合法且必填字段完整" : "存在缺失字段或类型错误";
        return new JsonValidationResult(valid, true, missingFields, typeErrors, List.of(), summary);
    }
}
