package org.knowledgeroot.app.content.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.knowledgeroot.app.content.ContentService;
import org.knowledgeroot.app.content.impl.database.Content;
import org.knowledgeroot.app.page.PageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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

        return new ModelAndView("redirect:/ui/page/" + pageId);
    }

    @GetMapping("/ui/page/{pageId}/content/{contentId}/edit")
    public String showEditContent(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("contentId") Integer contentId,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("page", pageService.findById(pageId));
        model.addAttribute("content", contentService.findById(contentId));

        return "content/edit";
    }

    @PostMapping("/ui/page/{pageId}/content/{contentId}/edit")
    public ModelAndView editContent(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("contentId") Integer contentId,
            @ModelAttribute ContentDto contentDto
    ) {
        contentDto.setId(contentId);
        contentDto.setParent(pageId);
        contentDto.setActive(true);
        contentDto.setCreateDate(LocalDateTime.now());
        contentDto.setChangeDate(LocalDateTime.now());
        contentDto.setCreatedBy(0);
        contentDto.setChangedBy(0);
        contentDto.setDeleted(false);
        contentDto.setSorting(1);
        contentDto.setTimeStart(LocalDateTime.now());
        contentDto.setTimeEnd(LocalDateTime.now());
        contentDto.setType("text");

        Content content = contentDtoConverter.convertBtoA(contentDto);
        contentService.updateContent(content);

        return new ModelAndView("redirect:/ui/page/" + pageId);
    }

    @DeleteMapping("/ui/page/{pageId}/content/{contentId}")
    public ResponseEntity deleteContent(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("contentId") Integer contentId,
            HttpServletResponse response
    ) {
        contentService.deleteContentById(contentId);

        // trigger reload page
        response.addHeader("HX-Trigger", "reload-page");

        return ResponseEntity.ok().build();
    }
}
