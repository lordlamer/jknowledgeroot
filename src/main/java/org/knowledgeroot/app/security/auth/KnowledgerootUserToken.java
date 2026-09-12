package org.knowledgeroot.app.security.auth;

import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public class KnowledgerootUserToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1951804365109885835L;

    private final UserDetails userDetails;

    public KnowledgerootUserToken(UserDetails userDetails) {
        super(authoritiesFor(userDetails));
        this.userDetails = userDetails;
        super.setAuthenticated(true);
    }

    private static Collection<? extends GrantedAuthority> authoritiesFor(UserDetails user) {
        if (user.isAdmin()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (user.isUser()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        throw new IllegalArgumentException("Guest users cannot have an authenticated token");
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("Create a new token after validating the user");
        }
        super.setAuthenticated(false);
    }

    @Override
    public Object getPrincipal() {
        return userDetails.getLogin();
    }

    @Override
    public String getName() {
        return userDetails.getLogin();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return userDetails;
    }
}
