package org.knowledgeroot.app.content.controller;

import org.knowledgeroot.app.content.ContentService;
import org.knowledgeroot.app.content.impl.database.Content;
import org.knowledgeroot.app.page.PageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

@Controller
public class ContentController {
    private final ContentService contentService;
    private final PageService pageService;
    private final ContentDtoConverter contentDtoConverter = new ContentDtoConverter();

    public ContentController(ContentService contentService, PageService pageService) {
        this.contentService = contentService;
        this.pageService = pageService;
    }

    @GetMapping("/ui/page/{pageId}/content/new")
    public String showNewContent(
            @PathVariable("pageId") Integer pageId,
            Model model
    ) {
        model.addAttribute("page", pageService.findById(pageId));

        return "content/new";
    }

    @PostMapping("/ui/page/{pageId}/content/new")
    public ModelAndView createNewContent(
        @PathVariable("pageId") Integer pageId,
        @ModelAttribute ContentDto contentDto
    ) {
        contentDto.setActive(true);
        contentDto.setCreateDate(LocalDateTime.now());
        contentDto.setChangeDate(LocalDateTime.now());
        contentDto.setCreatedBy(0);
        contentDto.setChangedBy(0);
        contentDto.setParent(pageId);
        contentDto.setDeleted(false);
        contentDto.setSorting(1);
        contentDto.setTimeStart(LocalDateTime.now());
        contentDto.setTimeEnd(LocalDateTime.now());
        contentDto.setType("text");

        Content content = contentDtoConverter.convertBtoA(contentDto);
        contentService.createContent(content);

        return new ModelAndView("redirect:/ui/page/" + pageId + "?trigger=reload-sidebar");
    }
}
