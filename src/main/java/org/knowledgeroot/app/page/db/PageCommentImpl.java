package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageComment;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class PageCommentImpl implements PageCommentDao {
    private final JdbcClient jdbcClient;

    @Override
    public List<PageComment> listForPage(PageId pageId) {
        return jdbcClient.sql("""
                SELECT
                    c.id,
                    c.page_id,
                    c.user_id,
                    c.content,
                    c.create_date,
                    u.login        AS author_login,
                    u.first_name   AS author_first_name,
                    u.last_name    AS author_last_name
                FROM page_comment c
                JOIN `user` u ON u.id = c.user_id
                WHERE c.page_id = :pageId
                ORDER BY c.create_date ASC, c.id ASC
                """)
                .param("pageId", pageId.value())
                .query((rs, rowNum) -> PageComment.builder()
                        .id(rs.getInt("id"))
                        .pageId(new PageId(rs.getInt("page_id")))
                        .userId(rs.getInt("user_id"))
                        .content(rs.getString("content"))
                        .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                        .authorLogin(rs.getString("author_login"))
                        .authorFirstName(rs.getString("author_first_name"))
                        .authorLastName(rs.getString("author_last_name"))
                        .build())
                .list();
    }

    @Override
    public int countForPage(PageId pageId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM page_comment WHERE page_id = :pageId
                """)
                .param("pageId", pageId.value())
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    public Integer create(PageId pageId, Integer userId, String content) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO page_comment (page_id, user_id, content, create_date)
                VALUES (:pageId, :userId, :content, :now)
                """)
                .param("pageId", pageId.value())
                .param("userId", userId)
                .param("content", content)
                .param("now", LocalDateTime.now())
                .update(kh);
        Number key = kh.getKey();
        return key == null ? null : key.intValue();
    }

    @Override
    public Optional<PageComment> findById(Integer commentId) {
        return jdbcClient.sql("""
                SELECT
                    c.id,
                    c.page_id,
                    c.user_id,
                    c.content,
                    c.create_date,
                    u.login        AS author_login,
                    u.first_name   AS author_first_name,
                    u.last_name    AS author_last_name
                FROM page_comment c
                JOIN `user` u ON u.id = c.user_id
                WHERE c.id = :id
                """)
                .param("id", commentId)
                .query((rs, rowNum) -> PageComment.builder()
                        .id(rs.getInt("id"))
                        .pageId(new PageId(rs.getInt("page_id")))
                        .userId(rs.getInt("user_id"))
                        .content(rs.getString("content"))
                        .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                        .authorLogin(rs.getString("author_login"))
                        .authorFirstName(rs.getString("author_first_name"))
                        .authorLastName(rs.getString("author_last_name"))
                        .build())
                .optional();
    }

    @Override
    public void delete(Integer commentId) {
        jdbcClient.sql("DELETE FROM page_comment WHERE id = :id")
                .param("id", commentId)
                .update();
    }
}
