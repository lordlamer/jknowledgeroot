package org.knowledgeroot.app.mcp.oauth;

import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Refresh tokens for the assistants, which are public clients: the default generator withholds
 * refresh tokens from public clients, whereas OAuth 2.1 allows them together with PKCE and
 * rotation. The lifetime comes from the client's token settings; the value is opaque.
 */
final class McpRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
    private final SecureRandom random = new SecureRandom();

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) return null;
        byte[] bytes = new byte[96];
        random.nextBytes(bytes);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), issuedAt, expiresAt);
    }
}
