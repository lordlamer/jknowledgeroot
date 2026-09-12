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
    @Value("${knowledgeroot.pages.allow-guest-root-creation:false}")
    private boolean allowGuestRootCreation;

    @ModelAttribute("canCreateRootPage")
    public boolean canCreateRootPage() {
        return !userContext.getUserContext().isGuest() || allowGuestRootCreation;
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
