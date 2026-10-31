package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.frontend.web.SearchController;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Page tree, favourites, search and the administrators' list of deleted pages. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class NavigationApiController {
    private static final int TREE_PAGE_SIZE = 50;
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_PATH_DEPTH = 12;
    private final ApiSupport api;
    private final PageDao pages;
    private final PageStarDao stars;
    private final org.knowledgeroot.app.space.CurrentSpace currentSpace;

    /**
     * Visible children of a parent (0 = top level of {@code space}, default: the session's space),
     * 50 per request; continue with {@code after=nextAfter}.
     */
    @GetMapping("/tree")
    ApiModels.TreeResponse tree(@RequestParam(defaultValue = "0") int parent, @RequestParam(defaultValue = "0") int after,
                                @RequestParam(required = false) Integer space) {
        RequestValidation.require(parent >= 0 && after >= 0 && (space == null || space > 0));
        int spaceId = space != null ? space : currentSpace.currentId();
        Integer viewer = api.viewer();
        ApiModels.PageRef parentRef = null;
        if (parent > 0) {
            var parentPage = pages.findNavigationPage(new PageId(parent), viewer);
            if (parentPage.isEmpty()) return new ApiModels.TreeResponse(null, List.of(), null);
            parentRef = ApiModels.PageRef.of(parentPage.get());
        }
        List<Page> visible = pages.listNavigationPages(spaceId, parent, after, viewer);
        List<Page> items = visible.stream().limit(TREE_PAGE_SIZE).toList();
        Set<Integer> withChildren = pages.parentsWithVisibleChildren(
                items.stream().map(page -> page.getPageId().value()).toList(), viewer);
        Set<Integer> starred = viewer == null ? Set.of() : stars.listStarredPageIds(viewer);
        Integer nextAfter = visible.size() > TREE_PAGE_SIZE ? items.getLast().getPageId().value() : null;
        return new ApiModels.TreeResponse(parentRef, items.stream().map(page -> {
            int id = page.getPageId().value();
            return new ApiModels.TreeNode(id, page.getName(), page.getParent(), page.getRevision(),
                    withChildren.contains(id), starred.contains(id));
        }).toList(), nextAfter);
    }

    /** Favourites of the signed-in user, most recently starred first, optionally only those in {@code space}; empty for guests. */
    @GetMapping("/starred")
    List<ApiModels.PageRef> starred(@RequestParam(required = false) Integer space) {
        Integer viewer = api.viewer();
        if (viewer == null) return List.of();
        return stars.listStarredPages(viewer).stream()
                .filter(page -> space == null || (page.getSpaceId() != null && page.getSpaceId().equals(space)))
                .filter(page -> api.can(page.getPageId().value(), PagePermission.PermissionLevel.VIEW))
                .map(ApiModels.PageRef::of).toList();
    }

    /** Searches the spaces the viewer may enter, or only {@code space} when given. */
    @GetMapping("/search")
    ApiModels.SearchResponse search(@RequestParam("q") String query, @RequestParam(defaultValue = "0") int start,
                                    @RequestParam(required = false) Integer space) {
        RequestValidation.text(query, 200);
        int offset = RequestValidation.start(start);
        RequestValidation.require(space == null || space > 0);
        String term = query.trim();
        Integer viewer = api.viewer();
        List<Page> results = term.isEmpty() ? List.of() : pages.listVisiblePages(
                PageFilter.builder().query(term).spaceId(space).start(offset).limit(SEARCH_PAGE_SIZE + 1).build(), viewer);
        // Ancestors are shared between hits, so resolve each visible parent only once per request.
        Map<Integer, Optional<Page>> parents = new HashMap<>();
        var hits = results.stream().limit(SEARCH_PAGE_SIZE).map(page -> new ApiModels.SearchHit(
                page.getPageId().value(), page.getName(), SearchController.snippet(page.getContent(), term),
                path(page.getParent(), viewer, parents), page.getChangeDate(), api.spaceRef(page.getSpaceId()))).toList();
        return new ApiModels.SearchResponse(term, hits, offset,
                results.size() > SEARCH_PAGE_SIZE && offset + SEARCH_PAGE_SIZE <= 100_000);
    }

    @GetMapping("/pages/deleted")
    ApiModels.DeletedResponse deleted(@RequestParam(defaultValue = "0") int start) {
        api.requireAdmin();
        int offset = RequestValidation.start(start);
        var filter = new PageFilter();
        filter.setDeleted(true);
        filter.setStart(offset);
        filter.setLimit(51);
        var items = pages.listPages(filter);
        return new ApiModels.DeletedResponse(items.stream().limit(50).map(page -> new ApiModels.DeletedPage(
                page.getPageId().value(), page.getName(), page.getChangeDate(), page.getChangedByLogin())).toList(),
                offset, items.size() > 50);
    }

    /** Root-to-parent names limited to pages the viewer may see; stops at the first hidden ancestor. */
    private List<String> path(Integer parent, Integer viewer, Map<Integer, Optional<Page>> cache) {
        var names = new ArrayList<String>();
        Integer current = parent;
        while (current != null && current > 0 && names.size() < MAX_PATH_DEPTH) {
            var ancestor = cache.computeIfAbsent(current, key -> pages.findNavigationPage(new PageId(key), viewer));
            if (ancestor.isEmpty()) break;
            names.addFirst(ancestor.get().getName());
            current = ancestor.get().getParent();
        }
        return names;
    }
}
