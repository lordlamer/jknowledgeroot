package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
class SidebarController {
    private final PageDao pageImpl;

    /**
     * Show sidebar
     *
     * @param model
     * @param parent
     * @param pageId
     * @return
     */
    @RequestMapping("/ui/sidebar")
    public String index(
            Model model,
            @RequestParam(name = "parent", required = false) Integer parent,
            @RequestParam(name = "singlePage", required = false) Integer singlePage
    ) {
        if(parent == null)
            parent = 0;

        PageFilter pageFilter = new PageFilter();
        pageFilter.setParent(parent);

        List<Page> pages = pageImpl.listPages(pageFilter);

        model.addAttribute("parentId", parent);

        if(parent == 0) {
            model.addAttribute("parentPage", null);
            if(singlePage != null) {
                model.addAttribute("singlePage", pageImpl.findById(new PageId(singlePage)));
            } else {
                model.addAttribute("singlePage", null);
            }
        } else {
            model.addAttribute("parentPage", pageImpl.findById(new PageId(parent)));
        }

        model.addAttribute("pages", pages);

        return "sidebar";
    }
}
