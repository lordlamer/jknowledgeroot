package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageFilter;
import org.knowledgeroot.app.page.db.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
class SidebarController {
    private final PageDao pageImpl;

    @RequestMapping("/ui/sidebar")
    public String index(Model model, @RequestParam(name = "parent", required = false) Integer parent) {
        if(parent == null)
            parent = 0;

        PageFilter pageFilter = new PageFilter();
        pageFilter.setParent(parent);

        List<Page> pages = pageImpl.listPages(pageFilter);

        model.addAttribute("parentId", parent);
        model.addAttribute("parentPage", pageImpl.findById(parent));
        model.addAttribute("pages", pages);

        return "sidebar";
    }
}
