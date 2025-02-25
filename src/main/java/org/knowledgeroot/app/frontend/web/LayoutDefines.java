package org.knowledgeroot.app.frontend.web;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class LayoutDefines {
    private final UserContext userContext;

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
