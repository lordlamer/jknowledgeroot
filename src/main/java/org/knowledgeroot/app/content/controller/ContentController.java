package org.knowledgeroot.app.content.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ContentController {
    @GetMapping("/content/new")
    public String showNewContent(Model model) {
        return "content/new";
    }
}
