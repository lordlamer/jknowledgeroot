-- Sign-in through an OpenID Connect provider (R64): which external identity (issuer and subject)
-- belongs to which account, the groups the provider named at the last sign-in (so memberships it
-- withdraws are removed again), and whether an account may still sign in with its local password.
-- Accounts created from a sign-in start without local login; administrators may turn it on.
ALTER TABLE `user` ADD COLUMN local_login BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE user_identity (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    groups TEXT NULL,
    create_date DATETIME(3) NOT NULL,
    last_login DATETIME(3) NOT NULL,
    UNIQUE INDEX user_identity_subject (issuer, subject),
    INDEX user_identity_user (user_id),
    CONSTRAINT user_identity_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB;
