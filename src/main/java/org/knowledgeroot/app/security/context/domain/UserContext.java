package org.knowledgeroot.app.security.context.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserContext {
    private final UserContextDao userContextDao;

    public UserDetails getUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // if the user is not authenticated, return guest context
        if(authentication instanceof AnonymousAuthenticationToken) {
            return getGuestContext();
        }

        // if the user is authenticated with token, return user context
        if(authentication instanceof KnowledgerootUserToken) {
            return (UserDetails) authentication.getDetails();
        }

        // fallback to guest context
        return getGuestContext();
    }

    /**
     * Get user context for login
     *
     * @param name
     * @return
     */
    public UserDetails getUserContextForLogin(String name) {
        return userContextDao.getUserDetails(name);
    }

    /**
     * Get guest context
     *
     * @return
     */
    private UserDetails getGuestContext() {
        return UserDetails.builder()
                .userId("guest")
                .login("guest")
                .email("")
                .firstName("Guest")
                .surName("")
                .role(UserDetails.Role.GUEST)
                .language("en")
                .build();
    }
}
