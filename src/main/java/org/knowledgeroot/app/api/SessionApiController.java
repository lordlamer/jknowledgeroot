package org.knowledgeroot.app.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.recovery.AccountRecoveryService;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.springframework.beans.factory.annotation.Value;
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
    private final ApiSupport api;
    private final DatabaseAuthentificationProvider authentication;
    private final UploadPolicy uploadPolicy;
    private final RecoverySettings recoverySettings;
    private final AccountRecoveryService recovery;
    private final SecurityContextRepository contextRepository = WebSecurityConfig.securityContextRepository();
    private final WebAuthenticationDetailsSource details = new WebAuthenticationDetailsSource();
    @Value("${knowledgeroot.pages.allow-guest-root-creation:false}")
    private boolean allowGuestRootCreation;
    @Value("${knowledgeroot.version:1.0.0-rc.2}")
    private String version;

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
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        new CsrfAuthenticationStrategy(WebSecurityConfig.csrfTokenRepository()).onAuthentication(result, request, response);
        return info(request);
    }

    @PostMapping("/auth/logout")
    ApiModels.SessionInfo logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        new CsrfLogoutHandler(WebSecurityConfig.csrfTokenRepository()).logout(request, response, current);
        new SecurityContextLogoutHandler().logout(request, response, current);
        return new ApiModels.SessionInfo(false, null, capabilities(false), null, version);
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
                csrf == null ? null : new ApiModels.Csrf(csrf.getHeaderName(), csrf.getToken()), version);
    }

    private ApiModels.Capabilities capabilities(boolean guest) {
        return new ApiModels.Capabilities(!guest || allowGuestRootCreation, recoverySettings.isEnabled(),
                uploadPolicy.maxFileBytes(), uploadPolicy.maxFiles());
    }
}
