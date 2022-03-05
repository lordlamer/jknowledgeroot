package org.knowledgeroot.app.page.controller;

import org.knowledgeroot.app.content.ContentFilter;
import org.knowledgeroot.app.content.ContentService;
import org.knowledgeroot.app.page.PageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final PageService pageService;
    private final ContentService contentService;

    public PageController(PageService pageService, ContentService contentService) {
        this.pageService = pageService;
        this.contentService = contentService;
    }

    @GetMapping("/page/new")
    public String showNewPage(Model model) {
        return "page/new";
    }

    @GetMapping("/page/{pageId}")
    public String showPage(@PathVariable("pageId") Integer pageId, Model model) {
        model.addAttribute("page", pageService.findById(pageId));

        ContentFilter contentFilter = new ContentFilter();
        contentFilter.setParent(pageId);
        model.addAttribute("contents", contentService.listContents(contentFilter));

        return "page/show";
    }
}
