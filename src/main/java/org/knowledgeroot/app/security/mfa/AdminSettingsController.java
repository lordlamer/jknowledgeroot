package org.knowledgeroot.app.security.mfa;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.mcp.McpSettings;
import org.knowledgeroot.app.mcp.McpUrls;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Application settings administrators change at runtime: the second-factor policy and the MCP server. */
@Controller
@RequiredArgsConstructor
class AdminSettingsController {
    private final MfaSettings settings;
    private final McpSettings mcp;
    private final McpUrls mcpUrls;
    private final UserContext userContext;

    @GetMapping("/admin/settings")
    String show(@RequestParam(required = false) String saved, Model model, HtmxRequest htmx, HttpServletRequest request) {
        model.addAttribute("policy", settings.policy());
        model.addAttribute("policies", MfaPolicy.values());
        model.addAttribute("saved", saved != null);
        model.addAttribute("mcpEnabled", mcp.enabled());
        model.addAttribute("mcpRedirectHosts", String.join("\n", mcp.redirectHosts()));
        model.addAttribute("mcpUrl", mcpUrls.resource(request));
        return htmx.isHtmxRequest() ? "admin/settings :: body" : "admin/settings";
    }

    @PostMapping("/admin/settings")
    String save(@RequestParam String mfaPolicy, @RequestParam(required = false) String mcpEnabled,
                @RequestParam(required = false) String mcpRedirectHosts) {
        MfaPolicy policy;
        try {
            policy = MfaPolicy.fromValue(mfaPolicy);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown policy");
        }
        int actor = Integer.parseInt(userContext.getUserContext().getUserId());
        // Validate the MCP input before anything is written.
        if (mcpRedirectHosts != null) McpSettings.parseHosts(mcpRedirectHosts);
        settings.update(policy, actor);
        if (mcpRedirectHosts != null) mcp.update(mcpEnabled != null, mcpRedirectHosts, actor);
        return "redirect:/admin/settings?saved";
    }
}
