-- Audit of the MCP server (R54): one row per tool, resource or prompt request of an assistant.
-- Parameters are a short summary without page contents; the detail is the text the assistant got.
CREATE TABLE mcp_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    user_id INT NULL,
    login VARCHAR(255) NULL,
    client_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(64) NOT NULL,
    parameters VARCHAR(1000) NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    detail VARCHAR(255) NULL,
    duration_ms INT NOT NULL,
    remote_addr VARCHAR(64) NULL,
    KEY ix_mcp_audit_created (created_at),
    KEY ix_mcp_audit_user (user_id, created_at)
) ENGINE=InnoDB;
