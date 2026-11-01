package org.knowledgeroot.app.mcp.oauth;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Disconnecting an assistant from the profile page. */
@Controller
@RequiredArgsConstructor
class McpConnectionsController {
    private final McpConnections connections;
    private final UserContext userContext;

    @PostMapping("/profile/connections/revoke")
    String revoke(@RequestParam String client) {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) return "redirect:/login";
        connections.revoke(Integer.parseInt(user.getUserId()), client);
        return "redirect:/profile?disconnected";
    }
}
