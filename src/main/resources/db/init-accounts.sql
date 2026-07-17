USE geo_backend;

INSERT INTO ai_account (platform, account_name, status, daily_limit, daily_used, request_interval_ms, consecutive_failures, max_consecutive_failures, priority) VALUES
('DOUBAO', 'doubao_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('DOUBAO', 'doubao_test_2', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('WENXIN', 'wenxin_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('WENXIN', 'wenxin_test_2', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('DEEPSEEK', 'deepseek_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('DEEPSEEK', 'deepseek_test_2', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('KIMI', 'kimi_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('TONGYI', 'tongyi_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('CHATGPT', 'chatgpt_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10),
('CLAUDE', 'claude_test_1', 'ACTIVE', 50, 0, 30000, 0, 5, 10);

INSERT INTO ai_account (platform, account_name, status, daily_limit, daily_used, request_interval_ms, consecutive_failures, max_consecutive_failures, priority) VALUES
('DOUBAO', 'doubao_backup_1', 'ACTIVE', 30, 0, 45000, 0, 5, 5),
('WENXIN', 'wenxin_backup_1', 'ACTIVE', 30, 0, 45000, 0, 5, 5),
('DEEPSEEK', 'deepseek_backup_1', 'ACTIVE', 30, 0, 45000, 0, 5, 5);

INSERT INTO ai_account (platform, account_name, status, daily_limit, daily_used, request_interval_ms, consecutive_failures, max_consecutive_failures, priority) VALUES
('DOUBAO', 'doubao_banned_1', 'BANNED', 50, 50, 30000, 5, 5, 0);

COMMIT;