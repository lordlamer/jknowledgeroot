package org.knowledgeroot.app.security.recovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class AccountRecoveryController {
    private final AccountRecoveryService recovery;
    private final RecoveryEmailRepository emails;
    private final UserContext users;

    @ModelAttribute
    void protect(HttpServletResponse response, Model model) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("confirmed", false);
        model.addAttribute("hasToken", false);
    }

    @GetMapping("/account/forgot-password")
    String forgot() { recovery.requireEnabled(); return "account/forgot"; }

    @PostMapping("/account/forgot-password")
    String request(@RequestParam(defaultValue="") String email, HttpServletRequest request, Model model) {
        recovery.requestReset(email, request.getRemoteAddr());
        model.addAttribute("sent", true);
        return "account/forgot";
    }

    @GetMapping("/profile/recovery")
    String profile(Model model) {
        recovery.requireEnabled();
        var user = users.getUserContext();
        if (user.isGuest()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        model.addAttribute("email", user.getEmail());
        model.addAttribute("verified", emails.verified(Integer.parseInt(user.getUserId())));
        return "account/profile";
    }

    @PostMapping("/profile/recovery")
    String requestVerification(@RequestParam String currentPassword, HttpServletRequest request, Model model) {
        profile(model);
        try {
            recovery.requestVerification(Integer.parseInt(users.getUserContext().getUserId()), currentPassword, request.getRemoteAddr());
            model.addAttribute("sent", true);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 400) throw ex;
            model.addAttribute("error", ex.getReason());
        }
        return "account/profile";
    }

    @GetMapping({"/account/reset-password", "/account/verify-email"})
    String link(@RequestParam(required=false) String token, HttpServletRequest request, Model model) {
        recovery.requireEnabled();
        String purpose = purpose(request);
        if (token != null) {
            request.getSession().removeAttribute(key(purpose));
            if (AccountRecoveryService.validToken(token))
                request.getSession().setAttribute(key(purpose), PasswordService.credentialTag(token));
            return "redirect:/account/" + (purpose.equals("reset") ? "reset-password" : "verify-email");
        }
        model.addAttribute("purpose", purpose);
        model.addAttribute("hasToken", token(request, purpose) != null);
        return "account/confirm";
    }

    @PostMapping({"/account/reset-password", "/account/verify-email"})
    String confirm(@RequestParam(required=false) String newPassword, @RequestParam(required=false) String confirmPassword,
                   HttpServletRequest request, HttpServletResponse response, Model model) {
        recovery.requireEnabled();
        String purpose = purpose(request);
        try {
            if (purpose.equals("reset")) recovery.resetFromSession(token(request, purpose), newPassword, confirmPassword, request.getRemoteAddr());
            else recovery.verifyFromSession(token(request, purpose), request.getRemoteAddr());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 400) throw ex;
            model.addAttribute("purpose", purpose);
            model.addAttribute("hasToken", token(request, purpose) != null);
            model.addAttribute("error", ex.getReason());
            response.setStatus(400);
            return "account/confirm";
        }
        request.getSession().removeAttribute(key(purpose));
        if (purpose.equals("reset")) {
            new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            return "redirect:/login?passwordChanged=true";
        }
        model.addAttribute("purpose", purpose); model.addAttribute("confirmed", true);
        return "account/confirm";
    }
    private static String purpose(HttpServletRequest request) {
        return request.getRequestURI().endsWith("verify-email") ? "verify" : "reset";
    }
    private static String key(String purpose) { return "account-recovery-" + purpose; }
    private static String token(HttpServletRequest request, String purpose) {
        var session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(key(purpose));
    }
}
