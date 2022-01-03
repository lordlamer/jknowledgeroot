package org.knowledgeroot.app.page.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/page/new")
    public String showNewPage(Model model) {
        return "page/new";
    }
}
