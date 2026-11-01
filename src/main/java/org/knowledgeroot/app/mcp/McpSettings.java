package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.mfa.AppSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Runtime settings of the MCP server (table {@code app_setting}): the switch administrators flip and
 * the hosts an AI assistant may name as OAuth redirect target when it registers itself. Both are
 * read on every request, so they are cached for a few seconds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpSettings {
    static final String ENABLED = "mcp.enabled";
    static final String REDIRECT_HOSTS = "mcp.redirect-hosts";
    public static final List<String> DEFAULT_REDIRECT_HOSTS = List.of("claude.ai", "*.claude.ai");
    private static final Pattern HOST = Pattern.compile("(\\*\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final Duration CACHE = Duration.ofSeconds(10);
    private final AppSettings settings;
    private volatile Snapshot snapshot;

    private record Snapshot(boolean enabled, List<String> hosts, Instant loadedAt) {}

    public boolean enabled() {
        return snapshot().enabled();
    }

    public List<String> redirectHosts() {
        return snapshot().hosts();
    }

    public void update(boolean enabled, String hosts, int actor) {
        List<String> parsed = parseHosts(hosts);
        settings.set(ENABLED, enabled ? "on" : "off", actor);
        settings.set(REDIRECT_HOSTS, String.join(", ", parsed), actor);
        snapshot = null;
        log.info("MCP server switched {} by user {} (redirect hosts: {})", enabled ? "on" : "off", actor, parsed);
    }

    /**
     * Whether an assistant may be sent back to this URI after the sign-in: https to a listed host,
     * or any port on the loopback interface for assistants running on the user's own machine.
     */
    public boolean allowsRedirect(String uri) {
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = parsed.getHost() == null ? null : parsed.getHost().toLowerCase(Locale.ROOT);
        String scheme = parsed.getScheme() == null ? null : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (host == null || scheme == null || parsed.getFragment() != null) return false;
        if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"))
            return scheme.equals("http") || scheme.equals("https");
        if (!scheme.equals("https")) return false;
        return redirectHosts().stream().anyMatch(pattern -> pattern.startsWith("*.")
                ? host.endsWith(pattern.substring(1)) && host.length() > pattern.length() - 1
                : host.equals(pattern));
    }

    /** Comma, space or newline separated host names, optionally with a leading {@code *.} wildcard. */
    public static List<String> parseHosts(String text) {
        if (text == null) return DEFAULT_REDIRECT_HOSTS;
        var hosts = new ArrayList<String>();
        for (String raw : text.split("[,\\s]+")) {
            String host = raw.trim().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) continue;
            if (!HOST.matcher(host).matches())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter host names like claude.ai or *.example.org, one per line.");
            if (!hosts.contains(host)) hosts.add(host);
        }
        if (String.join(", ", hosts).length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The redirect host list is limited to 255 characters.");
        return hosts;
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null && current.loadedAt().plus(CACHE).isAfter(Instant.now())) return current;
        boolean enabled = settings.get(ENABLED).map("on"::equals).orElse(false);
        List<String> hosts = settings.get(REDIRECT_HOSTS).map(McpSettings::parseHostsLenient).orElse(DEFAULT_REDIRECT_HOSTS);
        current = new Snapshot(enabled, hosts, Instant.now());
        snapshot = current;
        return current;
    }

    private static List<String> parseHostsLenient(String stored) {
        try {
            return parseHosts(stored);
        } catch (ResponseStatusException e) {
            log.error("Stored MCP redirect host list is invalid; using the default list until an administrator saves it");
            return DEFAULT_REDIRECT_HOSTS;
        }
    }
}
