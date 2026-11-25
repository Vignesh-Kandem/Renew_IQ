CREATE TABLE renewal_email_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    renewal_date DATE NOT NULL,
    days_before INT NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_renewal_email UNIQUE (subscription_id, renewal_date, days_before),
    CONSTRAINT fk_renewal_email_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE,
    CONSTRAINT chk_renewal_email_days CHECK (days_before IN (7, 3, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
