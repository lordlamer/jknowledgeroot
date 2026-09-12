CREATE TABLE login_attempt (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    window_start BIGINT NOT NULL,
    attempts INT NOT NULL,
    INDEX idx_login_attempt_expiry (window_start)
) ENGINE=InnoDB;
