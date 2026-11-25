ALTER TABLE chat_conversations
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN processing_started_at DATETIME(6) NULL,
    ADD INDEX idx_conversations_user_updated (user_id, updated_at);
UPDATE chat_conversations SET updated_at = created_at;
ALTER TABLE chat_messages
    ADD COLUMN metadata LONGTEXT NULL,
    ADD COLUMN request_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_chat_message_request UNIQUE (conversation_id, request_id);
