-- Comments on pages, authored by registered users only.
CREATE TABLE page_comment (
    id          INTEGER  NOT NULL AUTO_INCREMENT,
    page_id     INTEGER  NOT NULL,
    user_id     INTEGER  NOT NULL,
    content     TEXT     NOT NULL,
    create_date DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_page_comment_page FOREIGN KEY (page_id) REFERENCES page  (id) ON DELETE CASCADE,
    CONSTRAINT fk_page_comment_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

CREATE INDEX idx_page_comment_page ON page_comment (page_id, create_date);
