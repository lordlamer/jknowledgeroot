package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PageStarImpl implements PageStarDao {
    private final JdbcClient jdbcClient;
    private final PagePermissionDao pagePermissionDao;

    @Override
    public void starPage(Integer userId, PageId pageId) {
        // INSERT IGNORE keeps the call idempotent — the (user_id, page_id) PK prevents duplicates.
        jdbcClient.sql("""
                INSERT IGNORE INTO user_page_star (user_id, page_id, create_date)
                VALUES (:userId, :pageId, :now)
                """)
                .param("userId", userId)
                .param("pageId", pageId.value())
                .param("now", LocalDateTime.now())
                .update();
    }

    @Override
    public void unstarPage(Integer userId, PageId pageId) {
        jdbcClient.sql("""
                DELETE FROM user_page_star
                WHERE user_id = :userId AND page_id = :pageId
                """)
                .param("userId", userId)
                .param("pageId", pageId.value())
                .update();
    }

    @Override
    public boolean isStarred(Integer userId, PageId pageId) {
        if (userId == null) {
            return false;
        }
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM user_page_star
                WHERE user_id = :userId AND page_id = :pageId
                """)
                .param("userId", userId)
                .param("pageId", pageId.value())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<Page> listStarredPages(Integer userId) {
        if (userId == null) {
            return List.of();
        }
        List<Page> pages = jdbcClient.sql("""
                SELECT p.id AS pageId, p.name
                FROM user_page_star s
                JOIN page p ON p.id = s.page_id
                WHERE s.user_id = :userId
                  AND p.deleted = 0
                ORDER BY s.create_date DESC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> Page.builder()
                        .pageId(new PageId(rs.getInt("pageId")))
                        .name(rs.getString("name"))
                        .build())
                .list();

        // Filter to only pages the user can still view (permission may have been revoked).
        return pages.stream()
                .filter(p -> pagePermissionDao.hasUserPermission(p.getPageId(), userId, PagePermission.PermissionLevel.VIEW))
                .collect(Collectors.toList());
    }
}
