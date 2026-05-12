-- Hibernate @JdbcTypeCode(LONG32VARCHAR) expects LONGTEXT on MariaDB; legacy DDL used TEXT (LONGVARCHAR).
ALTER TABLE ld_notification_settings MODIFY COLUMN employee_email_body LONGTEXT NULL;
ALTER TABLE ld_notification_settings MODIFY COLUMN manager_email_body LONGTEXT NULL;
ALTER TABLE ld_notification_settings MODIFY COLUMN manager_goal_setting_january_body LONGTEXT NULL;
ALTER TABLE ld_notification_settings MODIFY COLUMN employee_goal_setting_january_body LONGTEXT NULL;
