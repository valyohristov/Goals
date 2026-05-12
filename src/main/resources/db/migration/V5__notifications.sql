CREATE TABLE ld_notification_settings (
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

INSERT INTO ld_notification_settings (id) VALUES (1);

CREATE TABLE ld_sent_emails (
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

CREATE INDEX idx_ld_sent_emails_sent_at ON ld_sent_emails (sent_at DESC);
