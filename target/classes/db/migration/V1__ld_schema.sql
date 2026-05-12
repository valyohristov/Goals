CREATE TABLE ld_employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity_fte DECIMAL(5,3) NOT NULL
);

CREATE TABLE ld_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity_fte DECIMAL(5,3) NOT NULL,
    color VARCHAR(32) NULL,
    CONSTRAINT uq_ld_project_name UNIQUE (name)
);

CREATE TABLE ld_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    fte DECIMAL(5,3) NOT NULL,
    CONSTRAINT fk_ld_alloc_emp FOREIGN KEY (employee_id) REFERENCES ld_employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ld_alloc_proj FOREIGN KEY (project_id) REFERENCES ld_projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_ld_alloc_emp_proj_week UNIQUE (employee_id, project_id, week_start_date)
);

CREATE INDEX idx_ld_alloc_week ON ld_allocations (week_start_date);
