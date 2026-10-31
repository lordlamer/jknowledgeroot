package org.knowledgeroot.app.page.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class PageMoveService {
    private final JdbcClient jdbc;
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final UserContext users;
    private final org.knowledgeroot.app.space.SpaceService spaces;

    public int actor() {
        var user = users.getUserContext();
        if (user.isGuest()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Integer.parseInt(user.getUserId());
    }

    public boolean canUseDestination(int parent) {
        return parent == 0 || permissions.hasUserPermission(new PageId(parent), actor(), PagePermission.PermissionLevel.EDIT);
    }

    /** The top level of a space is a destination for whoever may create root pages there. */
    public boolean canUseTopLevel(int spaceId) {
        actor();
        return spaces.canCreateRootPage(spaceId);
    }

    public Page movablePage(PageId id) {
        if (!permissions.hasUserPermission(id, actor(), PagePermission.PermissionLevel.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var page = pages.findById(id);
        if (Boolean.TRUE.equals(page.getDeleted())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore the page before moving it.");
        return page;
    }

    public void move(PageId id, int parent, Long revision) {
        move(id, parent, revision, null);
    }

    /** @param space the space whose top level receives the page when {@code parent} is 0; null keeps the page's space */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void move(PageId id, int parent, Long revision, Integer space) {
        movablePage(id);
        if (parent < 0 || (space != null && space <= 0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // Only structural moves take this mutex. Ordinary edits keep their row-level locks.
        jdbc.sql("SELECT id FROM page_tree_lock WHERE id=1 FOR UPDATE").query(Integer.class).single();
        long current = jdbc.sql("SELECT revision FROM page WHERE id=? FOR UPDATE").param(id.value()).query(Long.class).single();
        var page = movablePage(id);
        page.setRevision(current);
        PageEditingService.requireRevision(page, revision);
        Integer topLevelSpace = parent == 0 ? (space != null ? space : page.getSpaceId()) : null;
        if (java.util.Objects.equals(page.getParent(), parent) && (parent > 0 || topLevelSpace.equals(page.getSpaceId()))) {
            if (!canUseDestination(parent)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return;
        }
        if (parent == 0 && !topLevelSpace.equals(page.getSpaceId()) && !canUseTopLevel(topLevelSpace))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var visited = new HashSet<Integer>();
        int ancestor = parent;
        while (ancestor > 0) {
            if (ancestor == id.value() || !visited.add(ancestor))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A page cannot be moved into its own subtree.");
            var node = jdbc.sql("SELECT parent, deleted FROM page WHERE id=? FOR UPDATE").param(ancestor)
                    .query((rs, row) -> new Parent(rs.getInt("parent"), rs.getBoolean("deleted"))).optional();
            if (node.isEmpty() || node.get().deleted())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The destination is no longer available.");
            ancestor = node.get().parent();
        }
        // Recheck current source and destination grants after acquiring the tree/row locks.
        movablePage(id);
        if (!canUseDestination(parent)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var subtree = jdbc.sql("""
                WITH RECURSIVE subtree AS (
                    SELECT id FROM page WHERE id=:id
                    UNION DISTINCT SELECT p.id FROM page p JOIN subtree s ON p.parent=s.id
                ) SELECT id FROM subtree WHERE id<>:id
                """).param("id", id.value()).query(Integer.class).list();
        // A page moved below a parent in another space takes that space along with its subtree.
        Integer targetSpace = parent > 0
                ? jdbc.sql("SELECT space_id FROM page WHERE id=?").param(parent).query(Integer.class).single()
                : topLevelSpace;
        // Record the original content, location and labels, then invalidate old edit forms.
        page.setParent(parent);
        page.setChangedBy(Integer.valueOf(users.getUserContext().getUserId()));
        page.setChangeDate(LocalDateTime.now());
        pages.updatePage(page);
        // A page moved to the top level keeps the grants it had instead of silently taking the space grants.
        if (parent == 0 && permissions.isInheriting(id)) permissions.setInheriting(id, false, page.getChangedBy());
        jdbc.sql("UPDATE page SET parent=?, space_id=? WHERE id=?").params(parent, targetSpace, id.value()).update();
        if (!subtree.isEmpty()) jdbc.sql("UPDATE page SET revision=revision+1, space_id=:space WHERE id IN (:ids)")
                .param("space", targetSpace).param("ids", subtree).update();
    }

    private record Parent(int parent, boolean deleted) { }
}
