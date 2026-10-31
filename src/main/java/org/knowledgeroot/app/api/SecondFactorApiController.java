package org.knowledgeroot.app.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.auth.CredentialRepository;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.knowledgeroot.app.security.mfa.MfaSettings;
import org.knowledgeroot.app.security.mfa.SecondFactorGate;
import org.knowledgeroot.app.security.mfa.SecondFactorPendingToken;
import org.knowledgeroot.app.security.mfa.SecondFactorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enrolment and management of the second factor for app clients. Enrolment works both for a
 * session that owes it after the password ({@code requirement: "enrol"}) and for a signed-in
 * user without a token; a pending session becomes fully signed in once the first code confirms.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SecondFactorApiController {
    static final String PENDING_SECRET = "knowledgeroot.mfa.apiPendingSecret";
    private final ApiSupport api;
    private final SecondFactorService factors;
    private final MfaSettings settings;
    private final SecondFactorGate gate;
    private final CredentialRepository credentials;
    private final PasswordService passwords;

    @GetMapping("/profile/mfa")
    ApiModels.SecondFactorStatus status() {
        int id = api.requireUser();
        var status = factors.status(id);
        return new ApiModels.SecondFactorStatus(status.enrolled(), status.enrolledAt(), status.recoveryCodesLeft(),
                settings.requires(api.user()), settings.policy().getValue());
    }

    /** Starts an enrolment; the secret stays in the session until {@link #confirm} sees the first code. */
    @PostMapping("/auth/mfa/setup")
    ApiModels.SecondFactorEnrolment setup(HttpServletRequest request) {
        UserDetails user = subject();
        if (factors.enrolled(Integer.parseInt(user.getUserId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A second factor is already set up.");
        var enrolment = factors.begin(user.getLogin());
        request.getSession().setAttribute(PENDING_SECRET, enrolment.secret());
        return new ApiModels.SecondFactorEnrolment(enrolment.secret(), enrolment.otpauthUri(), enrolment.qrSvg());
    }

    /** Confirms the enrolment with the first code and returns the recovery codes exactly once. */
    @PostMapping("/auth/mfa/confirm")
    ApiModels.SecondFactorConfirmed confirm(@RequestBody ApiModels.CodeRequest body, HttpServletRequest request,
                                            HttpServletResponse response) {
        UserDetails user = subject();
        String secret = (String) request.getSession().getAttribute(PENDING_SECRET);
        if (secret == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start the setup first.");
        var codes = factors.confirm(Integer.parseInt(user.getUserId()), secret, body == null ? null : body.code());
        request.getSession().removeAttribute(PENDING_SECRET);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof SecondFactorPendingToken pending) {
            try {
                authentication = gate.promote(pending, request, response);
            } catch (UserNotFoundException e) {
                new SecurityContextLogoutHandler().logout(request, response, pending);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user name or password.");
            }
            // The CSRF token rotates with the completed sign-in; fetch /api/v1/session for the new one.
            new CsrfAuthenticationStrategy(WebSecurityConfig.csrfTokenRepository()).onAuthentication(authentication, request, response);
        }
        return new ApiModels.SecondFactorConfirmed(codes, !(authentication instanceof SecondFactorPendingToken));
    }

    /** Removal needs the password and a current code and is refused while the policy requires a token. */
    @DeleteMapping("/profile/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@RequestBody ApiModels.SecondFactorRemoval body) {
        int id = api.requireUser();
        UserDetails user = api.user();
        if (settings.requires(user))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The policy requires a second factor for your account. Ask an administrator to reset it.");
        if (!factors.enrolled(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No second factor is set up.");
        boolean password = body != null && credentials.findById(user.getUserId())
                .map(c -> passwords.matches(body.currentPassword(), c.hash())).orElse(false);
        if (!password || factors.verify(id, body.code()) == SecondFactorService.Result.INVALID)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password or code is not valid.");
        factors.remove(id);
    }

    /** The account that enrols: the pending sign-in, or the signed-in user. */
    private UserDetails subject() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof SecondFactorPendingToken pending) {
            if (pending.getRequirement() != SecondFactorGate.Requirement.ENROL)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A second factor is already set up.");
            return pending.getUser();
        }
        api.requireUser();
        return api.user();
    }
}
