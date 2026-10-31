package org.knowledgeroot.app.security.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Decides after a correct password whether a session needs the second factor, and moves it between the two states. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecondFactorGate {
    public enum Requirement {
        /** Password alone signs the user in. */
        NONE,
        /** A token is enrolled: ask for a code. */
        VERIFY,
        /** The policy requires a token this account does not have yet: enrol first. */
        ENROL
    }

    private final MfaSettings settings;
    private final SecondFactorService factors;
    private final UserContext userContext;
    // Stateless; interchangeable with the repository configured in WebSecurityConfig.
    private final SecurityContextRepository contextRepository = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

    public Requirement requirement(UserDetails user) {
        if (user == null || user.isGuest() || !settings.enforces()) return Requirement.NONE;
        if (factors.enrolled(Integer.parseInt(user.getUserId()))) return Requirement.VERIFY;
        return settings.requires(user) ? Requirement.ENROL : Requirement.NONE;
    }

    public static boolean pending(Authentication authentication) {
        return authentication instanceof SecondFactorPendingToken;
    }

    /** Replaces the password authentication with the pending state and stores it in the session. */
    public SecondFactorPendingToken storePending(UserDetails user, Requirement requirement,
                                                 HttpServletRequest request, HttpServletResponse response) {
        var pending = new SecondFactorPendingToken(user, requirement);
        store(pending, request, response);
        log.info("Second factor {} pending for user {}", requirement, user.getLogin());
        return pending;
    }

    /**
     * Completes the sign-in: re-reads the account, rotates the session ID and stores the full token.
     * @throws UserNotFoundException when the account was disabled or deleted meanwhile
     */
    public KnowledgerootUserToken promote(SecondFactorPendingToken pending, HttpServletRequest request,
                                          HttpServletResponse response) {
        UserDetails current = userContext.getUserContextForId(pending.getUser().getUserId());
        if (current.isGuest()) throw new UserNotFoundException("Account no longer available");
        if (request.getSession(false) != null) request.changeSessionId();
        var token = new KnowledgerootUserToken(current);
        store(token, request, response);
        log.info("Second factor accepted for user {}", current.getLogin());
        return token;
    }

    private void store(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
