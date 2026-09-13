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

    public void requireAdministrator() {
        if (!users.getUserContext().isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public Page movablePage(PageId id) {
        requireAdministrator();
        var page = pages.findById(id);
        if (Boolean.TRUE.equals(page.getDeleted())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore the page before moving it.");
        return page;
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void move(PageId id, int parent, Long revision) {
        requireAdministrator();
        if (parent < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // Only structural moves take this mutex. Ordinary edits keep their row-level locks.
        jdbc.sql("SELECT id FROM page_tree_lock WHERE id=1 FOR UPDATE").query(Integer.class).single();
        long current = jdbc.sql("SELECT revision FROM page WHERE id=? FOR UPDATE").param(id.value()).query(Long.class).single();
        var page = movablePage(id);
        page.setRevision(current);
        PageEditingService.requireRevision(page, revision);
        if (java.util.Objects.equals(page.getParent(), parent)) return;
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
        var subtree = jdbc.sql("""
                WITH RECURSIVE subtree AS (
                    SELECT id FROM page WHERE id=:id
                    UNION DISTINCT SELECT p.id FROM page p JOIN subtree s ON p.parent=s.id
                ) SELECT id FROM subtree WHERE id<>:id
                """).param("id", id.value()).query(Integer.class).list();
        // Record the original content, location and labels, then invalidate old edit forms.
        page.setParent(parent);
        page.setChangedBy(Integer.valueOf(users.getUserContext().getUserId()));
        page.setChangeDate(LocalDateTime.now());
        pages.updatePage(page);
        if (parent == 0 && permissions.isInheriting(id)) permissions.setInheriting(id, false, page.getChangedBy());
        jdbc.sql("UPDATE page SET parent=? WHERE id=?").params(parent, id.value()).update();
        if (!subtree.isEmpty()) jdbc.sql("UPDATE page SET revision=revision+1 WHERE id IN (:ids)").param("ids", subtree).update();
    }

    private record Parent(int parent, boolean deleted) { }
}
