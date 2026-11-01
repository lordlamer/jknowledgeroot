package org.knowledgeroot.app.mcp;

import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/**
 * The seam every MCP tool runs through: it names the caller for the log, enforces the write scope
 * for changing tools and decides which failure texts reach the assistant. Spring AI forwards the
 * message of any exception a tool throws, so everything that is not written for the assistant is
 * logged here and replaced by a fixed text.
 */
@Component
@Slf4j
public class McpToolSupport {
    public <T> T read(String tool, Supplier<T> body) {
        return run(tool, false, body);
    }

    public <T> T write(String tool, Supplier<T> body) {
        return run(tool, true, body);
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

    private <T> T run(String tool, boolean write, Supplier<T> body) {
        McpUserToken token = caller();
        log.info("MCP tool {} called by user {}", tool, token.getName());
        if (write && !token.hasScope(McpUserToken.SCOPE_WRITE))
            throw new McpToolException("This connection may only read. Reconnect Knowledgeroot and allow writing to use this tool.");
        try {
            return body.get();
        } catch (McpToolException e) {
            throw e;
        } catch (ResponseStatusException e) {
            throw new McpToolException(message(e));
        } catch (RuntimeException e) {
            log.warn("MCP tool {} failed for user {}", tool, token.getName(), e);
            throw new McpToolException("The request could not be completed. Try again or narrow the request.");
        }
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
