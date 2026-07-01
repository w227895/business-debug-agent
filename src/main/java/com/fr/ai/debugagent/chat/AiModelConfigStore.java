package com.fr.ai.debugagent.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AiModelConfigStore {

    private final JdbcTemplate jdbcTemplate;

    public AiModelConfigStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiModelConfig> listModels() {
        return jdbcTemplate.query("""
                        SELECT id, provider, model, display_name, temperature, enabled, active, updated_at
                        FROM ai_model_configs
                        WHERE enabled = 1
                        ORDER BY provider ASC, id ASC
                        """,
                (rs, rowNum) -> new AiModelConfig(
                        rs.getLong("id"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("display_name"),
                        rs.getObject("temperature", Double.class),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("active"),
                        rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    public AiModelConfig getActiveModel() {
        List<AiModelConfig> activeModels = jdbcTemplate.query("""
                        SELECT id, provider, model, display_name, temperature, enabled, active, updated_at
                        FROM ai_model_configs
                        WHERE enabled = 1 AND active = 1
                        ORDER BY id ASC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new AiModelConfig(
                        rs.getLong("id"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("display_name"),
                        rs.getObject("temperature", Double.class),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("active"),
                        rs.getTimestamp("updated_at").toLocalDateTime()));
        if (!activeModels.isEmpty()) {
            return activeModels.get(0);
        }

        AiModelConfig fallback = listModels().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可用的模型配置"));
        activateModel(fallback.id());
        return getActiveModel();
    }

    @Transactional
    public AiModelConfig activateModel(long modelId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM ai_model_configs
                        WHERE id = ? AND enabled = 1
                        """,
                Integer.class,
                modelId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("模型配置不存在或未启用：" + modelId);
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE ai_model_configs SET active = 0, updated_at = ?", now);
        jdbcTemplate.update("UPDATE ai_model_configs SET active = 1, updated_at = ? WHERE id = ?", now, modelId);
        return getActiveModel();
    }
}
