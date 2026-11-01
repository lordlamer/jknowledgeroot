package org.knowledgeroot.app.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * The public origin the MCP server is reached at. Tokens name it as issuer and audience, the
 * metadata documents point to it, and assistants are sent back to it after the sign-in. It is
 * either configured ({@code knowledgeroot.mcp.public-url}, usually {@code KR_PUBLIC_URL}) or
 * derived from the request like Spring Authorization Server derives its issuer.
 */
@Component
public class McpUrls {
    public static final String MCP_PATH = "/mcp";
    public static final String RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";
    private final String configured;

    public McpUrls(@Value("${knowledgeroot.mcp.public-url:}") String publicUrl) {
        String trimmed = publicUrl == null ? "" : publicUrl.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        configured = trimmed;
    }

    public Optional<String> configured() {
        return configured.isEmpty() ? Optional.empty() : Optional.of(configured);
    }

    /** Scheme, host, port and context path of the request, or the configured public URL. */
    public String origin(HttpServletRequest request) {
        if (!configured.isEmpty()) return configured;
        return UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
                .replacePath(request.getContextPath()).replaceQuery(null).fragment(null).build().toUriString();
    }

    /** The origin of the request bound to the current thread. */
    public Optional<String> currentOrigin() {
        if (!configured.isEmpty()) return Optional.of(configured);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return Optional.of(origin(attributes.getRequest()));
        return Optional.empty();
    }

    public String resource(HttpServletRequest request) {
        return origin(request) + MCP_PATH;
    }

    public String resourceMetadata(HttpServletRequest request) {
        return origin(request) + RESOURCE_METADATA_PATH + MCP_PATH;
    }
}
