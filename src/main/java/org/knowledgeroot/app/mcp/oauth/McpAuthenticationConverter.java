package org.knowledgeroot.app.mcp.oauth;

import org.knowledgeroot.app.mcp.McpUserToken;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;
import java.util.Objects;

/**
 * Turns a verified access token into the user it was issued to. The account is read again on
 * every request, so a disabled, deleted or renamed account and a changed password end the
 * assistant's access immediately, like they end a web session.
 */
class McpAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    static final String CREDENTIAL_CLAIM = "cred";
    private final UserContext users;

    McpAuthenticationConverter(UserContext users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserDetails user;
        try {
            user = users.getUserContextForId(jwt.getSubject());
        } catch (UserNotFoundException | NumberFormatException e) {
            throw new InvalidBearerTokenException("The account is no longer available");
        }
        if (user.isGuest()) throw new InvalidBearerTokenException("The account is no longer available");
        if (!Objects.equals(user.getCredentialTag(), jwt.getClaimAsString(CREDENTIAL_CLAIM)))
            throw new InvalidBearerTokenException("The password changed; connect the assistant again");
        return new McpUserToken(user, scopes(jwt), jwt);
    }

    static List<String> scopes(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        if (scope instanceof String text && !text.isBlank()) return List.of(text.trim().split("\\s+"));
        return List.of();
    }
}
