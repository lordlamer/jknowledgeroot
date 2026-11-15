package org.knowledgeroot.app.security.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * After the provider confirmed the user (R64): the session carries the same {@link KnowledgerootUserToken}
 * a password sign-in produces, so every filter, controller and the session validation treat both the
 * same. The second factor is the provider's business; the sign-in continues at {@code /login/success},
 * which also resumes an assistant's OAuth flow that sent the browser to the login page.
 */
@Component
@Slf4j
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {
    static final String SUCCESS_URL = "/login/success";
    private final SecurityContextRepository contextRepository = WebSecurityConfig.securityContextRepository();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof LinkedOidcUserService.LinkedOidcUser user)) {
            throw new IllegalStateException("OpenID Connect sign-in without a linked account");
        }
        var token = new KnowledgerootUserToken(user.getAccount());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        log.info("Login success through OpenID Connect for user: {}", user.getAccount().getLogin());
        response.sendRedirect(request.getContextPath() + SUCCESS_URL);
    }

}
