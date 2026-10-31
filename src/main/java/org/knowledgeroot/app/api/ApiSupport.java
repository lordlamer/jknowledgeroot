package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Identity and permission helpers shared by the client API controllers. */
@Component
@RequiredArgsConstructor
class ApiSupport {
    private final UserContext userContext;
    private final PagePermissionDao permissions;
    private final PageDao pages;
    private final SpaceService spaces;

    UserDetails user() {
        return userContext.getUserContext();
    }

    /** Database ID of the signed-in account, or null for guests. */
    Integer viewer() {
        UserDetails user = user();
        if (user.isGuest()) return null;
        try {
            return Integer.valueOf(user.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    int requireUser() {
        Integer id = viewer();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        return id;
    }

    boolean admin() {
        return user().isAdmin();
    }

    void requireAdmin() {
        requireUser();
        if (!admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator rights required.");
    }

    boolean can(int pageId, PagePermission.PermissionLevel level) {
        return pageId > 0 && permissions.hasUserPermission(new PageId(pageId), viewer(), level);
    }

    void require(int pageId, PagePermission.PermissionLevel level) {
        if (!can(pageId, level)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page.");
    }

    Page page(int pageId) {
        try {
            Page page = pages.findById(new PageId(pageId));
            if (page == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
            return page;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        }
    }

    SpaceService spaces() {
        return spaces;
    }

    ApiModels.SpaceRef spaceRef(Integer spaceId) {
        if (spaceId == null) return null;
        return spaces.spaces().find(spaceId).map(space -> new ApiModels.SpaceRef(space.getId(), space.getKey(), space.getName())).orElse(null);
    }

    /** Root-to-page path limited to pages the viewer may see. */
    List<ApiModels.PageRef> path(int pageId) {
        if (pageId <= 0) return List.of();
        return pages.getPageHierarchy(new PageId(pageId)).stream()
                .filter(ancestor -> can(ancestor.getPageId().value(), PagePermission.PermissionLevel.VIEW))
                .map(ApiModels.PageRef::of).toList();
    }
}
