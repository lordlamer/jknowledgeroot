package org.knowledgeroot.app.ui;

import org.knowledgeroot.app.page.PageFilter;
import org.knowledgeroot.app.page.PageService;
import org.knowledgeroot.app.page.impl.database.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SidebarController {
    private PageService pageService;

    public SidebarController(PageService pageService) {
        this.pageService = pageService;
    }

    @RequestMapping("/ui/sidebar")
    public String index(Model model, @RequestParam(name = "parent", required = false) Integer parent) {
        if(parent == null)
            parent = 0;

        PageFilter pageFilter = new PageFilter();
        pageFilter.setParent(parent);

        List<Page> pages = pageService.listPages(pageFilter);

        model.addAttribute("parentId", parent);
        model.addAttribute("parentPage", pageService.findById(parent));
        model.addAttribute("pages", pages);

        return "sidebar";
    }
}
