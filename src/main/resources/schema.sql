CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    tool_calls_json LONGTEXT NULL,
    INDEX idx_chat_messages_session_id_id (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_facts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    memory_key VARCHAR(128) NOT NULL,
    memory_value TEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_chat_facts_session_key (session_id, memory_key),
    INDEX idx_chat_facts_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS model_call_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    request_messages_json LONGTEXT NOT NULL,
    response_text LONGTEXT NULL,
    success TINYINT(1) NOT NULL,
    error_type VARCHAR(255) NULL,
    error_message TEXT NULL,
    duration_millis BIGINT NOT NULL DEFAULT 0,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_model_call_logs_session_id_id (session_id, id),
    INDEX idx_model_call_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_model_configs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    temperature DOUBLE NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    active TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_ai_model_configs_provider_model (provider, model),
    INDEX idx_ai_model_configs_enabled_active (enabled, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ai_model_configs (provider, model, display_name, temperature, enabled, active, updated_at)
VALUES
    ('deepseek', 'deepseek-chat', 'DeepSeek Chat', 0.7, 1, 1, NOW(6)),
    ('deepseek', 'deepseek-reasoner', 'DeepSeek Reasoner', 0.7, 1, 0, NOW(6))
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    temperature = VALUES(temperature),
    enabled = VALUES(enabled),
    updated_at = VALUES(updated_at);
