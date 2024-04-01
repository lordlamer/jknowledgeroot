package org.knowledgeroot.app.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Database authentication provider
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseAuthentificationProvider implements AuthenticationProvider {
    private final JdbcTemplate jdbcTemplate;
    private final UserContext userContext;

    private static final String AUTH_SQL = "select `password` from `user` WHERE BINARY lower(`login`)=? AND active=1 AND deleted=0";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication userAuth = null;

        String login = authentication.getName();
        String password = authentication.getCredentials().toString();

        String dbPassword = "";
        try {
            dbPassword = jdbcTemplate.queryForObject(AUTH_SQL, new Object[]{login.toLowerCase()}, String.class);
        } catch(EmptyResultDataAccessException e) {
            log.debug("Could not find user in database: {}", login);
        }

        // check login
        // check plain text password with database password
        if (PasswordHasher.verify(password, dbPassword)) {
            log.info("Login success for user: {}", login);

            // get user details
            UserDetails userDetails = userContext.getUserContextForLogin(login);

            // build spring auth object for session
            List<GrantedAuthority> grantedAuths = new ArrayList<>();

            // add admin role if user is admin
            if(userDetails.isAdmin()) {
                grantedAuths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            // add user role if user is user or admin
            if(userDetails.isUser() || userDetails.isAdmin()) {
                grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
            }

            // create auth object
            userAuth = new KnowledgerootUserToken(userDetails, grantedAuths);
        } else {
            log.info("Login failed for user: {}", login);
        }

        return userAuth;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
