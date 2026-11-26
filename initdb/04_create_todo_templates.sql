CREATE TABLE IF NOT EXISTS todo_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS todo_template_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    CONSTRAINT uq_template_position UNIQUE (template_id, position),
    CONSTRAINT fk_template_items_template FOREIGN KEY (template_id)
        REFERENCES todo_templates(id) ON DELETE CASCADE
);
