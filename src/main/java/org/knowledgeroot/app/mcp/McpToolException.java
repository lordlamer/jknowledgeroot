package org.knowledgeroot.app.mcp;

/**
 * The only exception an MCP tool lets through to the assistant. Its message is a fixed text
 * written for the assistant; it never carries a cause, because the transport would quote the
 * cause's message.
 */
public class McpToolException extends RuntimeException {
    public McpToolException(String message) {
        super(message);
    }
}
