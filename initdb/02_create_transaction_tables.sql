CREATE TABLE IF NOT EXISTS todo_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    todo_id BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_todo_history_todo_id (todo_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

-- No foreign key: audit attempts must survive rollback of a newly created ToDo.
-- CREATE attempts have a null todo_id because the generated ID is not yet known.
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    todo_id BIGINT UNSIGNED NULL,
    operation VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_log_todo_id (todo_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
