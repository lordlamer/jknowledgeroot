package org.knowledgeroot.app.page.ui;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class PageHistoryController {
    private final PageDao pages;
    private final PageEditingService editing;
    private final UserContext users;

    @GetMapping("/ui/page/{id}/history")
    public String history(@PathVariable int id, @RequestParam(defaultValue="0") int start,
                          Model model, HtmxRequest request) {
        var pageId = new PageId(id);
        model.addAttribute("page", editing.historyPage(pageId));
        start = RequestValidation.start(start);
        var items = pages.listRevisions(pageId, start);
        model.addAttribute("items", items.stream().limit(50).toList());
        model.addAttribute("start", start);
        model.addAttribute("hasNext", items.size() > 50);
        model.addAttribute("admin", users.getUserContext().isAdmin());
        return request.isHtmxRequest() ? "page/history :: body" : "page/history";
    }

    @GetMapping("/ui/page/{id}/history/{historyId}")
    public String version(@PathVariable int id, @PathVariable int historyId, Model model, HtmxRequest request) {
        var pageId = new PageId(id);
        model.addAttribute("page", editing.historyPage(pageId));
        model.addAttribute("previous", pages.findRevision(pageId, historyId));
        return request.isHtmxRequest() ? "page/revision :: body" : "page/revision";
    }

    @PostMapping("/ui/page/{id}/history/{historyId}/restore")
    public String restore(@PathVariable int id, @PathVariable int historyId,
                          @RequestParam(required=false) Long revision) {
        editing.restore(new PageId(id), historyId, revision);
        return "redirect:/ui/page/" + id + "?trigger=reload-sidebar";
    }

    @GetMapping("/ui/page/deleted")
    public String deleted(@RequestParam(defaultValue="0") int start, Model model, HtmxRequest request) {
        if (!users.getUserContext().isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        start = RequestValidation.start(start);
        var filter = new PageFilter();
        filter.setDeleted(true); filter.setStart(start); filter.setLimit(51);
        var items = pages.listPages(filter);
        model.addAttribute("items", items.stream().limit(50).toList());
        model.addAttribute("start", start); model.addAttribute("hasNext", items.size() > 50);
        return request.isHtmxRequest() ? "page/deleted :: body" : "page/deleted";
    }
}
