-- Demo data aligned with load-distribution/data/*.json; week = Monday 2026-05-11
INSERT INTO ld_employees (name, capacity_fte) VALUES
    ('Alice Ivanova', 1.000),
    ('Peter Georgiev', 1.000),
    ('Maria Petrova', 0.800);

INSERT INTO ld_projects (name, capacity_fte, color) VALUES
    ('Core Platform', 1.800, '#4CAF50'),
    ('Mobile App', 1.200, '#2196F3'),
    ('Data Hub', 0.800, '#FF9800');

INSERT INTO ld_allocations (employee_id, project_id, week_start_date, fte) VALUES
    (1, 1, '2026-05-11', 0.600),
    (1, 2, '2026-05-11', 0.400),
    (2, 1, '2026-05-11', 0.500),
    (2, 3, '2026-05-11', 0.500),
    (3, 2, '2026-05-11', 0.600);
