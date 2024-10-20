package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.content.domain.ContentDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Slf4j
@RequiredArgsConstructor
class SearchController {
    private final ContentDao contentImpl;

    @GetMapping("/search")
    public String showSearch(@RequestParam("q") String searchQuery, Model model) {

        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("contents", contentImpl.searchContent(searchQuery));

        return "search/search";
    }
}
