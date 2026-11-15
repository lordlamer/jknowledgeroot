package org.knowledgeroot.app.page.template;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A prepared title, content and labels that "New page" offers in a space (R65). Templates are not
 * pages: they have no history, permissions, comments or attachments and stay out of navigation and search.
 */
public record PageTemplate(int id, int spaceId, String name, String content, List<String> labels,
                           Integer createdBy, LocalDateTime createDate,
                           Integer changedBy, String changedByLogin, LocalDateTime changeDate) {
}
