-- OAuth 2.1 authorization server behind the MCP server for AI assistants (R52).
-- Tables follow the Spring Authorization Server JDBC schema with MariaDB types.

-- Clients that registered dynamically (RFC 7591), one per assistant installation.
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    client_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_id_issued_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    client_secret VARCHAR(200) NULL,
    client_secret_expires_at DATETIME(3) NULL,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000) NULL,
    post_logout_redirect_uris VARCHAR(1000) NULL,
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    UNIQUE KEY uk_oauth2_registered_client_client_id (client_id),
    KEY ix_oauth2_registered_client_issued (client_id_issued_at)
) ENGINE=InnoDB;

-- Authorization codes, access and refresh tokens per user and client; principal_name is the user id.
-- Token and attribute columns are TEXT: the JDBC service writes them as strings, which MariaDB does not accept for BLOB.
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    registered_client_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000) NULL,
    attributes TEXT NULL,
    state VARCHAR(500) NULL,
    authorization_code_value TEXT NULL,
    authorization_code_issued_at DATETIME(3) NULL,
    authorization_code_expires_at DATETIME(3) NULL,
    authorization_code_metadata TEXT NULL,
    access_token_value TEXT NULL,
    access_token_issued_at DATETIME(3) NULL,
    access_token_expires_at DATETIME(3) NULL,
    access_token_metadata TEXT NULL,
    access_token_type VARCHAR(100) NULL,
    access_token_scopes VARCHAR(1000) NULL,
    oidc_id_token_value TEXT NULL,
    oidc_id_token_issued_at DATETIME(3) NULL,
    oidc_id_token_expires_at DATETIME(3) NULL,
    oidc_id_token_metadata TEXT NULL,
    refresh_token_value TEXT NULL,
    refresh_token_issued_at DATETIME(3) NULL,
    refresh_token_expires_at DATETIME(3) NULL,
    refresh_token_metadata TEXT NULL,
    user_code_value TEXT NULL,
    user_code_issued_at DATETIME(3) NULL,
    user_code_expires_at DATETIME(3) NULL,
    user_code_metadata TEXT NULL,
    device_code_value TEXT NULL,
    device_code_issued_at DATETIME(3) NULL,
    device_code_expires_at DATETIME(3) NULL,
    device_code_metadata TEXT NULL,
    KEY ix_oauth2_authorization_principal (principal_name, registered_client_id),
    KEY ix_oauth2_authorization_state (state(128)),
    KEY ix_oauth2_authorization_code (authorization_code_value(128)),
    KEY ix_oauth2_authorization_refresh (refresh_token_value(128)),
    KEY ix_oauth2_authorization_refresh_expiry (refresh_token_expires_at)
) ENGINE=InnoDB;

-- Scopes a user granted to a client on the consent page.
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB;

-- RSA keys that sign access tokens, shared by all instances; the private key is encrypted with the master key.
CREATE TABLE oauth2_signing_key (
    id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    private_key_enc BLOB NOT NULL,
    public_key TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
