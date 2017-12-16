package org.knowledgeroot.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseAuthentificationProvider implements AuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseAuthentificationProvider.class);

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication userAuth = null;

        String login = authentication.getName();
        String password = authentication.getCredentials().toString();

        // check if customer was found and if password hash matches
        if(login.equals("admin") && password.equals("admin")) {
            logger.info("Login success for user: " + login);

            // build spring auth object for session
            List<GrantedAuthority> grantedAuths = new ArrayList<>();
            grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
            userAuth = new UsernamePasswordAuthenticationToken(login, password, grantedAuths);
        } else {
            logger.info("Login failed for user: " + login);
        }

        return userAuth;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
