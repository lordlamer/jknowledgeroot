-- Runtime settings administrators change in the application (first use: the second-factor policy).
CREATE TABLE app_setting (
    name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    value VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci NOT NULL,
    changed_by INT NULL,
    change_date DATETIME(3) NOT NULL
) ENGINE=InnoDB;

-- One TOTP factor per user; the secret is AES-GCM encrypted with the master key,
-- last_step blocks reuse of an accepted time step.
CREATE TABLE user_second_factor (
    user_id INT NOT NULL PRIMARY KEY,
    secret_enc VARBINARY(255) NOT NULL,
    last_step BIGINT NOT NULL,
    enrolled_at DATETIME(3) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Recovery codes as keyed hashes; a code is spent once used_at is set.
CREATE TABLE user_recovery_code (
    user_id INT NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    used_at DATETIME(3) NULL,
    PRIMARY KEY (user_id, code_hash),
    FOREIGN KEY (user_id) REFERENCES user_second_factor(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;
