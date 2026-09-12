package org.knowledgeroot.app.security.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.Locale;

/**
 * Database authentication provider
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseAuthentificationProvider implements AuthenticationProvider {
    private final CredentialRepository credentials;
    private final UserContext userContext;
    private final PasswordService passwords;
    private final LoginAttemptLimiter limiter;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String login = authentication.getName().toLowerCase(Locale.ROOT);
        if (login.length() > 255 || authentication.getCredentials() == null
                || authentication.getCredentials().toString().length() > 1024) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String password = authentication.getCredentials().toString();
        try {
            String source = authentication.getDetails() instanceof WebAuthenticationDetails details
                    ? details.getRemoteAddress() : "unknown";
            limiter.requireAttempt(login, source);
            var credential = credentials.findByLogin(login).orElse(null);
            if (!passwords.matches(password, credential == null ? null : credential.hash())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            String currentHash = credential.hash();
            if (passwords.upgradeEncoding(currentHash)) {
                String replacement = passwords.encode(password);
                if (!credentials.replaceHash(credential.userId(), currentHash, replacement)) {
                    throw new BadCredentialsException("Invalid username or password");
                }
                currentHash = replacement;
            }
            UserDetails user = userContext.getUserContextForId(credential.userId());
            if (user.isGuest() || !credentials.isCurrent(credential.userId(), currentHash)) {
                throw new BadCredentialsException("Invalid username or password");
            }
            log.info("Login success for user: {}", login);
            return new KnowledgerootUserToken(user);
        } catch (UserNotFoundException e) {
            throw new BadCredentialsException("Invalid username or password");
        } catch (DataAccessException e) {
            throw new AuthenticationServiceException("Login temporarily unavailable");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
