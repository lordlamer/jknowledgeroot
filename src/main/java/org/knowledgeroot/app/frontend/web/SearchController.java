package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.db.PageImpl;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@Slf4j
@RequiredArgsConstructor
class SearchController {
    private final PageImpl pageImpl;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;

    private Integer getCurrentUserId() {
        UserDetails currentUser = userContext.getUserContext();
        if (currentUser.isGuest()) {
            return null;
        }
        try {
            return Integer.valueOf(currentUser.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping("/search")
    public String showSearch(
            @RequestParam("q") String searchQuery,
            Model model,
            HtmxRequest htmxRequest
    ) {
        Integer currentUserId = getCurrentUserId();
        List<Page> visiblePages = pageImpl.searchContent(searchQuery).stream()
                .filter(page -> pagePermissionDao.hasUserPermission(
                        page.getPageId(),
                        currentUserId,
                        PagePermission.PermissionLevel.VIEW
                ))
                .toList();

        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("pages", visiblePages);

        if (htmxRequest.isHtmxRequest()) {
            return "search/search :: body";
        }

        return "search/search";
    }
}
