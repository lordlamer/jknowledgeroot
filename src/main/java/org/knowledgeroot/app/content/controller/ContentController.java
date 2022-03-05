package org.knowledgeroot.app.content.controller;

import org.knowledgeroot.app.content.ContentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ContentController {
    private ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/content/new")
    public String showNewContent(Model model) {
        return "content/new";
    }
}
