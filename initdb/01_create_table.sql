CREATE TABLE todos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id BIGINT UNSIGNED NULL,
    display_order INT NOT NULL DEFAULT 2147483647,
    title VARCHAR(255) NOT NULL,
    detail VARCHAR(255) NULL,
    category VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 2,
    due_date DATE NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at DATETIME NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_todos_parent_id (parent_id),
    CONSTRAINT fk_todos_parent FOREIGN KEY (parent_id) REFERENCES todos(id) ON DELETE CASCADE,
    CONSTRAINT chk_todos_category CHECK (
        category IN (
            'デザイン',
            'マーケティング',
            'プログラミング',
            '資格',
            '就職活動'
        )
    ),
    CONSTRAINT chk_todos_priority CHECK (priority IN (1, 2, 3)),
    CONSTRAINT chk_todos_completed CHECK (completed IN (FALSE, TRUE)),
    INDEX idx_todos_category (category),
    INDEX idx_todos_due_date (due_date)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO todos (title, detail, category, priority, due_date, completed, completed_at)
VALUES ('ポートフォリオのワイヤーを描く', '', 'デザイン', 1, '2026-11-15', TRUE, '2026-04-05 00:00:00');
