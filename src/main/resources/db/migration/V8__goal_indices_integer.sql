-- Hibernate maps Integer/int to JDBC INTEGER; legacy DDL used TINYINT and fails ddl-auto=validate.
ALTER TABLE ld_goal_slot MODIFY COLUMN slot_index INT NOT NULL;
ALTER TABLE ld_goal_month MODIFY COLUMN month_index INT NOT NULL;
ALTER TABLE ld_goal_checkin MODIFY COLUMN period_index INT NOT NULL;
