package org.knowledgeroot.app.security.mfa;

import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.List;

/**
 * Session state between a correct password and the second factor. It carries no role, so the
 * domain treats the request as a guest, and {@link SecondFactorGateFilter} keeps it on the MFA pages.
 */
public class SecondFactorPendingToken extends AbstractAuthenticationToken {
    public static final String AUTHORITY = "MFA_PENDING";
    @Serial
    private static final long serialVersionUID = 1L;
    private final UserDetails user;
    private final SecondFactorGate.Requirement requirement;

    public SecondFactorPendingToken(UserDetails user, SecondFactorGate.Requirement requirement) {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        if (requirement == SecondFactorGate.Requirement.NONE) throw new IllegalArgumentException("Nothing pending");
        this.user = user;
        this.requirement = requirement;
        super.setAuthenticated(true);
    }

    public UserDetails getUser() {
        return user;
    }

    public int getUserId() {
        return Integer.parseInt(user.getUserId());
    }

    public SecondFactorGate.Requirement getRequirement() {
        return requirement;
    }

    @Override
    public Object getPrincipal() {
        return user.getLogin();
    }

    @Override
    public String getName() {
        return user.getLogin();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return user;
    }
}
