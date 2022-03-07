package org.knowledgeroot.app.page.controller;

import org.knowledgeroot.app.content.ContentFilter;
import org.knowledgeroot.app.content.ContentService;
import org.knowledgeroot.app.page.PageService;
import org.knowledgeroot.app.page.impl.database.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

@Controller
public class PageController {
    private final PageService pageService;
    private final ContentService contentService;
    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

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

    @PostMapping("/page/new")
    public ModelAndView createNewPage(@ModelAttribute PageDto pageDto) {
        System.out.println(pageDto.getName());
        System.out.println(pageDto.getSubtitle());
        System.out.println(pageDto.getDescription());

        pageDto.setCreateDate(LocalDateTime.now());
        pageDto.setCreatedBy(0);
        pageDto.setChangeDate(LocalDateTime.now());
        pageDto.setChangedBy(0);
        pageDto.setActive(true);
        pageDto.setAlias("");
        pageDto.setTimeStart(LocalDateTime.now());
        pageDto.setTimeEnd(LocalDateTime.now());
        pageDto.setContentCollapse(false);
        pageDto.setContentPosition("end");
        pageDto.setDeleted(false);
        pageDto.setIcon("");
        pageDto.setParent(0);
        pageDto.setShowContentDescription(true);
        pageDto.setShowTableOfContent(true);
        pageDto.setSorting(0);
        pageDto.setTooltip("");

        Page page = pageDtoConverter.convertBtoA(pageDto);
        pageService.createPage(page);

        return new ModelAndView("redirect:/page/" + page.getId());
    }
}
