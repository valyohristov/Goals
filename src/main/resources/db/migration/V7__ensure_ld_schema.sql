-- Idempotent repair: creates missing LD tables/indexes when Flyway history is ahead of actual DDL
-- (e.g. orphan flyway_schema_history rows or partially migrated schemas).

CREATE TABLE IF NOT EXISTS ld_employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity_fte DECIMAL(5,3) NOT NULL
);

ALTER TABLE ld_employees ADD COLUMN IF NOT EXISTS manager_id BIGINT NULL;
ALTER TABLE ld_employees ADD COLUMN IF NOT EXISTS is_manager TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ld_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity_fte DECIMAL(5,3) NOT NULL,
    color VARCHAR(32) NULL,
    CONSTRAINT uq_ld_project_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS ld_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    fte DECIMAL(5,3) NOT NULL,
    CONSTRAINT fk_ld_alloc_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ld_alloc_proj FOREIGN KEY (project_id) REFERENCES ld_projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_ld_alloc_emp_proj_week UNIQUE (employee_id, project_id, week_start_date)
);

CREATE INDEX IF NOT EXISTS idx_ld_alloc_week ON ld_allocations (week_start_date);

CREATE TABLE IF NOT EXISTS ld_app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(320) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role VARCHAR(32) NOT NULL,
    employee_id BIGINT NULL,
    username_manual TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT uq_ld_user_username UNIQUE (username),
    CONSTRAINT fk_ld_user_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ld_app_users_employee ON ld_app_users (employee_id);

CREATE TABLE IF NOT EXISTS ld_password_reset_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME NULL,
    CONSTRAINT fk_ld_prt_user FOREIGN KEY (user_id) REFERENCES ld_app_users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ld_week_settings (
    week_start_date DATE PRIMARY KEY,
    manual TINYINT(1) NOT NULL DEFAULT 0,
    copy_to_next_week TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS ld_goal_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    goal_year INT NOT NULL,
    CONSTRAINT uq_ld_goal_plan UNIQUE (employee_id, goal_year),
    CONSTRAINT fk_ld_goal_plan_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ld_goal_slot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_plan_id BIGINT NOT NULL,
    slot_index INT NOT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '',
    description VARCHAR(4000) NOT NULL DEFAULT '',
    CONSTRAINT uq_ld_goal_slot UNIQUE (goal_plan_id, slot_index),
    CONSTRAINT fk_ld_goal_slot_plan FOREIGN KEY (goal_plan_id) REFERENCES ld_goal_plan (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ld_goal_month (
    goal_slot_id BIGINT NOT NULL,
    month_index INT NOT NULL,
    doing_text VARCHAR(8000) NOT NULL DEFAULT '',
    outcome_text VARCHAR(8000) NOT NULL DEFAULT '',
    PRIMARY KEY (goal_slot_id, month_index),
    CONSTRAINT fk_ld_goal_month_slot FOREIGN KEY (goal_slot_id) REFERENCES ld_goal_slot (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ld_goal_checkin (
    goal_slot_id BIGINT NOT NULL,
    period_index INT NOT NULL,
    meeting_date VARCHAR(10) NOT NULL DEFAULT '',
    employee_notes VARCHAR(4000) NOT NULL DEFAULT '',
    manager_notes VARCHAR(4000) NOT NULL DEFAULT '',
    PRIMARY KEY (goal_slot_id, period_index),
    CONSTRAINT fk_ld_goal_ci_slot FOREIGN KEY (goal_slot_id) REFERENCES ld_goal_slot (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ld_notification_settings (
    id TINYINT PRIMARY KEY,
    notifications_enabled TINYINT(1) NOT NULL DEFAULT 0,
    tenant_id VARCHAR(128) NOT NULL DEFAULT '',
    client_id VARCHAR(128) NOT NULL DEFAULT '',
    client_secret VARCHAR(512) NOT NULL DEFAULT '',
    sender_user VARCHAR(320) NOT NULL DEFAULT '',
    from_name VARCHAR(200) NOT NULL DEFAULT 'Goals',
    from_email VARCHAR(320) NOT NULL DEFAULT '',
    employee_email_subject VARCHAR(500) NOT NULL DEFAULT '',
    employee_email_body LONGTEXT NULL,
    manager_email_subject VARCHAR(500) NOT NULL DEFAULT '',
    manager_email_body LONGTEXT NULL,
    manager_goal_setting_january_subject VARCHAR(500) NOT NULL DEFAULT '',
    manager_goal_setting_january_body LONGTEXT NULL,
    employee_goal_setting_january_subject VARCHAR(500) NOT NULL DEFAULT '',
    employee_goal_setting_january_body LONGTEXT NULL
);

INSERT IGNORE INTO ld_notification_settings (id) VALUES (1);

CREATE TABLE IF NOT EXISTS ld_sent_emails (
    id VARCHAR(64) PRIMARY KEY,
    sent_at DATETIME NOT NULL,
    to_addr VARCHAR(320) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    trigger_kind VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_msg VARCHAR(2000) NULL,
    planned_key VARCHAR(400) NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_ld_sent_emails_sent_at ON ld_sent_emails (sent_at DESC);
