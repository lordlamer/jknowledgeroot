package org.knowledgeroot.app.security.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Second step of the browser sign-in. Enrolment (QR code, first code, recovery codes) when the
 * policy demands a token the account lacks, otherwise the code prompt. Five failures end the session.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
class MfaController {
    static final String PENDING_SECRET = "knowledgeroot.mfa.pendingSecret";
    static final String FAILURES = "knowledgeroot.mfa.failures";
    static final int MAX_FAILURES = 5;
    private final SecondFactorGate gate;
    private final SecondFactorService factors;

    @GetMapping("/mfa")
    String show(Authentication authentication, HttpSession session, Model model) {
        if (!(authentication instanceof SecondFactorPendingToken pending)) return "redirect:/";
        if (pending.getRequirement() == SecondFactorGate.Requirement.VERIFY) return "mfa/verify";
        String secret = (String) session.getAttribute(PENDING_SECRET);
        var enrolment = secret == null ? factors.begin(pending.getUser().getLogin())
                : factors.enrolment(pending.getUser().getLogin(), secret);
        session.setAttribute(PENDING_SECRET, enrolment.secret());
        model.addAttribute("qrSvg", enrolment.qrSvg());
        model.addAttribute("secret", enrolment.secret().replaceAll("(.{4})(?!$)", "$1 "));
        return "mfa/setup";
    }

    @PostMapping("/mfa/setup")
    String confirmSetup(@RequestParam String code, Authentication authentication, HttpServletRequest request,
                        HttpServletResponse response, RedirectAttributes redirect) {
        if (!(authentication instanceof SecondFactorPendingToken pending)) return "redirect:/";
        HttpSession session = request.getSession();
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (pending.getRequirement() != SecondFactorGate.Requirement.ENROL || secret == null) return "redirect:/mfa";
        java.util.List<String> codes;
        try {
            codes = factors.confirm(pending.getUserId(), secret, code);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 409) {
                session.removeAttribute(PENDING_SECRET);
                return "redirect:/mfa";
            }
            return failed(request, response, redirect);
        }
        session.removeAttribute(PENDING_SECRET);
        if (!promote(pending, request, response)) return "redirect:/login?error";
        redirect.addFlashAttribute("recoveryCodes", codes);
        return "redirect:/mfa/recovery-codes";
    }

    /** The codes arrive once as a flash attribute right after the enrolment. */
    @GetMapping("/mfa/recovery-codes")
    String recoveryCodes(Model model) {
        return model.containsAttribute("recoveryCodes") ? "mfa/recovery-codes" : "redirect:/";
    }

    @PostMapping("/mfa/verify")
    String verify(@RequestParam String code, Authentication authentication, HttpServletRequest request,
                  HttpServletResponse response, RedirectAttributes redirect) {
        if (!(authentication instanceof SecondFactorPendingToken pending)) return "redirect:/";
        if (pending.getRequirement() != SecondFactorGate.Requirement.VERIFY) return "redirect:/mfa";
        var result = factors.verify(pending.getUserId(), code);
        if (result == SecondFactorService.Result.INVALID) return failed(request, response, redirect);
        if (!promote(pending, request, response)) return "redirect:/login?error";
        return "redirect:/login/success" + (result == SecondFactorService.Result.RECOVERY_CODE_OK ? "?recovery" : "");
    }

    private boolean promote(SecondFactorPendingToken pending, HttpServletRequest request, HttpServletResponse response) {
        try {
            gate.promote(pending, request, response);
            request.getSession().removeAttribute(FAILURES);
            return true;
        } catch (UserNotFoundException e) {
            new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            return false;
        }
    }

    private String failed(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        HttpSession session = request.getSession();
        int failures = (session.getAttribute(FAILURES) instanceof Integer n ? n : 0) + 1;
        if (failures >= MAX_FAILURES) {
            log.warn("Second factor locked after {} failures for {}", failures, request.getRemoteAddr());
            new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            return "redirect:/login?mfaLocked";
        }
        session.setAttribute(FAILURES, failures);
        redirect.addFlashAttribute("error", "The code is not valid. " + (MAX_FAILURES - failures) + " attempts left.");
        return "redirect:/mfa";
    }
}
