-- Page templates (R65): a space administrator prepares a title, content and labels that "New page"
-- offers in that space. A template is not a page: it has no history, permissions, comments or
-- attachments and never shows up in navigation or search. Labels are stored one per line.
CREATE TABLE page_template (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    labels VARCHAR(4096) NOT NULL DEFAULT '',
    created_by INT NULL,
    create_date DATETIME(3) NOT NULL,
    changed_by INT NULL,
    change_date DATETIME(3) NOT NULL,
    UNIQUE INDEX page_template_name (space_id, name),
    CONSTRAINT page_template_space FOREIGN KEY (space_id) REFERENCES space(id) ON DELETE CASCADE,
    CONSTRAINT page_template_creator FOREIGN KEY (created_by) REFERENCES `user`(id) ON DELETE SET NULL,
    CONSTRAINT page_template_editor FOREIGN KEY (changed_by) REFERENCES `user`(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
