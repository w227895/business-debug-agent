package com.fr.ai.debugagent.chat;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatSessionMemory {

    private final JdbcTemplate jdbcTemplate;

    public ChatSessionMemory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTokenColumns() {
        ensureColumn("prompt_tokens", "INT NOT NULL DEFAULT 0");
        ensureColumn("completion_tokens", "INT NOT NULL DEFAULT 0");
        ensureColumn("total_tokens", "INT NOT NULL DEFAULT 0");
    }

    public String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    public void addMessage(String sessionId, String role, String content) {
        addMessage(sessionId, role, content, ChatTokenUsage.empty());
    }

    public void addMessage(String sessionId, String role, String content, ChatTokenUsage tokenUsage) {
        ChatTokenUsage usage = tokenUsage == null ? ChatTokenUsage.empty() : tokenUsage;
        jdbcTemplate.update("""
                        INSERT INTO chat_messages (
                            session_id,
                            role,
                            content,
                            created_at,
                            prompt_tokens,
                            completion_tokens,
                            total_tokens
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                sessionId,
                role,
                content,
                LocalDateTime.now(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens());
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return jdbcTemplate.query("""
                        SELECT role, content, created_at, prompt_tokens, completion_tokens, total_tokens
                        FROM chat_messages
                        WHERE session_id = ?
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> new ChatMessage(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        new ChatTokenUsage(
                                rs.getInt("prompt_tokens"),
                                rs.getInt("completion_tokens"),
                                rs.getInt("total_tokens"))),
                sessionId);
    }

    public void remember(String sessionId, String key, String value) {
        jdbcTemplate.update("""
                        INSERT INTO chat_facts (session_id, memory_key, memory_value, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            memory_value = VALUES(memory_value),
                            updated_at = VALUES(updated_at)
                        """,
                sessionId,
                key,
                value,
                LocalDateTime.now());
    }

    public void addModelCallLog(
            String sessionId,
            String provider,
            String model,
            String requestMessagesJson,
            String responseText,
            boolean success,
            String errorType,
            String errorMessage,
            long durationMillis,
            ChatTokenUsage tokenUsage) {
        ChatTokenUsage usage = tokenUsage == null ? ChatTokenUsage.empty() : tokenUsage;
        jdbcTemplate.update("""
                        INSERT INTO model_call_logs (
                            session_id,
                            provider,
                            model,
                            request_messages_json,
                            response_text,
                            success,
                            error_type,
                            error_message,
                            duration_millis,
                            prompt_tokens,
                            completion_tokens,
                            total_tokens,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                sessionId,
                provider,
                model,
                requestMessagesJson,
                responseText,
                success,
                errorType,
                errorMessage,
                durationMillis,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                LocalDateTime.now());
    }

    public Map<String, String> getFacts(String sessionId) {
        List<Map.Entry<String, String>> entries = jdbcTemplate.query("""
                        SELECT memory_key, memory_value
                        FROM chat_facts
                        WHERE session_id = ?
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> Map.entry(
                        rs.getString("memory_key"),
                        rs.getString("memory_value")),
                sessionId);

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    public ChatTokenUsage getTotalTokenUsage(String sessionId) {
        return jdbcTemplate.queryForObject("""
                        SELECT
                            COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                            COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                            COALESCE(SUM(total_tokens), 0) AS total_tokens
                        FROM chat_messages
                        WHERE session_id = ?
                        """,
                (rs, rowNum) -> new ChatTokenUsage(
                        rs.getInt("prompt_tokens"),
                        rs.getInt("completion_tokens"),
                        rs.getInt("total_tokens")),
                sessionId);
    }

    public List<ModelCallLog> getModelCallLogs(String sessionId) {
        return jdbcTemplate.query("""
                        SELECT
                            id,
                            session_id,
                            provider,
                            model,
                            request_messages_json,
                            response_text,
                            success,
                            error_type,
                            error_message,
                            duration_millis,
                            prompt_tokens,
                            completion_tokens,
                            total_tokens,
                            created_at
                        FROM model_call_logs
                        WHERE session_id = ?
                        ORDER BY id DESC
                        LIMIT 30
                        """,
                (rs, rowNum) -> new ModelCallLog(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("request_messages_json"),
                        rs.getString("response_text"),
                        rs.getBoolean("success"),
                        rs.getString("error_type"),
                        rs.getString("error_message"),
                        rs.getLong("duration_millis"),
                        new ChatTokenUsage(
                                rs.getInt("prompt_tokens"),
                                rs.getInt("completion_tokens"),
                                rs.getInt("total_tokens")),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                sessionId);
    }

    @Transactional
    public int deleteSession(String sessionId) {
        int modelCallLogs = jdbcTemplate.update(
                "DELETE FROM model_call_logs WHERE session_id = ?",
                sessionId);
        int facts = jdbcTemplate.update(
                "DELETE FROM chat_facts WHERE session_id = ?",
                sessionId);
        int messages = jdbcTemplate.update(
                "DELETE FROM chat_messages WHERE session_id = ?",
                sessionId);
        return modelCallLogs + facts + messages;
    }

    public List<ChatSessionSummary> listSessions() {
        return jdbcTemplate.query("""
                        SELECT
                            grouped.session_id,
                            COALESCE(
                                (
                                    SELECT first_user.content
                                    FROM chat_messages first_user
                                    WHERE first_user.session_id = grouped.session_id
                                      AND first_user.role = 'user'
                                    ORDER BY first_user.id ASC
                                    LIMIT 1
                                ),
                                '新对话'
                            ) AS title,
                            grouped.updated_at
                        FROM (
                            SELECT session_id, MAX(created_at) AS updated_at
                            FROM chat_messages
                            GROUP BY session_id
                        ) grouped
                        ORDER BY grouped.updated_at DESC
                        LIMIT 50
                        """,
                (rs, rowNum) -> new ChatSessionSummary(
                        rs.getString("session_id"),
                        summarizeTitle(rs.getString("title")),
                        rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    private String summarizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新对话";
        }
        String normalized = title.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 28) {
            return normalized;
        }
        return normalized.substring(0, 28) + "...";
    }

    private void ensureColumn(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'chat_messages'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE chat_messages ADD COLUMN " + columnName + " " + definition);
        }
    }
}
