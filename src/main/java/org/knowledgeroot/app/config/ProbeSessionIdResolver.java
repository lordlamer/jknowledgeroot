package org.knowledgeroot.app.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;
import java.util.List;
import java.util.Set;

/** Ignore login cookies on probes, including session lookups made by MVC infrastructure. */
final class ProbeSessionIdResolver implements HttpSessionIdResolver {
    private static final Set<String> PROBES = Set.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness");
    private final HttpSessionIdResolver delegate;

    ProbeSessionIdResolver(HttpSessionIdResolver delegate) { this.delegate = delegate; }

    @Override public List<String> resolveSessionIds(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equals(request.getMethod()) && PROBES.contains(path)) return List.of();
        return delegate.resolveSessionIds(request);
    }

    @Override public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        delegate.setSessionId(request, response, sessionId);
    }

    @Override public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        delegate.expireSession(request, response);
    }
}
