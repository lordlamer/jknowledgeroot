-- Watching pages and spaces, notifications and the durable mail queue (R66). A user watches a page or
-- a space and is told about publications, comments and moves there; a mention in a comment reaches
-- the named user. Every notification is kept for the user's own history; mails are queued in the
-- database and sent by a worker with retries, so a mail server outage loses nothing.
CREATE TABLE page_watch (
    user_id INT NOT NULL,
    page_id INT NOT NULL,
    create_date DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, page_id),
    INDEX page_watch_page (page_id),
    CONSTRAINT page_watch_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    CONSTRAINT page_watch_page FOREIGN KEY (page_id) REFERENCES page(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE space_watch (
    user_id INT NOT NULL,
    space_id INT NOT NULL,
    create_date DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, space_id),
    INDEX space_watch_space (space_id),
    CONSTRAINT space_watch_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    CONSTRAINT space_watch_space FOREIGN KEY (space_id) REFERENCES space(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- How often a user wants mails: immediate, hourly, daily or never (the history stays).
CREATE TABLE notification_setting (
    user_id INT NOT NULL PRIMARY KEY,
    frequency VARCHAR(16) NOT NULL DEFAULT 'immediate',
    mentions BOOLEAN NOT NULL DEFAULT TRUE,
    last_digest DATETIME(3) NULL,
    change_date DATETIME(3) NOT NULL,
    CONSTRAINT notification_setting_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE notification (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    page_id INT NULL,
    page_name VARCHAR(255) NOT NULL,
    actor_id INT NULL,
    actor_login VARCHAR(255) NULL,
    detail VARCHAR(1024) NULL,
    create_date DATETIME(3) NOT NULL,
    read_date DATETIME(3) NULL,
    mailed BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX notification_user (user_id, create_date),
    INDEX notification_unmailed (mailed, user_id),
    CONSTRAINT notification_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    CONSTRAINT notification_page FOREIGN KEY (page_id) REFERENCES page(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE mail_queue (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    sender VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    create_date DATETIME(3) NOT NULL,
    next_attempt DATETIME(3) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1024) NULL,
    sent_date DATETIME(3) NULL,
    INDEX mail_queue_due (sent_date, next_attempt),
    CONSTRAINT mail_queue_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
