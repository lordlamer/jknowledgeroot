package org.knowledgeroot.app.mcp.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Client "authentication" of a public client refreshing or revoking a token: the assistant only
 * names its client id. Spring Authorization Server authenticates public clients solely through
 * PKCE on the code exchange, so these two requests need this pair. The token itself is then
 * checked against the client by the grant, which refuses tokens issued to another client.
 */
final class McpPublicClientRefresh {
    private McpPublicClientRefresh() {}

    static AuthenticationConverter converter() {
        return request -> {
            if (!"POST".equals(request.getMethod()) || !refreshOrRevocation(request.getParameterMap())) return null;
            // Confidential clients authenticate with their secret through the standard converters.
            if (request.getHeader("Authorization") != null || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
                    || request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) return null;
            String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
            if (clientIds == null || clientIds.length != 1 || clientIds[0].isBlank()) return null;
            return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, parameters(request));
        };
    }

    static AuthenticationProvider provider(RegisteredClientRepository clients) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                var token = (OAuth2ClientAuthenticationToken) authentication;
                if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())
                        || !refreshOrRevocation(token.getAdditionalParameters())) return null;
                RegisteredClient client = clients.findByClientId((String) token.getPrincipal());
                if (client == null || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                        || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN))
                    throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Unknown client", null));
                return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    /** The refresh grant names its grant type; a revocation (RFC 7009) names the token and no grant type. */
    private static boolean refreshOrRevocation(Map<String, ?> parameters) {
        Object grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (grantType instanceof String[] values) grantType = values.length == 1 ? values[0] : null;
        if (grantType != null) return AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType);
        return parameters.containsKey(OAuth2ParameterNames.TOKEN);
    }

    private static Map<String, Object> parameters(HttpServletRequest request) {
        var parameters = new HashMap<String, Object>();
        request.getParameterMap().forEach((name, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(name)) parameters.put(name, values.length == 1 ? values[0] : values);
        });
        return parameters;
    }
}
