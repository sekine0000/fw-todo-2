CREATE TABLE IF NOT EXISTS task_dependencies (
    prerequisite_id BIGINT UNSIGNED NOT NULL,
    successor_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (prerequisite_id, successor_id),
    CONSTRAINT fk_dependency_prerequisite FOREIGN KEY (prerequisite_id) REFERENCES todos(id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_successor FOREIGN KEY (successor_id) REFERENCES todos(id) ON DELETE CASCADE,
    CONSTRAINT chk_dependency_self CHECK (prerequisite_id <> successor_id)
);

CREATE TABLE IF NOT EXISTS task_dependency_lock (id INT PRIMARY KEY);
INSERT INTO task_dependency_lock (id)
SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM task_dependency_lock WHERE id = 1);
