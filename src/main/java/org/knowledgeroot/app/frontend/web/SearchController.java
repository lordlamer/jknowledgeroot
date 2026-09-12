package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@Controller
@RequiredArgsConstructor
class SearchController {
    private static final int PAGE_SIZE = 20;
    private final PageDao pages;
    private final UserContext users;

    record SearchHit(int id, String name, String snippet) {}

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
        model.addAttribute("searchQuery", term);
        model.addAttribute("pages", results.stream().limit(PAGE_SIZE).map(page -> new SearchHit(
                page.getPageId().value(), page.getName(), snippet(page.getContent()))).toList());
        model.addAttribute("start", offset);
        model.addAttribute("previousStart", Math.max(0, offset - PAGE_SIZE));
        model.addAttribute("nextStart", offset + PAGE_SIZE);
        model.addAttribute("hasNext", results.size() > PAGE_SIZE && offset + PAGE_SIZE <= 100_000);
        return request.isHtmxRequest() ? "search/search :: body" : "search/search";
    }

    private String snippet(String html) {
        if (html == null) return "";
        String text = HtmlUtils.htmlUnescape(html.replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
