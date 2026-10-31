package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.space.CurrentSpace;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Spaces for app clients: the ones the viewer may enter, their details and root pages. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SpaceApiController {
    private final ApiSupport api;
    private final SpaceService spaces;
    private final CurrentSpace currentSpace;
    private final org.knowledgeroot.app.page.domain.PageDao pages;

    /** Spaces the viewer may enter; {@code current} marks the one the session last opened. */
    @GetMapping("/spaces")
    List<ApiModels.SpaceInfo> list() {
        int current = currentSpace.currentId();
        return spaces.visibleSpaces().stream().map(space -> info(space, space.getId() == current)).toList();
    }

    @GetMapping("/spaces/{id}")
    ApiModels.SpaceDetail detail(@PathVariable int id) {
        spaces.requireView(id);
        Space space = spaces.space(id);
        var roots = new PageFilter();
        roots.setSpaceId(id);
        roots.setParent(0);
        roots.setDeleted(false);
        roots.setLimit(101);
        var rootPages = pages.listVisiblePages(roots, api.viewer()).stream().limit(100).map(ApiModels.PageRef::of).toList();
        return new ApiModels.SpaceDetail(info(space, currentSpace.currentId() == id), rootPages);
    }

    /** Remembers the space for the session, like the switcher in the web sidebar. */
    @PostMapping("/spaces/{id}/open")
    ApiModels.SpaceInfo open(@PathVariable int id) {
        spaces.requireView(id);
        currentSpace.switchTo(id);
        return info(spaces.space(id), true);
    }

    private ApiModels.SpaceInfo info(Space space, boolean current) {
        if (space == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found.");
        return new ApiModels.SpaceInfo(space.getId(), space.getKey(), space.getName(), space.getDescription(),
                space.getHomePageId(), space.isArchived(), spaces.canCreateRootPage(space.getId()), spaces.canManage(space.getId()), current);
    }
}
