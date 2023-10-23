package org.knowledgeroot.app.page.ui;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.content.domain.ContentDao;
import org.knowledgeroot.app.content.domain.ContentFilter;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final PageDao pageImpl;
    private final ContentDao contentImpl;
    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

    @GetMapping("/ui/page/new")
    public String showNewPage(Model model) {
        return "page/new";
    }

    @GetMapping("/ui/page/{pageId}")
    public String showPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("page", pageImpl.findById(new PageId(pageId)));

        ContentFilter contentFilter = new ContentFilter();
        contentFilter.setParent(pageId);
        model.addAttribute("contents", contentImpl.listContents(contentFilter));

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        return "page/show";
    }

    @GetMapping("/ui/page/{pageId}/edit")
    public String showEditPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("page", pageImpl.findById(new PageId(pageId)));

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        return "page/edit";
    }

    @PostMapping("/ui/page/{pageId}/edit")
    public ModelAndView editPage(
            @PathVariable("pageId") Integer pageId,
            @ModelAttribute PageDto pageDto
    ) {
        pageDto.setId(pageId);
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
        pageImpl.updatePage(page);

        return new ModelAndView("redirect:/ui/page/" + page.getPageId().value() + "?trigger=reload-sidebar");
    }

    @PostMapping("/ui/page/new")
    public ModelAndView createNewPage(@ModelAttribute PageDto pageDto) {
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
        pageImpl.createPage(page);

        return new ModelAndView("redirect:/ui/page/" + page.getPageId().value() + "?trigger=reload-sidebar");
    }

    @DeleteMapping("/ui/page/{pageId}")
    public ModelAndView deletePage(@PathVariable("pageId") Integer pageId) {
        pageImpl.deletePageById(new PageId(pageId));

        return new ModelAndView("redirect:/ui/welcome?trigger=reload-sidebar");
    }
}
