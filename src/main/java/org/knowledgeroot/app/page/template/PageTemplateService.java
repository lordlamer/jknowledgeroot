package org.knowledgeroot.app.page.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.sanitizer.Sanitizer;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Page templates of a space (R65): space administrators manage them, everyone who may enter the space
 * sees them when creating a page. Changes are announced with {@link PageTemplatesChanged} so that the
 * MCP server keeps one prompt per template.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageTemplateService {
    /** Templates per space; enough for a team's page kinds, small enough for a select box and the prompt list. */
    static final int MAX_PER_SPACE = 100;
    static final int MAX_CONTENT_BYTES = 65_535;
    static final int MAX_LABELS = 32;
    static final int MAX_LABEL_LENGTH = 64;
    private final PageTemplateRepository templates;
    private final SpaceService spaces;
    private final UserContext users;
    private final ApplicationEventPublisher events;

    /** The templates of a space the user may enter, by name. */
    public List<PageTemplate> listForSpace(int spaceId) {
        spaces.requireView(spaceId);
        return templates.listForSpace(spaceId);
    }

    /** A template the user may use; unknown ids and templates of spaces the user may not enter look the same. */
    public PageTemplate template(int id) {
        PageTemplate template = templates.find(id).orElseThrow(PageTemplateService::notFound);
        if (!spaces.canView(template.spaceId())) throw notFound();
        return template;
    }

    /** The template for a new page in {@code spaceId}; templates of other spaces are refused. */
    public PageTemplate templateFor(int spaceId, int templateId) {
        PageTemplate template = template(templateId);
        if (template.spaceId() != spaceId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The template does not belong to this space.");
        return template;
    }

    public boolean canManage(int spaceId) {
        return spaces.canManage(spaceId);
    }

    @Transactional
    public PageTemplate create(int spaceId, String name, String content, List<String> labels) {
        spaces.requireManage(spaceId);
        String trimmedName = validName(name);
        String cleanContent = validContent(content);
        List<String> cleanLabels = validLabels(labels);
        if (templates.countForSpace(spaceId) >= MAX_PER_SPACE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space takes at most 100 templates.");
        if (templates.nameTaken(spaceId, trimmedName, null))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists in this space.");
        int id = templates.create(spaceId, trimmedName, cleanContent, cleanLabels, actor());
        log.info("Template {} \"{}\" created in space {} by user {}", id, trimmedName, spaceId, actor());
        events.publishEvent(new PageTemplatesChanged(spaceId));
        return templates.find(id).orElseThrow();
    }

    @Transactional
    public PageTemplate update(int id, String name, String content, List<String> labels) {
        PageTemplate template = templates.find(id).orElseThrow(PageTemplateService::notFound);
        spaces.requireManage(template.spaceId());
        String trimmedName = validName(name);
        String cleanContent = validContent(content);
        List<String> cleanLabels = validLabels(labels);
        if (templates.nameTaken(template.spaceId(), trimmedName, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists in this space.");
        templates.update(id, trimmedName, cleanContent, cleanLabels, actor());
        events.publishEvent(new PageTemplatesChanged(template.spaceId()));
        return templates.find(id).orElseThrow();
    }

    @Transactional
    public void delete(int id) {
        PageTemplate template = templates.find(id).orElseThrow(PageTemplateService::notFound);
        spaces.requireManage(template.spaceId());
        templates.delete(id);
        log.info("Template {} \"{}\" deleted from space {} by user {}", id, template.name(), template.spaceId(), actor());
        events.publishEvent(new PageTemplatesChanged(template.spaceId()));
    }

    /** Splits the comma-separated label field of the forms; blank entries are dropped. */
    public static List<String> parseLabels(String joined) {
        if (joined == null || joined.isBlank()) return List.of();
        return java.util.Arrays.stream(joined.split(",")).map(String::trim).filter(label -> !label.isEmpty()).toList();
    }

    private Integer actor() {
        UserDetails user = users.getUserContext();
        return user.isGuest() ? null : Integer.valueOf(user.getUserId());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found.");
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a template name of at most 255 characters.");
        return trimmed;
    }

    private static String validContent(String content) {
        String clean = Sanitizer.sanitize(content == null ? "" : content);
        if (clean.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The template content is too long.");
        return clean;
    }

    private static List<String> validLabels(List<String> labels) {
        if (labels == null) return List.of();
        List<String> clean = labels.stream().filter(Objects::nonNull).map(String::trim).filter(label -> !label.isEmpty()).distinct().toList();
        if (clean.size() > MAX_LABELS || clean.stream().anyMatch(label -> label.length() > MAX_LABEL_LENGTH))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Up to 32 labels of 64 characters each.");
        return clean;
    }
}
