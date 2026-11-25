CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    subscription_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_cycle ENUM('MONTHLY','QUARTERLY','WEEKLY','YEARLY') NOT NULL,
    renewal_date DATE NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payments_user_id (user_id, id),
    CONSTRAINT uk_payment_subscription_cycle UNIQUE (subscription_id, renewal_date),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- subscription_id is a historical reference, intentionally without a foreign key:
-- deleting the tracked subscription must not erase its payment history.
