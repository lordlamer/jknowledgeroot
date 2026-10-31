package org.knowledgeroot.app.security.mfa;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Application settings administrators change at runtime; today the second-factor policy. */
@Controller
@RequiredArgsConstructor
class AdminSettingsController {
    private final MfaSettings settings;
    private final UserContext userContext;

    @GetMapping("/admin/settings")
    String show(@RequestParam(required = false) String saved, Model model, HtmxRequest htmx) {
        model.addAttribute("policy", settings.policy());
        model.addAttribute("policies", MfaPolicy.values());
        model.addAttribute("saved", saved != null);
        return htmx.isHtmxRequest() ? "admin/settings :: body" : "admin/settings";
    }

    @PostMapping("/admin/settings")
    String save(@RequestParam String mfaPolicy) {
        MfaPolicy policy;
        try {
            policy = MfaPolicy.fromValue(mfaPolicy);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown policy");
        }
        settings.update(policy, Integer.parseInt(userContext.getUserContext().getUserId()));
        return "redirect:/admin/settings?saved";
    }
}
