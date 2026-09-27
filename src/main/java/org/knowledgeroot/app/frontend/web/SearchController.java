package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
class SearchController {
    private static final int PAGE_SIZE = 20;
    private static final int SNIPPET_LENGTH = 300;
    private static final int MAX_PATH_DEPTH = 12;
    private final PageDao pages;
    private final UserContext users;

    /** Text fragment of a title or snippet; matches are rendered as highlighted, escaped text. */
    record Part(String text, boolean match) {}

    record SearchHit(int id, String name, String snippet, List<Part> title, List<Part> excerpt,
                     List<String> path, LocalDateTime changed) {}

    @GetMapping("/search")
    public String showSearch(@RequestParam("q") String query,
                             @RequestParam(name = "start", defaultValue = "0") Integer start,
                             Model model, HtmxRequest request) {
        RequestValidation.text(query, 200);
        int offset = RequestValidation.start(start);
        String term = query.trim();
        var user = users.getUserContext();
        Integer viewer = user.isGuest() ? null : Integer.valueOf(user.getUserId());
        List<Page> results = term.isEmpty() ? List.of() : pages.listVisiblePages(
                PageFilter.builder().query(term).start(offset).limit(PAGE_SIZE + 1).build(), viewer);
        // Ancestors are shared between hits, so resolve each visible parent only once per request.
        Map<Integer, Optional<Page>> parents = new HashMap<>();
        var hits = results.stream().limit(PAGE_SIZE).map(page -> {
            String snippet = snippet(page.getContent(), term);
            return new SearchHit(page.getPageId().value(), page.getName(), snippet,
                    highlight(page.getName(), term), highlight(snippet, term),
                    path(page.getParent(), viewer, parents), page.getChangeDate());
        }).toList();
        model.addAttribute("searchQuery", term);
        model.addAttribute("pages", hits);
        model.addAttribute("start", offset);
        model.addAttribute("previousStart", Math.max(0, offset - PAGE_SIZE));
        model.addAttribute("nextStart", offset + PAGE_SIZE);
        model.addAttribute("hasNext", results.size() > PAGE_SIZE && offset + PAGE_SIZE <= 100_000);
        return request.isHtmxRequest() ? "search/search :: body" : "search/search";
    }

    /** Plain-text excerpt centred on the first match, or the beginning of the page when only the title matches. */
    static String snippet(String html, String term) {
        if (html == null) return "";
        String text = HtmlUtils.htmlUnescape(html.replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
        if (text.length() <= SNIPPET_LENGTH) return text;
        int match = term.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
        if (match < 0) return text.substring(0, SNIPPET_LENGTH) + "…";
        int begin = Math.max(0, match - SNIPPET_LENGTH / 3);
        int end = Math.min(text.length(), begin + SNIPPET_LENGTH);
        begin = Math.max(0, end - SNIPPET_LENGTH);
        // Avoid cutting words in half at the excerpt boundaries.
        if (begin > 0) { int space = text.indexOf(' ', begin); if (space > 0 && space < match) begin = space + 1; }
        if (end < text.length()) { int space = text.lastIndexOf(' ', end); if (space > match + term.length()) end = space; }
        return (begin > 0 ? "…" : "") + text.substring(begin, end) + (end < text.length() ? "…" : "");
    }

    static List<Part> highlight(String text, String term) {
        if (text == null || text.isEmpty()) return List.of();
        if (term.isEmpty()) return List.of(new Part(text, false));
        var parts = new ArrayList<Part>();
        String lower = text.toLowerCase(Locale.ROOT), needle = term.toLowerCase(Locale.ROOT);
        int from = 0, match;
        // Lower-casing can change string length for a few scripts; fall back to no highlighting then.
        if (lower.length() != text.length()) return List.of(new Part(text, false));
        while ((match = lower.indexOf(needle, from)) >= 0) {
            if (match > from) parts.add(new Part(text.substring(from, match), false));
            parts.add(new Part(text.substring(match, match + needle.length()), true));
            from = match + needle.length();
        }
        if (from < text.length()) parts.add(new Part(text.substring(from), false));
        return parts;
    }

    /** Root-to-parent names limited to pages the viewer may see; stops at the first hidden ancestor. */
    private List<String> path(Integer parent, Integer viewer, Map<Integer, Optional<Page>> cache) {
        var names = new ArrayList<String>();
        Integer current = parent;
        while (current != null && current > 0 && names.size() < MAX_PATH_DEPTH) {
            int id = current;
            var ancestor = cache.computeIfAbsent(id, key -> pages.findNavigationPage(new PageId(key), viewer));
            if (ancestor.isEmpty()) break;
            names.addFirst(ancestor.get().getName());
            current = ancestor.get().getParent();
        }
        return names;
    }
}
