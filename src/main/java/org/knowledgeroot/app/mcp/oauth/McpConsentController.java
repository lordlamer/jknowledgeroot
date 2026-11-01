package org.knowledgeroot.app.mcp.oauth;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.mcp.McpSettings;
import org.knowledgeroot.app.mcp.McpUserToken;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Set;

/**
 * The consent page of the OAuth flow: names the assistant and lets the user decide whether it may
 * only read or also write. The form posts back to the authorization endpoint, where Spring
 * Authorization Server records the granted scopes and sends the assistant its code.
 */
@Controller
@RequiredArgsConstructor
class McpConsentController {
    static final String PATH = "/oauth2/consent";
    private final McpSettings settings;
    private final RegisteredClientRepository clients;
    private final UserContext userContext;

    @GetMapping(PATH)
    String consent(@RequestParam("client_id") String clientId, @RequestParam String state,
                   @RequestParam(required = false) String scope, Model model) {
        if (!settings.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown client");
        Set<String> requested = scope == null || scope.isBlank() ? Set.of() : Set.of(scope.trim().split("\\s+"));
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("clientHost", client.getRedirectUris().stream().findFirst()
                .map(uri -> { try { return URI.create(uri).getHost(); } catch (IllegalArgumentException e) { return null; } })
                .orElse(null));
        model.addAttribute("state", state);
        model.addAttribute("writeRequested", requested.contains(McpUserToken.SCOPE_WRITE));
        model.addAttribute("readScope", McpUserToken.SCOPE_READ);
        model.addAttribute("writeScope", McpUserToken.SCOPE_WRITE);
        model.addAttribute("user", user);
        return "mcp/consent";
    }
}
