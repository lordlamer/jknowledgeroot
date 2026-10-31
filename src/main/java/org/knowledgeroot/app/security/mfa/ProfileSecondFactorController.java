package org.knowledgeroot.app.security.mfa;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.auth.CredentialRepository;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Enrolment, status and removal of the signed-in user's own second factor. */
@Controller
@RequiredArgsConstructor
class ProfileSecondFactorController {
    static final String PENDING_SECRET = "knowledgeroot.mfa.profilePendingSecret";
    private final UserContext userContext;
    private final SecondFactorService factors;
    private final MfaSettings settings;
    private final CredentialRepository credentials;
    private final PasswordService passwords;

    @GetMapping("/profile/mfa")
    String show(@RequestParam(required = false) String removed, Model model, HtmxRequest htmx) {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        status(user, model);
        model.addAttribute("removed", removed != null);
        return htmx.isHtmxRequest() ? "profile/mfa :: body" : "profile/mfa";
    }

    @PostMapping("/profile/mfa/setup")
    String setup(HttpSession session, Model model, HtmxRequest htmx) {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        if (factors.enrolled(id(user))) return "redirect:/profile/mfa";
        var enrolment = factors.begin(user.getLogin());
        session.setAttribute(PENDING_SECRET, enrolment.secret());
        return setupView(enrolment, null, model, htmx);
    }

    @PostMapping("/profile/mfa/confirm")
    String confirm(@RequestParam String code, HttpSession session, Model model, HtmxRequest htmx) {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) return "redirect:/profile/mfa";
        try {
            var codes = factors.confirm(id(user), secret, code);
            session.removeAttribute(PENDING_SECRET);
            model.addAttribute("recoveryCodes", codes);
            return htmx.isHtmxRequest() ? "profile/mfa-codes :: body" : "profile/mfa-codes";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 409) {
                session.removeAttribute(PENDING_SECRET);
                return "redirect:/profile/mfa";
            }
            return setupView(factors.enrolment(user.getLogin(), secret), e.getReason(), model, htmx);
        }
    }

    /** Removal needs the password and a current code, and is refused while the policy requires a token. */
    @PostMapping("/profile/mfa/remove")
    String remove(@RequestParam String currentPassword, @RequestParam String code, Model model, HtmxRequest htmx) {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        String error = null;
        if (settings.requires(user)) error = "The policy requires a second factor for your account. Ask an administrator to reset it.";
        else if (!factors.enrolled(id(user))) return "redirect:/profile/mfa";
        else if (!credentials.findById(user.getUserId()).map(c -> passwords.matches(currentPassword, c.hash())).orElse(false)
                || factors.verify(id(user), code) == SecondFactorService.Result.INVALID)
            error = "Password or code is not valid.";
        if (error != null) {
            status(user, model);
            model.addAttribute("removeError", error);
            return htmx.isHtmxRequest() ? "profile/mfa :: body" : "profile/mfa";
        }
        factors.remove(id(user));
        return "redirect:/profile/mfa?removed";
    }

    private void status(UserDetails user, Model model) {
        model.addAttribute("mfa", factors.status(id(user)));
        model.addAttribute("policy", settings.policy());
        model.addAttribute("required", settings.requires(user));
    }

    private String setupView(SecondFactorService.Enrolment enrolment, String error, Model model, HtmxRequest htmx) {
        model.addAttribute("qrSvg", enrolment.qrSvg());
        model.addAttribute("secret", enrolment.secret().replaceAll("(.{4})(?!$)", "$1 "));
        model.addAttribute("setupError", error);
        return htmx.isHtmxRequest() ? "profile/mfa-setup :: body" : "profile/mfa-setup";
    }

    private static int id(UserDetails user) {
        return Integer.parseInt(user.getUserId());
    }
}
