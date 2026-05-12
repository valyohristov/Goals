ALTER TABLE ld_employees
    ADD COLUMN manager_id BIGINT NULL,
    ADD COLUMN is_manager TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE ld_employees
    ADD CONSTRAINT fk_ld_emp_mgr FOREIGN KEY (manager_id) REFERENCES ld_employees (id) ON DELETE SET NULL;

CREATE TABLE ld_app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(320) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role VARCHAR(32) NOT NULL,
    employee_id BIGINT NULL,
    username_manual TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT uq_ld_user_username UNIQUE (username),
    CONSTRAINT fk_ld_user_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE
);

CREATE INDEX idx_ld_app_users_employee ON ld_app_users (employee_id);

CREATE TABLE ld_password_reset_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME NULL,
    CONSTRAINT fk_ld_prt_user FOREIGN KEY (user_id) REFERENCES ld_app_users (id) ON DELETE CASCADE
);

CREATE TABLE ld_week_settings (
    week_start_date DATE PRIMARY KEY,
    manual TINYINT(1) NOT NULL DEFAULT 0,
    copy_to_next_week TINYINT(1) NOT NULL DEFAULT 1
);
