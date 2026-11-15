package org.knowledgeroot.app.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.template.PageTemplate;
import org.knowledgeroot.app.page.template.PageTemplateRepository;
import org.knowledgeroot.app.page.template.PageTemplatesChanged;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.knowledgeroot.app.mcp.McpToolSupport.params;

/**
 * One MCP prompt per page template (R65), named {@code template_<id>}: it tells the assistant to read
 * the template with list_templates, fill it in and create the page with create_page(template=…) after
 * the user approved. The prompts are registered at start and whenever a template changes; the stateless
 * server has no sessions to notify, clients see the new list on their next prompts/list.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpTemplatePrompts {
    static final String PREFIX = "template_";
    private final McpToolSupport support;
    private final PageTemplateRepository templates;
    private final SpaceService spaces;
    private final ObjectProvider<McpStatelessSyncServer> server;
    private final Set<String> registered = new HashSet<>();

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        sync();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onTemplatesChanged(PageTemplatesChanged event) {
        sync();
    }

    /** Registers prompts for the current templates and drops the ones whose template is gone or renamed. */
    synchronized void sync() {
        McpStatelessSyncServer mcp = server.getIfAvailable();
        if (mcp == null) return;
        List<PageTemplate> current = templates.listAll();
        Set<String> wanted = new HashSet<>();
        try {
            for (PageTemplate template : current) {
                String name = PREFIX + template.id();
                wanted.add(name);
                // The title carries the template's name; re-register after a rename.
                if (registered.contains(name)) mcp.removePrompt(name);
                mcp.addPrompt(specification(template));
                registered.add(name);
            }
            for (String stale : List.copyOf(registered)) {
                if (wanted.contains(stale)) continue;
                mcp.removePrompt(stale);
                registered.remove(stale);
            }
        } catch (RuntimeException e) {
            log.warn("MCP template prompts could not be refreshed", e);
        }
    }

    private McpStatelessServerFeatures.SyncPromptSpecification specification(PageTemplate template) {
        String name = PREFIX + template.id();
        String spaceKey = spaces.spaces().find(template.spaceId()).map(Space::getKey).orElse("?");
        var prompt = new McpSchema.Prompt(name, template.name(),
                "Creates a page from the template \"" + template.name() + "\" of space " + spaceKey + ".",
                List.of(new McpSchema.PromptArgument("parent", "Page id the new page goes below; omit for a top-level page of the space", false),
                        new McpSchema.PromptArgument("details", "What the page is about: facts, names and dates to fill into the template", false)));
        return new McpStatelessServerFeatures.SyncPromptSpecification(prompt, (context, request) -> get(template.id(), request));
    }

    private McpSchema.GetPromptResult get(int templateId, McpSchema.GetPromptRequest request) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        String parent = text(arguments.get("parent"));
        String details = text(arguments.get("details"));
        return support.prompt(PREFIX + templateId, params("parent", parent, "details", details), () -> {
            PageTemplate template = templates.find(templateId).orElseThrow(() -> new McpToolException("The template no longer exists."));
            if (!spaces.canView(template.spaceId())) throw new McpToolException("The template was not found or you may not use it.");
            String spaceKey = spaces.spaces().find(template.spaceId()).map(Space::getKey).orElse(String.valueOf(template.spaceId()));
            String text = """
                    Create a Knowledgeroot page from the template "%s" (template id %d, space %s).
                    1. Call list_templates with space %d and take the content and labels of template %d as the structure.
                    2. Fill the structure with what I give you%s. Keep the template's headings and order; leave a short
                       placeholder where information is missing and ask me for it.
                    3. Show me the page as Markdown and the labels. Only after I approve, call create_page with
                       template=%d%s, the filled content and the name of the page.
                    """.formatted(template.name(), template.id(), spaceKey, template.spaceId(), template.id(),
                    details.isBlank() ? "" : ": " + details, template.id(),
                    parent.isBlank() ? " (ask me for the parent page, or omit parent for a top-level page)" : " and parent " + parent);
            return new McpSchema.GetPromptResult("Page from the template \"" + template.name() + "\"",
                    List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text.strip()))));
        });
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
