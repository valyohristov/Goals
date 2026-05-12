CREATE TABLE ld_goal_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    goal_year INT NOT NULL,
    CONSTRAINT uq_ld_goal_plan UNIQUE (employee_id, goal_year),
    CONSTRAINT fk_ld_goal_plan_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE
);

CREATE TABLE ld_goal_slot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_plan_id BIGINT NOT NULL,
    slot_index INT NOT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '',
    description VARCHAR(4000) NOT NULL DEFAULT '',
    CONSTRAINT uq_ld_goal_slot UNIQUE (goal_plan_id, slot_index),
    CONSTRAINT fk_ld_goal_slot_plan FOREIGN KEY (goal_plan_id) REFERENCES ld_goal_plan (id) ON DELETE CASCADE
);

CREATE TABLE ld_goal_month (
    goal_slot_id BIGINT NOT NULL,
    month_index INT NOT NULL,
    doing_text VARCHAR(8000) NOT NULL DEFAULT '',
    outcome_text VARCHAR(8000) NOT NULL DEFAULT '',
    PRIMARY KEY (goal_slot_id, month_index),
    CONSTRAINT fk_ld_goal_month_slot FOREIGN KEY (goal_slot_id) REFERENCES ld_goal_slot (id) ON DELETE CASCADE
);

CREATE TABLE ld_goal_checkin (
    goal_slot_id BIGINT NOT NULL,
    period_index INT NOT NULL,
    meeting_date VARCHAR(10) NOT NULL DEFAULT '',
    employee_notes VARCHAR(4000) NOT NULL DEFAULT '',
    manager_notes VARCHAR(4000) NOT NULL DEFAULT '',
    PRIMARY KEY (goal_slot_id, period_index),
    CONSTRAINT fk_ld_goal_ci_slot FOREIGN KEY (goal_slot_id) REFERENCES ld_goal_slot (id) ON DELETE CASCADE
);
