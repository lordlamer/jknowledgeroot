package org.knowledgeroot.app.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.recovery.AccountRecoveryService;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.knowledgeroot.app.security.mfa.MfaSettings;
import org.knowledgeroot.app.security.mfa.SecondFactorGate;
import org.knowledgeroot.app.security.mfa.SecondFactorPendingToken;
import org.knowledgeroot.app.security.mfa.SecondFactorService;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Session lifecycle for app clients: CSRF bootstrap, login, logout and password-reset requests. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SessionApiController {
    static final String MFA_FAILURES = "knowledgeroot.mfa.apiFailures";
    static final int MAX_MFA_FAILURES = 5;
    private final ApiSupport api;
    private final DatabaseAuthentificationProvider authentication;
    private final UploadPolicy uploadPolicy;
    private final RecoverySettings recoverySettings;
    private final AccountRecoveryService recovery;
    private final SecondFactorGate gate;
    private final SecondFactorService factors;
    private final MfaSettings mfaSettings;
    /** Maven artifact version from the generated build-info; absent only in IDE runs without the build step. */
    private final ObjectProvider<BuildProperties> build;
    private final SecurityContextRepository contextRepository = WebSecurityConfig.securityContextRepository();
    private final WebAuthenticationDetailsSource details = new WebAuthenticationDetailsSource();
    private final org.knowledgeroot.app.space.SpaceService spaces;

    /** Current identity plus a CSRF token for the following state-changing requests. */
    @GetMapping("/session")
    ApiModels.SessionInfo session(HttpServletRequest request) {
        return info(request);
    }

    @PostMapping("/auth/login")
    ApiModels.SessionInfo login(@RequestBody ApiModels.LoginRequest body, HttpServletRequest request,
                                HttpServletResponse response) {
        if (body == null || body.username() == null || body.password() == null || body.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter user name and password.");
        }
        var token = UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password());
        token.setDetails(details.buildDetails(request));
        Authentication result;
        try {
            result = authentication.authenticate(token);
        } catch (AuthenticationServiceException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in is temporarily unavailable.");
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user name or password.");
        }
        // Same protections as form login: a fresh session ID and a rotated CSRF token.
        if (request.getSession(false) != null) request.changeSessionId();
        else request.getSession(true);
        UserDetails user = (UserDetails) result.getDetails();
        var requirement = gate.requirement(user);
        if (requirement != SecondFactorGate.Requirement.NONE) {
            // The password was right, but the session owes the second factor: see POST /auth/mfa.
            gate.storePending(user, requirement, request, response);
        } else {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(result);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
        }
        new CsrfAuthenticationStrategy(WebSecurityConfig.csrfTokenRepository()).onAuthentication(result, request, response);
        return info(request);
    }

    /** Second step after a login that answered {@code mfa.pending}: an authenticator or recovery code. */
    @PostMapping("/auth/mfa")
    ApiModels.SessionInfo secondFactor(@RequestBody ApiModels.CodeRequest body, HttpServletRequest request,
                                       HttpServletResponse response) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof SecondFactorPendingToken pending))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No second factor is pending.");
        if (pending.getRequirement() != SecondFactorGate.Requirement.VERIFY)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set up the second factor in the web application first.");
        var result = factors.verify(pending.getUserId(), body == null ? null : body.code());
        if (result == SecondFactorService.Result.INVALID) {
            int failures = (request.getSession().getAttribute(MFA_FAILURES) instanceof Integer n ? n : 0) + 1;
            if (failures >= MAX_MFA_FAILURES) {
                new SecurityContextLogoutHandler().logout(request, response, pending);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Too many wrong codes. Sign in again.");
            }
            request.getSession().setAttribute(MFA_FAILURES, failures);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The code is not valid.");
        }
        Authentication full;
        try {
            full = gate.promote(pending, request, response);
        } catch (UserNotFoundException e) {
            new SecurityContextLogoutHandler().logout(request, response, pending);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user name or password.");
        }
        request.getSession().removeAttribute(MFA_FAILURES);
        new CsrfAuthenticationStrategy(WebSecurityConfig.csrfTokenRepository()).onAuthentication(full, request, response);
        return info(request);
    }

    @PostMapping("/auth/logout")
    ApiModels.SessionInfo logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        new CsrfLogoutHandler(WebSecurityConfig.csrfTokenRepository()).logout(request, response, current);
        new SecurityContextLogoutHandler().logout(request, response, current);
        return new ApiModels.SessionInfo(false, null, capabilities(false), null, version(),
                new ApiModels.SecondFactor(false, null, false, mfaSettings.policy().getValue()));
    }

    /** Always answers 204 so the response does not reveal whether an address is known. */
    @PostMapping("/auth/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgotPassword(@RequestBody ApiModels.EmailRequest body, HttpServletRequest request) {
        recovery.requestReset(body == null || body.email() == null ? "" : body.email(), request.getRemoteAddr());
    }

    private ApiModels.SessionInfo info(HttpServletRequest request) {
        var user = api.user();
        boolean guest = user.isGuest();
        ApiModels.CurrentUser current = guest ? null : new ApiModels.CurrentUser(Integer.parseInt(user.getUserId()),
                user.getLogin(), user.getFirstName(), user.getSurName(), user.getEmail(), user.isAdmin());
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new ApiModels.SessionInfo(!guest, current, capabilities(guest),
                csrf == null ? null : new ApiModels.Csrf(csrf.getHeaderName(), csrf.getToken()), version(), secondFactor(guest, user));
    }

    private ApiModels.SecondFactor secondFactor(boolean guest, UserDetails user) {
        String policy = mfaSettings.policy().getValue();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof SecondFactorPendingToken pending)
            return new ApiModels.SecondFactor(true, pending.getRequirement().name().toLowerCase(java.util.Locale.ROOT),
                    pending.getRequirement() == SecondFactorGate.Requirement.VERIFY, policy);
        return new ApiModels.SecondFactor(false, null, !guest && factors.enrolled(Integer.parseInt(user.getUserId())), policy);
    }

    private String version() {
        BuildProperties properties = build.getIfAvailable();
        return properties == null ? "unknown" : properties.getVersion();
    }

    private ApiModels.Capabilities capabilities(boolean guest) {
        return new ApiModels.Capabilities(spaces.canCreateRootPage(spaces.defaultSpace().getId()), recoverySettings.isEnabled(),
                uploadPolicy.maxFileBytes(), uploadPolicy.maxFiles());
    }
}
