CREATE TABLE IF NOT EXISTS sprint (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CONSTRAINT chk_sprint_dates CHECK (end_date >= start_date)
);
CREATE TABLE IF NOT EXISTS sprint_task (
    sprint_id BIGINT NOT NULL,
    todo_id BIGINT NOT NULL,
    PRIMARY KEY (sprint_id, todo_id),
    CONSTRAINT fk_sprint_task_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id) ON DELETE CASCADE
);
