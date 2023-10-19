package org.knowledgeroot.app.content.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.content.api.converter.ContentDtoConverter;
import org.knowledgeroot.app.content.api.dto.ContentDto;
import org.knowledgeroot.app.content.db.Content;
import org.knowledgeroot.app.content.domain.ContentDao;
import org.knowledgeroot.app.page.domain.PageDao;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
class ContentController {
    private final ContentDao contentImpl;
    private final PageDao pageImpl;
    private final ContentDtoConverter contentDtoConverter = new ContentDtoConverter();

    @GetMapping("/ui/page/{pageId}/content/new")
    public String showNewContent(
            @PathVariable("pageId") Integer pageId,
            Model model
    ) {
        model.addAttribute("page", pageImpl.findById(pageId));

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
        contentImpl.createContent(content);

        return new ModelAndView("redirect:/ui/page/" + pageId);
    }

    @GetMapping("/ui/page/{pageId}/content/{contentId}/edit")
    public String showEditContent(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("contentId") Integer contentId,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if(request.getHeader("HX-Request") != null && request.getHeader("HX-Request").equals("true")) {
            System.out.println("HX-Request: " + request.getHeader("HX-Request"));
            model.addAttribute("fullLayout", false);
        } else {
            System.out.println("HX-Trigger: DIRECT REQUEST");
            model.addAttribute("fullLayout", true);
        }

        model.addAttribute("page", pageImpl.findById(pageId));
        model.addAttribute("content", contentImpl.findById(contentId));

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
        contentImpl.updateContent(content);

        return new ModelAndView("redirect:/ui/page/" + pageId);
    }

    @DeleteMapping("/ui/page/{pageId}/content/{contentId}")
    public ResponseEntity deleteContent(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("contentId") Integer contentId,
            HttpServletResponse response
    ) {
        contentImpl.deleteContentById(contentId);

        // trigger reload page
        response.addHeader("HX-Trigger", "reload-page");

        return ResponseEntity.ok().build();
    }
}
