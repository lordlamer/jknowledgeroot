-- One row serializes bootstrap across application instances and records completion.
CREATE TABLE knowledgeroot_setup (
    id TINYINT NOT NULL PRIMARY KEY,
    initialized BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
INSERT INTO knowledgeroot_setup (id, initialized) VALUES (1, FALSE);
