package org.knowledgeroot.app.mcp.oauth;

import org.knowledgeroot.app.mcp.McpSettings;
import org.knowledgeroot.app.mcp.McpUserToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2ClientRegistration;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dynamic client registration (RFC 7591) for assistants: anybody may register, but only as a
 * public client with PKCE whose redirect URIs point to an allow-listed host or the loopback
 * interface, and every client gets the same scopes, consent requirement and token lifetimes.
 */
final class McpClientRegistration {
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(7);
    static final Duration AUTHORIZATION_CODE_LIFETIME = Duration.ofMinutes(5);

    private McpClientRegistration() {}

    static Consumer<OAuth2ClientRegistrationAuthenticationContext> validator(McpSettings settings) {
        var standard = new OAuth2ClientRegistrationAuthenticationValidator();
        return context -> {
            standard.accept(context);
            OAuth2ClientRegistrationAuthenticationToken token = context.getAuthentication();
            OAuth2ClientRegistration registration = token.getClientRegistration();
            List<String> redirectUris = registration.getRedirectUris();
            if (redirectUris == null || redirectUris.isEmpty())
                throw error("invalid_redirect_uri", "At least one redirect_uri is required.");
            for (String uri : redirectUris) {
                if (!settings.allowsRedirect(uri))
                    throw error("invalid_redirect_uri", "This server only redirects to its allow-listed hosts or to localhost.");
            }
            String method = registration.getTokenEndpointAuthenticationMethod();
            if (method != null && !ClientAuthenticationMethod.NONE.getValue().equals(method))
                throw error("invalid_client_metadata", "Only public clients (token_endpoint_auth_method \"none\") are accepted.");
        };
    }

    static Converter<OAuth2ClientRegistration, RegisteredClient> registeredClientConverter() {
        var standard = new OAuth2ClientRegistrationRegisteredClientConverter();
        return registration -> {
            RegisteredClient base = standard.convert(registration);
            if (base == null) return null;
            return RegisteredClient.from(base)
                    .clientSecret(null)
                    .clientAuthenticationMethods(methods -> {
                        methods.clear();
                        methods.add(ClientAuthenticationMethod.NONE);
                    })
                    .authorizationGrantTypes(grants -> {
                        grants.clear();
                        grants.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                        grants.add(AuthorizationGrantType.REFRESH_TOKEN);
                    })
                    .scopes(scopes -> {
                        scopes.clear();
                        scopes.add(McpUserToken.SCOPE_READ);
                        scopes.add(McpUserToken.SCOPE_WRITE);
                    })
                    .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                            .refreshTokenTimeToLive(REFRESH_TOKEN_LIFETIME)
                            .authorizationCodeTimeToLive(AUTHORIZATION_CODE_LIFETIME)
                            .reuseRefreshTokens(false)
                            .build())
                    .build();
        };
    }

    private static OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, "https://datatracker.ietf.org/doc/html/rfc7591#section-3.2.2"));
    }
}
