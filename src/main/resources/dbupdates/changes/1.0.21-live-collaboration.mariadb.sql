-- Live collaboration (R63): the Yjs updates of a page's working copy, appended by the browsers
-- that edit it at the same time and replayed to everyone who joins. The server does not interpret
-- the bytes; a browser regularly replaces the log with a compact snapshot. Publishing or
-- discarding the working copy clears the log together with the working copy.
CREATE TABLE page_collab_update (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    page_id INT NOT NULL,
    update_bytes MEDIUMBLOB NOT NULL,
    create_date DATETIME(3) NOT NULL,
    INDEX page_collab_update_page (page_id, id),
    CONSTRAINT page_collab_update_page FOREIGN KEY (page_id) REFERENCES page(id) ON DELETE CASCADE
) ENGINE=InnoDB;
