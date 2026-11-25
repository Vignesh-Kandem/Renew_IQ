-- MySQL 8. Existing subscription/document rows are preserved.
-- Ownership stays NULL until trusted server authentication assigns it.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_enabled_password CHECK (enabled = FALSE OR password_hash IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Also supports a new, empty database using the same migration.
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    billing_cycle ENUM('MONTHLY','QUARTERLY','WEEKLY','YEARLY') NOT NULL,
    next_renewal_date DATE NOT NULL,
    status ENUM('ACTIVE','CANCELLED','PAUSED') NOT NULL,
    notes VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE subscriptions
    ADD COLUMN user_id BIGINT NULL,
    ADD INDEX idx_subscriptions_user_renewal (user_id, next_renewal_date),
    ADD CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_subscriptions_amount CHECK (amount >= 0);

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(180) NOT NULL,
    content LONGTEXT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE knowledge_documents
    ADD COLUMN user_id BIGINT NULL,
    ADD INDEX idx_documents_user_uploaded (user_id, uploaded_at),
    ADD CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

CREATE TABLE chat_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_conversations_user_created (user_id, created_at),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role ENUM('USER','ASSISTANT') NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_messages_conversation_id (conversation_id, id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
