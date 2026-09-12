package org.knowledgeroot.app.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

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
        String login = authentication.getName().toLowerCase(Locale.ROOT);
        if (authentication.getCredentials() == null) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String password = authentication.getCredentials().toString();
        try {
            String dbPassword = jdbcTemplate.queryForObject(AUTH_SQL, String.class, login);
            if (!matchesPassword(password, dbPassword)) {
                throw new BadCredentialsException("Invalid username or password");
            }
            UserDetails user = userContext.getUserContextForLogin(login);
            if (user.isGuest()) {
                throw new BadCredentialsException("Invalid username or password");
            }
            log.info("Login success for user: {}", login);
            return new KnowledgerootUserToken(user);
        } catch (EmptyResultDataAccessException | UserNotFoundException e) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    private boolean matchesPassword(String password, String hash) {
        if (hash == null) {
            return false;
        }
        try {
            return PasswordHasher.verify(password, hash);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            // A malformed legacy hash must fail authentication, not fail the request.
            return false;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
