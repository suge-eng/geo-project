CREATE DATABASE IF NOT EXISTS geo_backend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE geo_backend;

CREATE TABLE IF NOT EXISTS ai_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL COMMENT 'AI平台: DOUBAO, WENXIN, DEEPSEEK, KIMI, TONGYI',
    account_name VARCHAR(100) NOT NULL COMMENT '账号名称/手机号',
    cookie TEXT COMMENT '登录后的Cookie',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, BANNED, MAINTENANCE, EXHAUSTED',
    daily_limit INT NOT NULL DEFAULT 50 COMMENT '每日请求上限',
    daily_used INT NOT NULL DEFAULT 0 COMMENT '今日已用次数',
    daily_reset_at DATETIME COMMENT '每日计数重置时间',
    last_request_at DATETIME COMMENT '上次请求时间',
    cooldown_until DATETIME COMMENT '冷却截止时间',
    request_interval_ms INT NOT NULL DEFAULT 30000 COMMENT '请求间隔(毫秒)',
    consecutive_failures INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    max_consecutive_failures INT NOT NULL DEFAULT 5 COMMENT '最大连续失败次数',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级, 越大越优先',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_platform_status (platform, status),
    INDEX idx_daily_reset (daily_reset_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI账号池';

CREATE TABLE IF NOT EXISTS task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(32) NOT NULL COMMENT '任务编号',
    user_id VARCHAR(64) DEFAULT 'anonymous' COMMENT '用户标识',
    title VARCHAR(200) COMMENT '任务标题',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, COMPLETED, PARTIAL_FAILED, FAILED, CANCELLED',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总操作数 = AI数量 × 问题数量',
    completed_count INT NOT NULL DEFAULT 0 COMMENT '已完成数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
    total_ai_count INT NOT NULL DEFAULT 0 COMMENT 'AI平台数量',
    total_question_count INT NOT NULL DEFAULT 0 COMMENT '问题数量',
    error_msg VARCHAR(500) COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME COMMENT '完成时间',
    UNIQUE KEY uk_task_no (task_no),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务主表';

CREATE TABLE IF NOT EXISTS task_ai (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(32) NOT NULL,
    ai_platform VARCHAR(32) NOT NULL COMMENT 'AI平台标识',
    ai_display_name VARCHAR(64) COMMENT 'AI平台展示名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务关联AI';

CREATE TABLE IF NOT EXISTS task_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(32) NOT NULL,
    question_text TEXT NOT NULL COMMENT '问题内容',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务关联问题';

CREATE TABLE IF NOT EXISTS task_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(32) NOT NULL,
    task_question_id BIGINT NOT NULL,
    ai_platform VARCHAR(32) NOT NULL COMMENT 'AI平台',
    question_text TEXT NOT NULL COMMENT '问题文本(冗余)',
    answer_text MEDIUMTEXT COMMENT 'AI回答文本',
    screenshot_urls VARCHAR(500) COMMENT '长截图OSS地址',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT',
    error_msg VARCHAR(500) COMMENT '失败原因',
    duration_ms BIGINT COMMENT '耗时(毫秒)',
    rpa_retry_count INT NOT NULL DEFAULT 0 COMMENT 'RPA重试次数',
    account_id BIGINT COMMENT '使用的账号ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME COMMENT '完成时间',
    INDEX idx_task_id (task_id),
    INDEX idx_task_no (task_no),
    INDEX idx_status (status),
    INDEX idx_task_question_id (task_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答结果明细';