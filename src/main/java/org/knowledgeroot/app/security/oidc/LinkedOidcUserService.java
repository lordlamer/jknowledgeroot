package org.knowledgeroot.app.security.oidc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Loads the OpenID Connect user as Spring Security does and attaches the Knowledgeroot account it
 * maps to (R64). A sign-in the mapping refuses fails like a wrong password, with the reason logged.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LinkedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final OidcAccountService accounts;

    /** The provider's user together with the local account. */
    @Getter
    public static class LinkedOidcUser extends DefaultOidcUser {
        private final transient UserDetails account;

        LinkedOidcUser(OidcUser user, UserDetails account) {
            super(user.getAuthorities(), user.getIdToken(), user.getUserInfo());
            this.account = account;
        }
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(request);
        try {
            return new LinkedOidcUser(user, accounts.signIn(user));
        } catch (OidcAccountService.Rejected ex) {
            log.warn("OpenID Connect sign-in of subject {} refused ({}): {}", user.getSubject(), ex.getCode(), ex.getMessage());
            throw new OAuth2AuthenticationException(new OAuth2Error("account_" + ex.getCode(), ex.getMessage(), null), ex);
        }
    }
}
