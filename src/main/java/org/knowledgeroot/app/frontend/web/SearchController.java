package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.db.PageImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Slf4j
@RequiredArgsConstructor
class SearchController {
    private final PageImpl pageImpl;

    @GetMapping("/search")
    public String showSearch(
            @RequestParam("q") String searchQuery,
            Model model,
            HtmxRequest htmxRequest
    ) {
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("pages", pageImpl.searchContent(searchQuery));

        if (htmxRequest.isHtmxRequest()) {
            return "search/search :: body";
        }

        return "search/search";
    }
}
