package org.knowledgeroot.app.security.mfa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Keeps a session that still owes the second factor on the MFA pages: browsers are redirected to
 * {@code /mfa}, API clients receive 401. Sign-out and the static resources stay reachable.
 */
public class SecondFactorGateFilter extends OncePerRequestFilter {
    private static final List<String> OPEN = List.of("/mfa", "/logout", "/login", "/logmein", "/error", "/favicon.ico",
            "/api/v1/session", "/api/v1/auth");
    private static final List<String> OPEN_PREFIXES = List.of("/mfa/", "/webjars/", "/resources/", "/api/v1/auth/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecondFactorGate.pending(SecurityContextHolder.getContext().getAuthentication())) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!open(path)) {
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Second factor required.\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/mfa");
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean open(String path) {
        return OPEN.contains(path) || OPEN_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
