-- Full-text search (R68): InnoDB FULLTEXT indexes on the page title, on title and content, and on
-- label names, plus the extracted text of attachments (text, Markdown, CSV, JSON, XML, HTML, PDF)
-- in file_text. The search ranks title, labels, content and attachments; the indexes on page and
-- tag follow every write by themselves, file_text is filled on upload and by a catch-up job.
ALTER TABLE page ADD FULLTEXT INDEX page_fulltext_name (name);
ALTER TABLE page ADD FULLTEXT INDEX page_fulltext_text (name, content);
ALTER TABLE tag ADD FULLTEXT INDEX tag_fulltext_name (name);

CREATE TABLE file_text (
    file_id INT NOT NULL PRIMARY KEY,
    text MEDIUMTEXT NOT NULL,
    indexed_at DATETIME(3) NOT NULL,
    CONSTRAINT file_text_file FOREIGN KEY (file_id) REFERENCES file(id) ON DELETE CASCADE,
    FULLTEXT INDEX file_text_fulltext (text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
