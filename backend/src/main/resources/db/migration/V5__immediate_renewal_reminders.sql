-- New subscriptions may need an immediate email on any day in the next week.
ALTER TABLE renewal_email_deliveries DROP CHECK chk_renewal_email_days;
ALTER TABLE renewal_email_deliveries
    ADD CONSTRAINT chk_renewal_email_days CHECK (days_before BETWEEN 0 AND 7);
