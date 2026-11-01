package org.knowledgeroot.app.mcp.oauth;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.mcp.McpAudit;
import org.knowledgeroot.app.mcp.McpSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Administrators' view of the MCP server: every user's connected assistants and the audit log. */
@Controller
@RequiredArgsConstructor
class McpAdminController {
    static final int PAGE_SIZE = 100;
    private final McpConnections connections;
    private final McpAudit audit;
    private final McpSettings settings;

    @GetMapping("/admin/mcp")
    String show(@RequestParam(required = false) String user, @RequestParam(defaultValue = "0") int start,
                @RequestParam(required = false) String disconnected, Model model, HtmxRequest htmx) {
        int offset = Math.max(0, Math.min(start, 1_000_000));
        var entries = audit.list(user, offset, PAGE_SIZE + 1);
        model.addAttribute("enabled", settings.enabled());
        model.addAttribute("connections", connections.listAll());
        model.addAttribute("entries", entries.stream().limit(PAGE_SIZE).toList());
        model.addAttribute("user", user == null ? "" : user.trim());
        model.addAttribute("start", offset);
        model.addAttribute("hasNext", entries.size() > PAGE_SIZE);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("retentionDays", McpAudit.RETENTION_DAYS);
        model.addAttribute("disconnected", disconnected != null);
        return htmx.isHtmxRequest() ? "admin/mcp :: body" : "admin/mcp";
    }

    @PostMapping("/admin/mcp/connections/revoke")
    String revoke(@RequestParam int userId, @RequestParam String client) {
        connections.revoke(userId, client);
        return "redirect:/admin/mcp?disconnected";
    }
}
