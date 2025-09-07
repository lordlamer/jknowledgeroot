package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class PageImpl implements PageDao {
    private final FileDao fileImpl;
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

        List<Page> pages = jdbcClient.sql(sql.toString())
                .params(params)
                .query(
                        (rs, rowNum) -> {
                            // Sichere Umwandlung von Timestamp zu LocalDateTime mit Null-Prüfung
                            LocalDateTime timeStart = null;
                            LocalDateTime timeEnd = null;
                            LocalDateTime createDate = null;
                            LocalDateTime changeDate = null;

                            Timestamp timestampTimeStart = rs.getTimestamp("time_start");
                            if (timestampTimeStart != null) {
                                timeStart = timestampTimeStart.toLocalDateTime();
                            }

                            Timestamp timestampTimeEnd = rs.getTimestamp("time_end");
                            if (timestampTimeEnd != null) {
                                timeEnd = timestampTimeEnd.toLocalDateTime();
                            }

                            Timestamp timestampCreateDate = rs.getTimestamp("create_date");
                            if (timestampCreateDate != null) {
                                createDate = timestampCreateDate.toLocalDateTime();
                            }

                            Timestamp timestampChangeDate = rs.getTimestamp("change_date");
                            if (timestampChangeDate != null) {
                                changeDate = timestampChangeDate.toLocalDateTime();
                            }

                            return Page.builder()
                                    .pageId(new PageId(rs.getInt("pageId")))
                                    .parent(rs.getInt("parent"))
                                    .name(rs.getString("name"))
                                    .content(rs.getString("content"))
                                    .timeStart(timeStart)
                                    .timeEnd(timeEnd)
                                    .active(rs.getBoolean("active"))
                                    .createdBy(rs.getObject("created_by") != null ? rs.getInt("created_by") : null)
                                    .createDate(createDate)
                                    .changedBy(rs.getObject("changed_by") != null ? rs.getInt("changed_by") : null)
                                    .changeDate(changeDate)
                                    .active(rs.getBoolean("active"))
                                    .deleted(rs.getBoolean("deleted"))
                                    .build();
                        }
                )
                .list();

        // add files to pages
        addFilesToPages(pages);

        return pages;
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
                    p.id as pageId,
                    p.parent,
                    p.name,
                    p.content,
                    p.time_start,
                    p.time_end,
                    p.active,
                    p.created_by,
                    cu.login as created_by_login,
                    p.create_date,
                    p.changed_by,
                    uu.login as changed_by_login,
                    p.change_date,
                    p.active,
                    p.deleted
                FROM 
                    page p
                LEFT JOIN user cu ON cu.id = p.created_by
                LEFT JOIN user uu ON uu.id = p.changed_by
                WHERE 
                    p.id = :id
                """)
                .param("id", pageId.value())
                .query(
                        (rs, rowNum) -> {
                            // Sichere Umwandlung von Timestamp zu LocalDateTime mit Null-Prüfung
                            LocalDateTime timeStart = null;
                            LocalDateTime timeEnd = null;
                            LocalDateTime createDate = null;
                            LocalDateTime changeDate = null;

                            Timestamp timestampTimeStart = rs.getTimestamp("time_start");
                            if (timestampTimeStart != null) {
                                timeStart = timestampTimeStart.toLocalDateTime();
                            }

                            Timestamp timestampTimeEnd = rs.getTimestamp("time_end");
                            if (timestampTimeEnd != null) {
                                timeEnd = timestampTimeEnd.toLocalDateTime();
                            }

                            Timestamp timestampCreateDate = rs.getTimestamp("create_date");
                            if (timestampCreateDate != null) {
                                createDate = timestampCreateDate.toLocalDateTime();
                            }

                            Timestamp timestampChangeDate = rs.getTimestamp("change_date");
                            if (timestampChangeDate != null) {
                                changeDate = timestampChangeDate.toLocalDateTime();
                            }

                            return Page.builder()
                                    .pageId(new PageId(rs.getInt("pageId")))
                                    .parent(rs.getInt("parent"))
                                    .name(rs.getString("name"))
                                    .content(rs.getString("content"))
                                    .timeStart(timeStart)
                                    .timeEnd(timeEnd)
                                    .active(rs.getBoolean("active"))
                                    .createdBy(rs.getObject("created_by") != null ? rs.getInt("created_by") : null)
                                    .createdByLogin(rs.getString("created_by_login"))
                                    .createDate(createDate)
                                    .changedBy(rs.getObject("changed_by") != null ? rs.getInt("changed_by") : null)
                                    .changedByLogin(rs.getString("changed_by_login"))
                                    .changeDate(changeDate)
                                    .active(rs.getBoolean("active"))
                                    .deleted(rs.getBoolean("deleted"))
                                    .build();
                        }
                )
                .single();
    }

    /**
     * create page from domain object
     *
     * @param page page to create
     */
    @Override
    public int createPage(Page page) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        int update = jdbcClient.sql("""
                INSERT INTO page (
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
                .update(keyHolder);

        Assert.state(update == 1, "Failed to create page: " + page.getName());

        return keyHolder.getKey().intValue();
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
        List<Page> pages = jdbcClient.sql("""
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
                .query(
                        (rs, rowNum) -> {
                            // Safe conversion of nullable timestamps to LocalDateTime
                            Timestamp tsStart = rs.getTimestamp("time_start");
                            Timestamp tsEnd = rs.getTimestamp("time_end");
                            Timestamp tsCreate = rs.getTimestamp("create_date");
                            Timestamp tsChange = rs.getTimestamp("change_date");

                            LocalDateTime timeStart = tsStart != null ? tsStart.toLocalDateTime() : null;
                            LocalDateTime timeEnd = tsEnd != null ? tsEnd.toLocalDateTime() : null;
                            LocalDateTime createDate = tsCreate != null ? tsCreate.toLocalDateTime() : null;
                            LocalDateTime changeDate = tsChange != null ? tsChange.toLocalDateTime() : null;

                            Integer createdBy = rs.getObject("created_by") != null ? rs.getInt("created_by") : null;
                            Integer changedBy = rs.getObject("changed_by") != null ? rs.getInt("changed_by") : null;

                            return Page.builder()
                                    .pageId(new PageId(rs.getInt("pageId")))
                                    .parent(rs.getInt("parent"))
                                    .name(rs.getString("name"))
                                    .content(rs.getString("content"))
                                    .timeStart(timeStart)
                                    .timeEnd(timeEnd)
                                    .active(rs.getBoolean("active"))
                                    .createdBy(createdBy)
                                    .createDate(createDate)
                                    .changedBy(changedBy)
                                    .changeDate(changeDate)
                                    .active(rs.getBoolean("active"))
                                    .deleted(rs.getBoolean("deleted"))
                                    .build();
                        }
                )
                .list();

        // add files to pages
        addFilesToPages(pages);

        return pages;
    }

    @Override
    public List<Page> getPageHierarchy(PageId pageId) {
        List<Page> hierarchy = new ArrayList<>();
        Set<Integer> visitedIds = new HashSet<>();  // Für Erkennung von Zirkelbezügen

        // Get current page
        Page currentPage = findById(pageId);
        if (currentPage == null || currentPage.getDeleted()) {
            return hierarchy;
        }

        // Add current page to hierarchy and mark as visited
        hierarchy.add(currentPage);
        visitedIds.add(currentPage.getPageId().value());

        // Recursively get all parents
        int parentId = currentPage.getParent();
        while (parentId != 0) {  // 0 indicates root level
            // Prüfe auf Zirkelbezüge
            if (visitedIds.contains(parentId)) {
                // Log warning about circular reference
                System.err.println("Circular reference detected in page hierarchy at page ID: " + parentId);
                break;
            }

            Page parentPage = findById(new PageId(parentId));
            // Prüfe ob Parent existiert und nicht gelöscht ist
            if (parentPage == null || parentPage.getDeleted()) {
                break;
            }

            // Add parent to beginning of list to maintain root->leaf order
            hierarchy.add(0, parentPage);
            visitedIds.add(parentId);
            parentId = parentPage.getParent();
        }

        return hierarchy;
    }

    /**
     * add files to pages
     * @param pages
     * @return
     */
    private List<Page> addFilesToPages(List<Page> pages) {
        for(Page page : pages) {
            page.setFiles(
                addFilesToPage(page).getFiles()
            );
        }

        return pages;
    }

    /**
     * add files to page
     * @param page
     * @return
     */
    private Page addFilesToPage(Page page) {
        page.setFiles(
                fileImpl.listFiles(
                        FileFilter.builder()
                                .pageId(page.getPageId())
                                .build()
                )
        );

        return page;
    }
}
