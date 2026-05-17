package org.knowledgeroot.app.page.ui;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class PageStarController {
    private final PageStarDao pageStarDao;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;

    @PostMapping("/ui/page/{pageId}/star")
    public String starPage(
            @PathVariable("pageId") Integer pageId,
            Model model,
            HttpServletResponse response
    ) {
        Integer userId = requireUserId();
        PageId pid = new PageId(pageId);

        if (!pagePermissionDao.hasUserPermission(pid, userId, PagePermission.PermissionLevel.VIEW)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        pageStarDao.starPage(userId, pid);

        model.addAttribute("pageId", pageId);
        model.addAttribute("starred", true);
        response.addHeader("HX-Trigger", "reload-stars");
        return "page/star-button :: button";
    }

    @DeleteMapping("/ui/page/{pageId}/star")
    public String unstarPage(
            @PathVariable("pageId") Integer pageId,
            Model model,
            HttpServletResponse response
    ) {
        Integer userId = requireUserId();
        PageId pid = new PageId(pageId);

        pageStarDao.unstarPage(userId, pid);

        model.addAttribute("pageId", pageId);
        model.addAttribute("starred", false);
        response.addHeader("HX-Trigger", "reload-stars");
        return "page/star-button :: button";
    }

    private Integer requireUserId() {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return Integer.valueOf(user.getUserId());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
