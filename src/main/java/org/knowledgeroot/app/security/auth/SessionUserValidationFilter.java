package org.knowledgeroot.app.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** Revalidate session accounts before applying request authorization. */
public class SessionUserValidationFilter extends OncePerRequestFilter {
    private final UserContext userContext;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionUserValidationFilter(UserContext userContext, SecurityContextRepository contextRepository) {
        this.userContext = userContext;
        this.contextRepository = contextRepository;
        logoutHandler.setSecurityContextRepository(contextRepository);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof KnowledgerootUserToken token) {
            UserDetails previous = (UserDetails) token.getDetails();
            UserDetails current = null;
            if (token.isAuthenticated()) {
                try {
                    current = userContext.getUserContextForId(previous.getUserId());
                } catch (UserNotFoundException e) {
                    // Disabled and deleted accounts are excluded by the account lookup.
                }
            }

            if (current == null || !previous.getUserId().equals(current.getUserId())
                    || current.isGuest() || previous.getRole() != current.getRole()
                    || !Objects.equals(previous.getCredentialTag(), current.getCredentialTag())) {
                logoutHandler.logout(request, response, authentication);
            } else {
                // Do not mutate a context shared with concurrent requests in the same session.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new KnowledgerootUserToken(current));
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
