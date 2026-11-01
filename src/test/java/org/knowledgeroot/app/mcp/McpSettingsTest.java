package org.knowledgeroot.app.mcp;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.mfa.AppSettings;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpSettingsTest {
    private McpSettings settings(String hosts) {
        AppSettings store = mock(AppSettings.class);
        when(store.get("mcp.enabled")).thenReturn(Optional.of("on"));
        when(store.get("mcp.redirect-hosts")).thenReturn(Optional.ofNullable(hosts));
        return new McpSettings(store);
    }

    @Test void redirectsGoToListedHostsOverHttpsOrToTheLoopbackInterface() {
        var settings = settings("claude.ai, *.claude.ai\nassistant.example.org");
        assertTrue(settings.allowsRedirect("https://claude.ai/api/mcp/auth_callback"));
        assertTrue(settings.allowsRedirect("https://app.claude.ai/callback"));
        assertTrue(settings.allowsRedirect("https://assistant.example.org/oauth"));
        assertTrue(settings.allowsRedirect("http://localhost:51234/callback"));
        assertTrue(settings.allowsRedirect("http://127.0.0.1:8080/cb"));
        assertFalse(settings.allowsRedirect("http://claude.ai/callback"), "plain http to a public host");
        assertFalse(settings.allowsRedirect("https://evil.example.com/cb"));
        assertFalse(settings.allowsRedirect("https://claude.ai.evil.example.com/cb"));
        assertFalse(settings.allowsRedirect("https://notclaude.ai/cb"));
        assertFalse(settings.allowsRedirect("https://xassistant.example.org/cb"));
        assertFalse(settings.allowsRedirect("https://claude.ai/cb#fragment"));
        assertFalse(settings.allowsRedirect("javascript:alert(1)"));
        assertFalse(settings.allowsRedirect("not a uri"));
    }

    @Test void hostListsAreNormalisedAndValidated() {
        assertEquals(List.of("claude.ai", "*.claude.ai", "x.example.org"), McpSettings.parseHosts("Claude.AI,\n *.claude.ai  x.example.org, claude.ai"));
        assertEquals(McpSettings.DEFAULT_REDIRECT_HOSTS, McpSettings.parseHosts(null));
        assertEquals(List.of(), McpSettings.parseHosts("  "));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> McpSettings.parseHosts("https://claude.ai")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> McpSettings.parseHosts("localhost")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> McpSettings.parseHosts(java.util.stream.IntStream.range(0, 60).mapToObj(i -> "host" + i + ".example.org").collect(java.util.stream.Collectors.joining(" ")))).getStatusCode().value());
        assertEquals(McpSettings.DEFAULT_REDIRECT_HOSTS, settings("broken host!").redirectHosts(), "a corrupt stored list falls back to the default");
    }
}
