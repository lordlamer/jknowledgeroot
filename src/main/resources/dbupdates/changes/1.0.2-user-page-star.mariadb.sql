-- Per-user starred pages (favorites)
CREATE TABLE user_page_star (
    user_id     INTEGER  NOT NULL,
    page_id     INTEGER  NOT NULL,
    create_date DATETIME NOT NULL,
    PRIMARY KEY (user_id, page_id),
    CONSTRAINT fk_user_page_star_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_page_star_page FOREIGN KEY (page_id) REFERENCES page  (id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8;

CREATE INDEX idx_user_page_star_user ON user_page_star (user_id);
CREATE INDEX idx_user_page_star_page ON user_page_star (page_id);
