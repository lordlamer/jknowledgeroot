package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.beans.factory.annotation.Value;

/**
 * Model attributes for the HTML layout. Limited to the Thymeleaf controllers so that the
 * actuator endpoints and the JSON APIs never pay for (or fail on) the space lookups.
 */
@ControllerAdvice(basePackages = {"org.knowledgeroot.app.frontend.web", "org.knowledgeroot.app.page.ui",
        "org.knowledgeroot.app.space", "org.knowledgeroot.app.security.user.web", "org.knowledgeroot.app.profile.web",
        "org.knowledgeroot.app.security.mfa", "org.knowledgeroot.app.login", "org.knowledgeroot.app.security.recovery",
        "org.knowledgeroot.app.file.web"})
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class LayoutDefines {
    private final UserContext userContext;
    /** Absent in web slice tests without the space beans; then nobody may create root pages. */
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.space.SpaceService> spaces;

    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.space.CurrentSpace> currentSpace;

    /** Root pages need the edit level on the space the sidebar currently shows. */
    @ModelAttribute("canCreateRootPage")
    public boolean canCreateRootPage() {
        var service = spaces.getIfAvailable();
        var current = currentSpace.getIfAvailable();
        if (service == null || current == null) return false;
        try {
            return current.current().map(space -> service.canCreateRootPage(space.getId())).orElse(false);
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Space lookup for the layout failed: {}", e.getMessage());
            return false;
        }
    }

    @ModelAttribute("currentSpace")
    public org.knowledgeroot.app.space.Space currentSpace() {
        var current = currentSpace.getIfAvailable();
        if (current == null) return null;
        try {
            return current.current().orElse(null);
        } catch (org.springframework.dao.DataAccessException e) {
            return null;
        }
    }

    /**
     * Called when a handler moved the sidebar into another space after the layout attributes were
     * computed: refreshes them and, for an htmx request, lets the page carry the switcher and the
     * sidebar head out of band and reload the tree and the favourites.
     */
    public void spaceChanged(org.springframework.ui.Model model, jakarta.servlet.http.HttpServletResponse response, boolean htmx) {
        model.addAttribute("currentSpace", currentSpace());
        model.addAttribute("canCreateRootPage", canCreateRootPage());
        if (htmx) {
            model.addAttribute("spaceChanged", true);
            response.addHeader("HX-Trigger", "reload-sidebar, reload-stars");
        }
    }

    @ModelAttribute("visibleSpaces")
    public java.util.List<org.knowledgeroot.app.space.Space> visibleSpaces() {
        var service = spaces.getIfAvailable();
        if (service == null) return java.util.List.of();
        try {
            return service.visibleSpaces();
        } catch (org.springframework.dao.DataAccessException e) {
            return java.util.List.of();
        }
    }

    @ModelAttribute("isguest")
    public boolean isGuest() {
        return userContext.getUserContext().isGuest();
    }

    @ModelAttribute("isadmin")
    public boolean isAdmin() {
        return userContext.getUserContext().isAdmin();
    }

    @ModelAttribute("isuser")
    public boolean isUser() {
        return userContext.getUserContext().isUser();
    }
}
