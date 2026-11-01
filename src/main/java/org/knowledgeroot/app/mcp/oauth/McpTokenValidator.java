package org.knowledgeroot.app.mcp.oauth;

import org.knowledgeroot.app.mcp.McpUrls;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Binds access tokens to this server: the issuer must be the origin the request arrived at and
 * the audience must name its {@code /mcp} endpoint, so a token issued for another host or
 * another resource is refused even though the signature is ours.
 */
class McpTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final McpUrls urls;

    McpTokenValidator(McpUrls urls) {
        this.urls = urls;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String origin = urls.currentOrigin().orElse(null);
        if (origin == null) return failure("The request origin is unknown");
        if (token.getIssuer() == null || !origin.equals(token.getIssuer().toString())) return failure("The token was issued for another server");
        if (token.getAudience() == null || !token.getAudience().contains(origin + McpUrls.MCP_PATH)) return failure("The token is not meant for the MCP server");
        if (token.getClaimAsString("cred") == null) return failure("The token lacks the credential marker");
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
