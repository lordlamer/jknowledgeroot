package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The seam every MCP tool, resource and prompt runs through: it names the caller, enforces the
 * write scope for changing tools, limits the call rate per user, writes the audit row and decides
 * which failure texts reach the assistant. Spring AI forwards the message of any exception a
 * tool throws, so everything that is not written for the assistant is logged here and replaced
 * by a fixed text.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpToolSupport {
    /** Calls per user and minute before the assistant is told to slow down. */
    static final int CALLS_PER_MINUTE = 240;
    private static final long WINDOW_MS = 60_000;
    private final McpAudit audit;
    private final Map<Integer, Deque<Long>> calls = new ConcurrentHashMap<>();

    public <T> T read(String tool, Supplier<T> body) {
        return run("tool", tool, false, Map.of(), body);
    }

    public <T> T read(String tool, Map<String, Object> parameters, Supplier<T> body) {
        return run("tool", tool, false, parameters, body);
    }

    public <T> T write(String tool, Supplier<T> body) {
        return run("tool", tool, true, Map.of(), body);
    }

    public <T> T write(String tool, Map<String, Object> parameters, Supplier<T> body) {
        return run("tool", tool, true, parameters, body);
    }

    public <T> T resource(String name, Map<String, Object> parameters, Supplier<T> body) {
        return run("resource", name, false, parameters, body);
    }

    public <T> T prompt(String name, Map<String, Object> parameters, Supplier<T> body) {
        return run("prompt", name, false, parameters, body);
    }

    /** Builds the parameter map for the audit; null values are allowed and recorded as absent. */
    public static Map<String, Object> params(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }

    /** The user behind the current MCP request. */
    public static McpUserToken caller() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof McpUserToken token) return token;
        throw new McpToolException("Sign in to Knowledgeroot to use this tool.");
    }

    public static UserDetails user() {
        return (UserDetails) caller().getDetails();
    }

    public static int userId() {
        return Integer.parseInt(user().getUserId());
    }

    private <T> T run(String kind, String name, boolean write, Map<String, Object> parameters, Supplier<T> body) {
        long started = System.nanoTime();
        McpUserToken token;
        try {
            token = caller();
        } catch (McpToolException e) {
            record(null, kind, name, parameters, "denied", e.getMessage(), started);
            throw e;
        }
        String outcome = "ok";
        String detail = null;
        try {
            if (!admit(Integer.parseInt(((UserDetails) token.getDetails()).getUserId()))) {
                outcome = "limited";
                throw new McpToolException("Too many requests. Wait a minute before calling Knowledgeroot again.");
            }
            if (write && !token.hasScope(McpUserToken.SCOPE_WRITE)) {
                outcome = "denied";
                throw new McpToolException("This connection may only read. Reconnect Knowledgeroot and allow writing to use this tool.");
            }
            try {
                return body.get();
            } catch (McpToolException e) {
                outcome = "error";
                throw e;
            } catch (ResponseStatusException e) {
                outcome = "error";
                throw new McpToolException(message(e));
            } catch (RuntimeException e) {
                outcome = "failed";
                log.warn("MCP {} {} failed for user {}", kind, name, token.getName(), e);
                throw new McpToolException("The request could not be completed. Try again or narrow the request.");
            }
        } catch (McpToolException e) {
            detail = e.getMessage();
            throw e;
        } finally {
            record(token, kind, name, parameters, outcome, detail, started);
        }
    }

    /** Sliding window per user; old timestamps are dropped on every call. */
    private boolean admit(int userId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = calls.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < now - WINDOW_MS) window.pollFirst();
            if (window.size() >= CALLS_PER_MINUTE) return false;
            window.addLast(now);
            return true;
        }
    }

    private void record(McpUserToken token, String kind, String name, Map<String, Object> parameters, String outcome,
                        String detail, long started) {
        try {
            UserDetails user = token == null ? null : (UserDetails) token.getDetails();
            audit.record(user == null ? null : Integer.valueOf(user.getUserId()), user == null ? null : user.getLogin(),
                    token == null ? null : token.getClientId(), kind, name, describe(parameters), outcome, detail,
                    (System.nanoTime() - started) / 1_000_000, remoteAddress());
            log.info("MCP {} {} by user {}: {}", kind, name, user == null ? "-" : user.getLogin(), outcome);
        } catch (RuntimeException e) {
            // The audit must not break the call that it records.
            log.error("MCP audit row could not be written", e);
        }
    }

    /** A compact text of the parameters: scalars as given, texts shortened, contents only by size. */
    static String describe(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return null;
        var out = new StringBuilder();
        parameters.forEach((key, value) -> {
            if (value == null) return;
            if (!out.isEmpty()) out.append(", ");
            out.append(key).append('=');
            if (key.equals("content") || key.equals("text")) {
                out.append('[').append(String.valueOf(value).length()).append(" chars]");
            } else if (value instanceof Collection<?> list) {
                out.append(list.size()).append(" items");
            } else {
                String text = String.valueOf(value).replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ");
                out.append(text.length() > 80 ? text.substring(0, 79) + "…" : text);
            }
        });
        return out.isEmpty() ? null : out.toString();
    }

    private static String remoteAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return attributes.getRequest().getRemoteAddr();
        return null;
    }

    /** Our status exceptions carry fixed English texts; the ones without get one per status. */
    static String message(ResponseStatusException e) {
        if (e.getReason() != null && !e.getReason().isBlank()) return e.getReason();
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) return "The request could not be completed.";
        return switch (status) {
            case FORBIDDEN -> "You do not have access to this item.";
            case NOT_FOUND -> "The item was not found.";
            case CONFLICT -> "The page has changed meanwhile. Read it again and retry with the current revision.";
            case PRECONDITION_REQUIRED -> "Pass the current revision of the page.";
            case BAD_REQUEST -> "The input is not valid.";
            default -> "The request could not be completed.";
        };
    }
}
