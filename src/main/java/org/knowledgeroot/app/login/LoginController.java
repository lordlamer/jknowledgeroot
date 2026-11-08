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
    private final org.knowledgeroot.app.security.oidc.OidcSettings oidc;
    private final org.knowledgeroot.app.security.oidc.OidcClientRegistrations oidcRegistrations;

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

        // "oidc": the provider's sign-in did not complete (R64); anything else is the password form.
        model.addAttribute("error", error != null && !error.equals("oidc"));
        model.addAttribute("oidcError", "oidc".equals(error));
        if (oidc.enabled()) model.addAttribute("oidc", oidc.getName());

        if (logout != null) {
            model.addAttribute("msg", messages.get("login.logged-out"));
        }

        if(htmxRequest.isHtmxRequest()) {
            return "login :: body";
        }

        return "login";
    }

    /** Starts the OpenID Connect sign-in (R64), after making sure the provider answers. */
    @GetMapping(org.knowledgeroot.app.security.oidc.OidcClientRegistrations.START_PATH)
    public String loginThroughProvider() {
        if (!oidc.enabled()) return "redirect:/login";
        if (!oidcRegistrations.available()) return "redirect:/login?error=oidc";
        return "redirect:" + org.knowledgeroot.app.security.oidc.OidcClientRegistrations.AUTHORIZATION_PATH;
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
