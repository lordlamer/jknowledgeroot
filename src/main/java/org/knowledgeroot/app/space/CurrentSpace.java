package org.knowledgeroot.app.space;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

/**
 * The space the sidebar shows: remembered in the session, set when a viewer switches spaces or
 * opens a page, and falling back to the default space or the first space the viewer may enter.
 */
@Component
@RequiredArgsConstructor
public class CurrentSpace {
    static final String ATTRIBUTE = "knowledgeroot.currentSpace";
    private final SpaceService spaces;

    private static Optional<HttpSession> session(boolean create) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return Optional.empty();
        return Optional.ofNullable(attributes.getRequest().getSession(create));
    }

    /** The current space when the viewer may still enter it, otherwise the best available space. */
    public Optional<Space> current() {
        List<Space> visible = spaces.visibleSpaces();
        Integer remembered = session(false).map(s -> (Integer) s.getAttribute(ATTRIBUTE)).orElse(null);
        if (remembered != null) {
            var match = visible.stream().filter(space -> space.getId().equals(remembered)).findFirst();
            if (match.isPresent()) return match;
        }
        Space fallback = spaces.defaultSpace();
        return visible.stream().filter(space -> space.getId().equals(fallback.getId())).findFirst()
                .or(() -> visible.stream().filter(space -> !space.isArchived()).findFirst())
                .or(() -> visible.stream().findFirst());
    }

    /** The current space's id, or the default space when the viewer may enter nothing. */
    public int currentId() {
        return current().map(Space::getId).orElseGet(() -> spaces.defaultSpace().getId());
    }

    public void switchTo(int spaceId) {
        session(true).ifPresent(s -> s.setAttribute(ATTRIBUTE, spaceId));
    }

    /** Opening a page moves the sidebar to the page's space. */
    public void follow(Integer spaceId) {
        if (spaceId != null) switchTo(spaceId);
    }
}
