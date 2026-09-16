package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.page.domain.PageDao;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SidebarNavigationIntegrationTest extends IsolatedApplicationTest {
    @Autowired PageDao pages;

    @Test void permissionsPrecedePaginationAndDeletedEarlierRowsDoNotSkipLaterChildren() throws Exception {
        grant(pageId, "guest", null, "view");
        for (int i = 0; i < 60; i++) child("hidden-" + pageId + "-" + i, false);
        var visible = new ArrayList<Integer>();
        for (int i = 0; i < 55; i++) visible.add(child("visible-" + pageId + "-" + i, true));
        var first = mvc.perform(get("/ui/sidebar").param("parent", "" + pageId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var ids = ids(first);
        assertEquals(pageId, ids.removeFirst());
        assertEquals(visible.subList(0, 50), ids);
        assertFalse(first.contains("hidden-"));
        assertTrue(first.contains("after=" + visible.get(49)));
        var metadata = pages.listNavigationPages(pageId, 0, null);
        assertEquals(51, metadata.size());
        assertNull(metadata.getFirst().getContent());
        assertNull(metadata.getFirst().getFiles());
        jdbc.update("UPDATE page SET deleted=TRUE WHERE id=?", visible.getFirst());
        var second = mvc.perform(get("/ui/sidebar").param("parent", "" + pageId).param("after", "" + visible.get(49)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(visible.subList(50, 55), ids(second));
        assertFalse(second.contains("Load more pages"));
        assertFalse(second.contains("<ul"));
        jdbc.update("DELETE FROM page_permission WHERE page_id=? AND role_type='guest'", pageId);
        assertTrue(ids(mvc.perform(get("/ui/sidebar").param("parent", "" + pageId).param("after", "" + visible.get(49)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEmpty());
    }

    @Test void groupRevocationAndGuessedCollapseIdsDoNotExposePrivateMetadata() throws Exception {
        jdbc.update("INSERT INTO `group` (name,active,created_by,create_date,changed_by,change_date) VALUES (?,TRUE,?,NOW(),?,NOW())", "nav-" + pageId, adminId, adminId);
        int group = jdbc.queryForObject("SELECT id FROM `group` WHERE name=?", Integer.class, "nav-" + pageId);
        jdbc.update("INSERT INTO group_member (group_id,member_id,member_type) VALUES (?,?,'user')", group, outsiderId);
        grant(pageId, "group", group, "view");
        int child = child("group-child-" + pageId, true);
        var member = login(outsider);
        assertEquals(List.of(pageId, child), ids(member.perform(get("/ui/sidebar").param("parent", "" + pageId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        jdbc.update("DELETE FROM group_member WHERE group_id=?", group);
        assertTrue(ids(member.perform(get("/ui/sidebar").param("parent", "" + pageId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEmpty());
        var denied = mvc.perform(get("/ui/sidebar").param("singlePage", "" + child)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(denied.contains("group-child-"));
        assertTrue(ids(denied).isEmpty());
        var allowed = login(reader).perform(get("/ui/sidebar").param("singlePage", "" + child))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(child), ids(allowed));
        assertFalse(allowed.contains("sidebar-tree"));
    }

    @Test void invalidNavigationParametersAreRejected() throws Exception {
        for (var query : List.of("parent=-1", "after=-1", "singlePage=0", "parent=1&singlePage=2", "singlePage=2&after=1", "after=2147483648")) {
            mvc.perform(get("/ui/sidebar?" + query)).andExpect(status().isBadRequest());
        }
    }

    private int child(String name, boolean inherit) {
        int id = page(name);
        jdbc.update("UPDATE page SET parent=?,inherit_permissions=? WHERE id=?", pageId, inherit, id);
        return id;
    }
    private List<Integer> ids(String html) {
        return new ArrayList<>(Pattern.compile("id=\"nav-page-(\\d+)\"").matcher(html).results()
                .map(match -> Integer.valueOf(match.group(1))).toList());
    }
}
