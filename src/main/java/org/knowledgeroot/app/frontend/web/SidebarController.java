package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
class SidebarController {
    private final PageDao pageImpl;
    private final PageStarDao pageStarDao;
    private final UserContext userContext;

    /**
     * Get the current user ID for permission checks.
     * Returns null for guest users, which will be handled by the permission system.
     */
    private Integer getCurrentUserId() {
        UserDetails currentUser = userContext.getUserContext();
        if (currentUser.isGuest()) {
            return null; // Guest users have no user ID
        }
        try {
            return Integer.valueOf(currentUser.getUserId());
        } catch (NumberFormatException e) {
            // If userId is not a valid integer, treat as guest
            return null;
        }
    }

    /**
     * Show sidebar
     *
     * @param model Model
     * @param parent Parent page id
     * @param singlePage Single page id
     * @return sidebar template
     */
    @GetMapping("/ui/sidebar")
    public String index(
            Model model,
            @RequestParam(name = "parent", required = false) Integer parent,
            @RequestParam(name = "singlePage", required = false) Integer singlePage,
            @RequestParam(name = "after", defaultValue = "0") int after
    ) {
        if(parent == null)
            parent = 0;

        RequestValidation.require(parent >= 0 && after >= 0);
        RequestValidation.id(singlePage);
        RequestValidation.require(singlePage == null || (parent == 0 && after == 0));
        Integer currentUserId = getCurrentUserId();
        model.addAttribute("parentId", parent);
        model.addAttribute("after", after);
        model.addAttribute("pages", List.of());
        model.addAttribute("hasNext", false);
        if (singlePage != null) {
            model.addAttribute("singlePage", pageImpl.findNavigationPage(new PageId(singlePage), currentUserId).orElse(null));
        } else if (parent != 0) {
            Page parentPage = pageImpl.findNavigationPage(new PageId(parent), currentUserId).orElse(null);
            model.addAttribute("parentPage", parentPage);
            if (parentPage == null) return "fragments/sidebar-entries :: entries";
        }
        if (singlePage == null) {
            List<Page> visiblePages = pageImpl.listNavigationPages(parent, after, currentUserId);
            model.addAttribute("pages", visiblePages.stream().limit(50).toList());
            model.addAttribute("hasNext", visiblePages.size() > 50);
            if (visiblePages.size() > 50) model.addAttribute("nextAfter", visiblePages.get(49).getPageId().value());
        }

        // Provide the set of starred page ids so the sidebar can render the
        // filled star icon for already-starred entries.
        Set<Integer> starredIds = currentUserId == null
                ? Set.of()
                : pageStarDao.listStarredPageIds(currentUserId);
        model.addAttribute("starredIds", starredIds);
        model.addAttribute("canStar", currentUserId != null);

        return singlePage != null ? "sidebar :: single" : after > 0 ? "fragments/sidebar-entries :: entries" : "sidebar";
    }

    /**
     * Render the starred-pages section in the sidebar.
     * Empty for guests; security config already restricts this endpoint to authenticated users.
     */
    @GetMapping("/ui/sidebar/starred")
    public String starred(Model model) {
        Integer currentUserId = getCurrentUserId();
        List<Page> starred = currentUserId == null
                ? List.of()
                : pageStarDao.listStarredPages(currentUserId);
        model.addAttribute("starred", starred);
        return "fragments/sidebar-starred :: starred";
    }
}
