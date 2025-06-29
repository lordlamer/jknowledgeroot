package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
class SidebarController {
    private final PageDao pageImpl;
    private final PagePermissionDao pagePermissionImpl;
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

        List<Page> allPages = pageImpl.listPages(pageFilter);

        // Filter pages based on user permissions - only show pages the user can view
        Integer currentUserId = getCurrentUserId();
        List<Page> visiblePages = allPages.stream()
                .filter(page -> pagePermissionImpl.hasUserPermission(
                        page.getPageId(), 
                        currentUserId, 
                        PagePermission.PermissionLevel.VIEW))
                .collect(Collectors.toList());

        model.addAttribute("parentId", parent);

        if(parent == 0) {
            model.addAttribute("parentPage", null);
            if(singlePage != null) {
                Page singlePageObj = pageImpl.findById(new PageId(singlePage));
                // Check if user can view the single page
                if (pagePermissionImpl.hasUserPermission(new PageId(singlePage), currentUserId, PagePermission.PermissionLevel.VIEW)) {
                    model.addAttribute("singlePage", singlePageObj);
                } else {
                    model.addAttribute("singlePage", null);
                }
            } else {
                model.addAttribute("singlePage", null);
            }
        } else {
            Page parentPageObj = pageImpl.findById(new PageId(parent));
            // Check if user can view the parent page
            if (pagePermissionImpl.hasUserPermission(new PageId(parent), currentUserId, PagePermission.PermissionLevel.VIEW)) {
                model.addAttribute("parentPage", parentPageObj);
            } else {
                model.addAttribute("parentPage", null);
            }
        }

        model.addAttribute("pages", visiblePages);

        return "sidebar";
    }
}
