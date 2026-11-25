-- Development verification only. Run using mysql --force so expected errors
-- do not interrupt the final ROLLBACK. Uses synthetic rows, not existing data.
USE renewiq_db;
START TRANSACTION;
INSERT INTO users (name, email) VALUES ('Constraint verification', CONCAT('schema-test-', UUID(), '@example.invalid'));
SET @test_user = LAST_INSERT_ID();
INSERT INTO chat_conversations (user_id, title) VALUES (@test_user, 'Constraint verification');
SET @test_conversation = LAST_INSERT_ID();
INSERT INTO chat_messages (conversation_id, role, content) VALUES (@test_conversation, 'USER', 'Test message');

-- Expected error 1451: a user with a conversation cannot be deleted.
DELETE FROM users WHERE id = @test_user;
-- Expected error 1452: nonexistent user cannot own a subscription.
INSERT INTO subscriptions (name, category, amount, billing_cycle, next_renewal_date, status, user_id)
VALUES ('Schema test', 'Testing', 1, 'MONTHLY', CURRENT_DATE, 'ACTIVE', -9223372036854775808);
-- Expected error 1452: nonexistent user cannot own a document.
INSERT INTO knowledge_documents (name, content, uploaded_at, user_id)
VALUES ('schema-test.txt', 'Test', CURRENT_TIMESTAMP, -9223372036854775808);
-- Expected error 1452: nonexistent conversation cannot contain a message.
INSERT INTO chat_messages (conversation_id, role, content) VALUES (-9223372036854775808, 'USER', 'Test');
-- Expected error 3819: subscription amount cannot be negative.
INSERT INTO subscriptions (name, category, amount, billing_cycle, next_renewal_date, status)
VALUES ('Schema test', 'Testing', -1, 'MONTHLY', CURRENT_DATE, 'ACTIVE');
-- Expected error 1062: duplicate user email.
INSERT INTO users (name, email) SELECT 'Duplicate', email FROM users WHERE id = @test_user;
-- Expected error 3819: enabled account must have a password hash.
UPDATE users SET enabled = TRUE WHERE id = @test_user;

DELETE FROM chat_conversations WHERE id = @test_conversation;
SELECT COUNT(*) AS messages_after_conversation_delete_must_be_zero
FROM chat_messages WHERE conversation_id = @test_conversation;
ROLLBACK;
SELECT COUNT(*) AS test_user_after_rollback_must_be_zero FROM users WHERE id = @test_user;
