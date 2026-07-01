CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
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
