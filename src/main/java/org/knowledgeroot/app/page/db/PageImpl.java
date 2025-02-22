package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class PageImpl implements PageDao {
    private final JdbcClient jdbcClient;

    /**
     * find pages
     *
     * @param pageFilter
     * @return
     */
    @Override
    public List<Page> listPages(PageFilter pageFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    id as pageId,
                    parent,
                    name,
                    content,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    active,
                    deleted
                FROM
                    page      
        """);

        Map<String, Object> params = new HashMap<>();
        Map<String, String> sqlParams = new HashMap<>();

        if (pageFilter.getId() != null) {
            sqlParams.put("id", "id = :id");
            params.put("id", pageFilter.getId());
        }

        if (pageFilter.getParent() != null) {
            sqlParams.put("parent", "parent = :parent");
            params.put("parent", pageFilter.getParent());
        }

        if (pageFilter.getName() != null) {
            sqlParams.put("name", "name like :name");
            params.put("name", "%" + pageFilter.getName() + "%");
        }

        if (pageFilter.getContent() != null) {
            sqlParams.put("content", "content like :description");
            params.put("content", "%" + pageFilter.getContent() + "%");
        }

        if (pageFilter.getTimeStartBegin() != null) {
            sqlParams.put("time_start_begin", "time_start >= :time_start_begin");
            params.put("time_start_begin", pageFilter.getTimeStartBegin());
        }

        if (pageFilter.getTimeStartEnd() != null) {
            sqlParams.put("time_start_end", "time_start <= :time_start_end");
            params.put("time_start_end", pageFilter.getTimeStartEnd());
        }

        if (pageFilter.getTimeEndBegin() != null) {
            sqlParams.put("time_end_begin", "time_end >= :time_end_begin");
            params.put("time_end_begin", pageFilter.getTimeEndBegin());
        }

        if (pageFilter.getTimeEndEnd() != null) {
            sqlParams.put("time_end_end", "time_end <= :time_end_end");
            params.put("time_end_end", pageFilter.getTimeEndEnd());
        }

        if (pageFilter.getActive() != null) {
            sqlParams.put("active", "active = :active");
            params.put("active", pageFilter.getActive());
        }

        if (pageFilter.getCreatedBy() != null) {
            sqlParams.put("created_by", "created_by = :created_by");
            params.put("created_by", pageFilter.getCreatedBy());
        }

        if (pageFilter.getCreateDateBegin() != null) {
            sqlParams.put("create_date_begin", "create_date >= :create_date_begin");
            params.put("create_date_begin", pageFilter.getCreateDateBegin());
        }

        if (pageFilter.getCreateDateEnd() != null) {
            sqlParams.put("create_date_end", "create_date <= :create_date_end");
            params.put("create_date_end", pageFilter.getCreateDateEnd());
        }

        if (pageFilter.getChangedBy() != null) {
            sqlParams.put("changed_by", "changed_by = :changed_by");
            params.put("changed_by", pageFilter.getChangedBy());
        }

        if (pageFilter.getChangeDateBegin() != null) {
            sqlParams.put("change_date_begin", "change_date >= :change_date_begin");
            params.put("change_date_begin", pageFilter.getChangeDateBegin());
        }

        if (pageFilter.getChangeDateEnd() != null) {
            sqlParams.put("change_date_end", "change_date <= :change_date_end");
            params.put("change_date_end", pageFilter.getChangeDateEnd());
        }

        if (pageFilter.getDeleted() != null) {
            sqlParams.put("deleted", "deleted = :deleted");
            params.put("deleted", pageFilter.getDeleted());
        }

        if(!sqlParams.isEmpty()) {
            sql.append(" WHERE ");

            for(Map.Entry<String, String> entry : sqlParams.entrySet()) {
                sql.append(entry.getValue());

                if(!entry.equals(sqlParams.entrySet().toArray()[sqlParams.size() - 1]))
                    sql.append(" AND ");
            }
        }

        if(pageFilter.getLimit() != null && pageFilter.getStart() != null) {
            params.put("limit", pageFilter.getLimit());
            params.put("start", pageFilter.getStart());
            sql.append(" LIMIT :start, :limit");
        } else if(pageFilter.getLimit() != null) {
            params.put("limit", pageFilter.getLimit());
            sql.append(" LIMIT :limit");
        } else if(pageFilter.getStart() != null) {
            params.put("start", pageFilter.getStart());
            sql.append(" LIMIT :start, 18446744073709551615");
        }

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(Page.class)
                .list();
    }

    /**
     * find page by given id
     *
     * @param pageId
     * @return
     */
    @Override
    public Page findById(PageId pageId) {
        return jdbcClient.sql("""
                SELECT 
                    id as pageId,
                    parent,
                    name,
                    content,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    active,
                    deleted
                FROM 
                    page 
                WHERE 
                    id = :id
                """)
                .param("id", pageId.value())
                .query(Page.class)
                .single();
    }

    /**
     * create page from domain object
     *
     * @param page page to create
     */
    @Override
    public void createPage(Page page) {
        int update = jdbcClient.sql("""
                INSERT INTO page (
                    parent,
                    name,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    deleted
                ) VALUES (
                    ?,?,?,?,?,?,?,?,?,?,?
                )
                """)
                .params(
                        page.getParent(),
                        page.getName(),
                        page.getContent(),
                        page.getTimeStart(),
                        page.getTimeEnd(),
                        page.getActive(),
                        page.getCreatedBy(),
                        page.getCreateDate(),
                        page.getChangedBy(),
                        page.getChangeDate(),
                        page.getDeleted()
                )
                .update();

        Assert.state(update == 1, "Failed to create page: " + page.getName());
    }

    /**
     * update existing page
     *
     * @param page page to update
     */
    @Override
    public void updatePage(Page page) {
        int update = jdbcClient.sql("""
                UPDATE page SET
                    name = ?,
                    content = ?,
                    time_start = ?,
                    time_end = ?,
                    active = ?,
                    changed_by = ?,
                    change_date = ?,
                    deleted = ?
                WHERE
                    id = ?
                """)
                .params(
                        page.getName(),
                        page.getContent(),
                        page.getTimeStart(),
                        page.getTimeEnd(),
                        page.getActive(),
                        page.getChangedBy(),
                        page.getChangeDate(),
                        page.getDeleted(),
                        page.getPageId().value()
                )
                .update();

        Assert.state(update == 1, "Failed to update page: " + page.getName());
    }

    /**
     * delete all pages
     */
    @Override
    public void deleteAllPages() {
        jdbcClient.sql("delete from page").update();
    }

    /**
     * delete page by given id
     *
     * @param pageId page id
     */
    @Override
    public void deletePageById(PageId pageId) {
        jdbcClient.sql("delete from page where id = :id")
                .param("id", pageId.value())
                .update();
    }

    @Override
    public List<Page> searchContent(String searchQuery) {
        return jdbcClient.sql("""
                SELECT 
                    id as pageId,
                    parent,
                    name,
                    content,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    active,
                    deleted
                FROM 
                    page 
                WHERE 
                    content like :searchQuery
                """)
                .param("searchQuery", "%" + searchQuery + "%")
                .query(Page.class)
                .list();
    }
}
