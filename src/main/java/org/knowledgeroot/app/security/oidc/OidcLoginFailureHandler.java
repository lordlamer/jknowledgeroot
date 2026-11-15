package org.knowledgeroot.app.security.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A failed OpenID Connect sign-in (R64) goes back to the login page with a neutral message; the
 * provider's error code and description are logged, because that is where an operator finds out
 * about a wrong client secret, a redirect URI the provider does not know or a clock that is off.
 */
@Component
@Slf4j
public class OidcLoginFailureHandler implements AuthenticationFailureHandler {
    static final String FAILURE_URL = "/login?error=oidc";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            var error = oauth.getError();
            log.warn("OpenID Connect sign-in failed: {} {}", error.getErrorCode(),
                    error.getDescription() == null ? "" : error.getDescription());
        } else {
            log.warn("OpenID Connect sign-in failed: {}", exception.toString());
        }

        response.sendRedirect(request.getContextPath() + FAILURE_URL);
    }
}
