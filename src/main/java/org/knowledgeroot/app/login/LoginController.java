package org.knowledgeroot.app.login;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@lombok.RequiredArgsConstructor
class LoginController {
    private final org.knowledgeroot.app.i18n.UiMessages messages;

    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (authenticated) {
            if (htmxRequest.isHtmxRequest()) {
                response.setHeader("HX-Redirect", "/");
                return "login :: body";
            }
            return "redirect:/";
        }

        if (error != null) {
            model.addAttribute("error", true);
        } else {
            model.addAttribute("error", false);
        }

        if (logout != null) {
            model.addAttribute("msg", messages.get("login.logged-out"));
        }

        if(htmxRequest.isHtmxRequest()) {
            return "login :: body";
        }

        return "login";
    }

    @GetMapping("/login/success")
    public String loginSuccess(
            HtmxRequest htmxRequest,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response
    ) {
        // An assistant's OAuth flow sent the browser here; continue it instead of opening the start page.
        var requestCache = new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
        var saved = requestCache.getRequest(request, response);
        if (saved != null && saved.getRedirectUrl() != null && saved.getRedirectUrl().contains("/oauth2/authorize")) {
            requestCache.removeRequest(request, response);
            if (htmxRequest.isHtmxRequest()) {
                response.setHeader("HX-Redirect", saved.getRedirectUrl());
                return "login :: body";
            }
            return "redirect:" + saved.getRedirectUrl();
        }
        if (htmxRequest.isHtmxRequest()) {
            response.setHeader("HX-Redirect", "/");
            return "login :: body";
        }
        return "redirect:/";
    }

    @GetMapping("/logout/success")
    public String logoutSuccess(
            HtmxRequest htmxRequest,
            HttpServletResponse response
    ) {
        if (htmxRequest.isHtmxRequest()) {
            response.setHeader("HX-Redirect", "/login?logout");
            return "login :: body";
        }
        return "redirect:/login?logout";
    }
}
