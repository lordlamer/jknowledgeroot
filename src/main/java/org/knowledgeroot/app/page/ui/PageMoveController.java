package org.knowledgeroot.app.page.ui;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class PageMoveController {
    private final PageMoveService moves;
    private final PageDao pages;
    private final PagePermissionDao permissions;

    @GetMapping("/ui/page/{id}/move")
    public String form(@PathVariable int id, @RequestParam(defaultValue="0") int parent,
                       @RequestParam(defaultValue="0") int start, @RequestParam(defaultValue="") String query,
                       Model model, HtmxRequest request) {
        var page = moves.movablePage(new PageId(id));
        RequestValidation.require(parent >= 0);
        RequestValidation.text(query, 255);
        start = RequestValidation.start(start);
        var filter = PageFilter.builder().parent(query.isBlank() ? parent : null).query(query.isBlank() ? null : query)
                .deleted(false).start(start).limit(51).build();
        // The administrative picker includes all live pages, without loading the whole tree.
        var items = pages.listPages(filter);
        model.addAttribute("page", page); model.addAttribute("items", items.stream().limit(50).toList());
        model.addAttribute("parent", parent); model.addAttribute("start", start); model.addAttribute("query", query);
        model.addAttribute("hasNext", items.size() > 50); model.addAttribute("inherits", permissions.isInheriting(page.getPageId()));
        model.addAttribute("destination", parent == 0 ? null : pages.findById(new PageId(parent)));
        model.addAttribute("validDestination", parent == 0 || pages.getPageHierarchy(new PageId(parent)).stream()
                .noneMatch(ancestor -> ancestor.getPageId().equals(page.getPageId())));
        return request.isHtmxRequest() ? "page/move :: body" : "page/move";
    }

    @PostMapping("/ui/page/{id}/move")
    public String move(@PathVariable int id, @RequestParam int parent, @RequestParam(required=false) Long revision) {
        moves.move(new PageId(id), parent, revision);
        return "redirect:/ui/page/" + id + "?trigger=reload-sidebar";
    }
}
