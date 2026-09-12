package org.knowledgeroot.app.security.auth;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Minimal endpoints to observe the real security chain and application user context. */
@RestController
@RequiredArgsConstructor
class SessionTestController {
    private final UserContext userContext;

    @GetMapping({"/profile/session-check", "/admin/session-check", "/ui/page/session-check"})
    Map<String, String> currentUser() {
        UserDetails user = userContext.getUserContext();
        return Map.of("id", user.getUserId(), "login", user.getLogin(), "role", user.getRole().name());
    }
}
