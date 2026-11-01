package org.knowledgeroot.app.mcp.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Wraps the converters of the authorization endpoint so that the authorization is stored with a
 * plain principal: the user id as name and the roles as authorities. The session token of the web
 * UI carries the whole account and is not meant to be serialised into the authorization table;
 * the user id also survives a renamed login.
 */
class McpPrincipalConverter implements AuthenticationConverter {
    private final AuthenticationConverter delegate;

    McpPrincipalConverter(AuthenticationConverter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        Authentication result = delegate.convert(request);
        if (result instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token
                && token.getPrincipal() instanceof Authentication principal) {
            return new OAuth2AuthorizationCodeRequestAuthenticationToken(token.getAuthorizationUri(), token.getClientId(),
                    stable(principal), token.getRedirectUri(), token.getState(), token.getScopes(), token.getAdditionalParameters());
        }
        if (result instanceof OAuth2AuthorizationConsentAuthenticationToken token
                && token.getPrincipal() instanceof Authentication principal) {
            return new OAuth2AuthorizationConsentAuthenticationToken(token.getAuthorizationUri(), token.getClientId(),
                    stable(principal), token.getState(), token.getScopes(), token.getAdditionalParameters());
        }
        return result;
    }

    static Authentication stable(Authentication authentication) {
        if (authentication instanceof KnowledgerootUserToken token && token.isAuthenticated()
                && token.getDetails() instanceof UserDetails user && !user.isGuest()) {
            return UsernamePasswordAuthenticationToken.authenticated(user.getUserId(), null, token.getAuthorities());
        }
        return authentication;
    }
}
