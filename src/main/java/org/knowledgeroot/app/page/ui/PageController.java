package org.knowledgeroot.app.page.ui;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final PageDao pageImpl;
    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

    @GetMapping("/ui/page/new")
    public String showNewPage(HtmxRequest htmxRequest) {
        if(htmxRequest.isHtmxRequest()) {
            return "page/new :: body";
        }

        return "page/new";
    }

    @GetMapping("/ui/page/{pageId}")
    public String showPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        PageId pid = new PageId(pageId);
        Page page = pageImpl.findById(pid);
        List<Page> hierarchy = pageImpl.getPageHierarchy(pid);

        model.addAttribute("page", page);
        model.addAttribute("breadcrumb", hierarchy);

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/show :: body";
        }

        return "page/show";
    }

    @GetMapping("/ui/page/{pageId}/edit")
    public String showEditPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        model.addAttribute("page", pageImpl.findById(new PageId(pageId)));

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/edit :: body";
        }

        return "page/edit";
    }

    @GetMapping("/ui/page/{pageId}/new")
    public String showNewPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        model.addAttribute("page", pageImpl.findById(new PageId(pageId)));

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/new :: body";
        }

        return "page/new";
    }

    @PostMapping("/ui/page/{pageId}/edit")
    public ModelAndView editPage(
            @PathVariable("pageId") Integer pageId,
            @ModelAttribute PageDto pageDto
    ) {
        pageDto.setId(pageId);
        pageDto.setChangeDate(LocalDateTime.now());
        pageDto.setChangedBy(1);
        pageDto.setActive(true);
        pageDto.setContent(pageDto.getContent());
        pageDto.setTimeStart(LocalDateTime.now());
        pageDto.setTimeEnd(LocalDateTime.now());
        pageDto.setDeleted(false);
        pageDto.setParent(null);

        Page page = pageDtoConverter.convertBtoA(pageDto);
        pageImpl.updatePage(page);

        return new ModelAndView("redirect:/ui/page/" + page.getPageId().value() + "?trigger=reload-sidebar");
    }

    @PostMapping("/ui/page/new")
    public ModelAndView createNewPage(@ModelAttribute PageDto pageDto) {
        pageDto.setCreateDate(LocalDateTime.now());
        pageDto.setCreatedBy(1);
        pageDto.setChangeDate(LocalDateTime.now());
        pageDto.setChangedBy(1);
        pageDto.setActive(true);
        pageDto.setContent(pageDto.getContent());
        pageDto.setTimeStart(LocalDateTime.now());
        pageDto.setTimeEnd(LocalDateTime.now());
        pageDto.setDeleted(false);

        if(pageDto.getParent() == null)
            pageDto.setParent(0);

        Page page = pageDtoConverter.convertBtoA(pageDto);
        pageImpl.createPage(page);

        if(page.getParent() != null && page.getParent() > 0)
            return new ModelAndView("redirect:/ui/page/" + page.getParent() + "?trigger=reload-sidebar");
        else
            return new ModelAndView("redirect:/?trigger=reload-sidebar");
    }

    @DeleteMapping("/ui/page/{pageId}")
    public ModelAndView deletePage(@PathVariable("pageId") Integer pageId) {
        pageImpl.deletePageById(new PageId(pageId));

        return new ModelAndView("redirect:/ui/welcome?trigger=reload-sidebar");
    }
}
