package com.fr.ai.debugagent.chat;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

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

    public String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    public void addMessage(String sessionId, String role, String content) {
        jdbcTemplate.update("""
                        INSERT INTO chat_messages (session_id, role, content, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                sessionId,
                role,
                content,
                LocalDateTime.now());
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return jdbcTemplate.query("""
                        SELECT role, content, created_at
                        FROM chat_messages
                        WHERE session_id = ?
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> new ChatMessage(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
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
}
