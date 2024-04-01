package org.knowledgeroot.app.security.auth;

import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class KnowledgerootUserToken extends AbstractAuthenticationToken {
    private final UserDetails userDetails;

    public KnowledgerootUserToken(UserDetails userDetails, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userDetails = userDetails;
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
