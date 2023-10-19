package org.knowledgeroot.app.frontend.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SearchController {
    @GetMapping("/search")
    public String showSearch(Model model) {
        return "search/search";
    }
}
