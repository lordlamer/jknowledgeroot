package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.beans.factory.annotation.Value;

@ControllerAdvice
@RequiredArgsConstructor
public class LayoutDefines {
    private final UserContext userContext;
    /** Absent in web slice tests without the space beans; then nobody may create root pages. */
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.space.SpaceService> spaces;

    /** Root pages need the edit level on the space; until the UI selects a space, the default space decides. */
    @ModelAttribute("canCreateRootPage")
    public boolean canCreateRootPage() {
        var service = spaces.getIfAvailable();
        if (service == null) return false;
        var space = service.defaultSpace();
        return space != null && service.canCreateRootPage(space.getId());
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
