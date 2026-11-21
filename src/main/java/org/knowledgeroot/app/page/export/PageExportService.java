package org.knowledgeroot.app.page.export;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageEditingService;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Page export (R69): one page as PDF, Word or Markdown for anyone who may view it. The content is the
 * published page; a draft page that was never published exports its working copy for users who may edit
 * it, as the page view shows it. Images are embedded from attachments the viewer may see.
 */
@Service
@RequiredArgsConstructor
public class PageExportService {
    static final int MAX_IMAGES = 50;
    static final int MAX_FILENAME = 80;

    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final PageEditingService editing;
    private final SpaceService spaces;
    private final UserContext users;
    private final ExportImages images;
    private final MessageSource messages;
    private final PdfWriter pdf;
    private final DocxWriter docx;
    private final MarkdownWriter markdown;

    public PageExport export(int pageId, ExportFormat format) {
        Integer viewer = viewer();
        PageId id = new PageId(pageId);
        if (!permissions.hasUserPermission(id, viewer, PagePermission.PermissionLevel.VIEW)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page.");
        }
        Page page;
        try {
            page = pages.findById(id);
        } catch (EmptyResultDataAccessException ex) {
            page = null;
        }
        if (page == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        ExportDocument document = document(page, viewer);
        ExportText text = new ExportText(messages, LocaleContextHolder.getLocale());
        byte[] bytes = switch (format) {
            case PDF -> pdf.write(document, text);
            case DOCX -> docx.write(document, text);
            case MARKDOWN -> markdown.write(document, text);
        };
        return new PageExport(filename(page.getName(), format), format.mediaType(), bytes);
    }

    ExportDocument document(Page page, Integer viewer) {
        PageId id = page.getPageId();
        String content = page.getContent();
        if (!page.isPublished() && permissions.hasUserPermission(id, viewer, PagePermission.PermissionLevel.EDIT)) {
            content = editing.draft(id).map(draft -> draft.content()).orElse(content);
        }
        Element body = Jsoup.parseBodyFragment(content == null ? "" : content).body();
        List<ExportImage> embedded = new ArrayList<>();
        Map<String, Integer> bySource = new HashMap<>();
        for (Element img : body.select("img")) {
            String src = img.attr("src").trim();
            Integer index = bySource.get(src);
            if (index == null && embedded.size() < MAX_IMAGES) {
                var image = images.load(src, viewer);
                if (image.isPresent()) {
                    embedded.add(image.get());
                    index = embedded.size() - 1;
                    bySource.put(src, index);
                }
            }
            if (index != null) img.attr(ExportDocument.IMAGE_ATTRIBUTE, String.valueOf(index));
        }
        List<String> path = pages.getPageHierarchy(id).stream()
                .filter(ancestor -> permissions.hasUserPermission(ancestor.getPageId(), viewer, PagePermission.PermissionLevel.VIEW))
                .map(Page::getName).toList();
        String space = page.getSpaceId() == null ? null
                : spaces.spaces().find(page.getSpaceId()).map(Space::getName).orElse(null);
        String title = page.getName() == null || page.getName().isBlank() ? "Page " + id : page.getName();
        return new ExportDocument(title, space, path, labels.listForPage(id),
                page.getCreatedByLogin(), page.getCreateDate(), page.getChangedByLogin(), page.getChangeDate(),
                body, List.copyOf(embedded));
    }

    /** The page name as a file name: reserved and control characters removed, at most 80 characters. */
    static String filename(String name, ExportFormat format) {
        String base = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (base.length() > MAX_FILENAME) base = base.substring(0, MAX_FILENAME).trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "page";
        return base + "." + format.extension();
    }

    private Integer viewer() {
        UserDetails user = users.getUserContext();
        if (user.isGuest()) return null;
        try {
            return Integer.valueOf(user.getUserId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
