package org.knowledgeroot.app.content.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.content.domain.Content;
import org.knowledgeroot.app.content.domain.ContentDao;
import org.knowledgeroot.app.content.domain.ContentFilter;
import org.knowledgeroot.app.content.domain.ContentId;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class ContentImpl implements ContentDao {
    private final JdbcClient jdbcClient;
    private final FileDao fileImpl;

    /**
     * find all contents
     *
     * @param contentFilter
     * @return
     */
    @Override
    public List<Content> listContents(ContentFilter contentFilter) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                id as contentId,
                parent,
                name,
                content,
                type,
                sorting,
                time_start,
                time_end,
                active,
                created_by,
                create_date,
                changed_by,
                change_date,
                deleted 
            FROM content
            """);

        Map<String, Object> params = new HashMap<>();
        Map<String, String> sqlParams = new HashMap<>();

        if(contentFilter.getId() != null) {
            params.put("id", contentFilter.getId());
            sqlParams.put("id", "id = :id");
        }

        if(contentFilter.getParent() != null) {
            params.put("parent", contentFilter.getParent());
            sqlParams.put("parent", "parent = :parent");
        }

        if(contentFilter.getName() != null) {
            params.put("name", "%" + contentFilter.getName() + "%");
            sqlParams.put("name", "name like :name");
        }

        if(contentFilter.getContent() != null) {
            params.put("content", "%" + contentFilter.getContent() + "%");
            sqlParams.put("content", "content like :content");
        }

        if(contentFilter.getType() != null) {
            params.put("type", contentFilter.getType());
        }

        if(contentFilter.getSorting() != null) {
            params.put("sorting", contentFilter.getSorting());
        }

        if(contentFilter.getTimeStartBegin() != null) {
            params.put("timeStartBegin", contentFilter.getTimeStartBegin());
        }

        if(contentFilter.getTimeStartEnd() != null) {
            params.put("timeStartEnd", contentFilter.getTimeStartEnd());
        }

        if(contentFilter.getTimeEndBegin() != null) {
            params.put("timeEndBegin", contentFilter.getTimeEndBegin());
            sqlParams.put("timeEndBegin", "time_end >= :timeEndBegin");
        }

        if(contentFilter.getTimeEndEnd() != null) {
            params.put("timeEndEnd", contentFilter.getTimeEndEnd());
            sqlParams.put("timeEndEnd", "time_end <= :timeEndEnd");
        }

        if(contentFilter.getActive() != null) {
            params.put("active", contentFilter.getActive());
            sqlParams.put("active", "active = :active");
        }

        if(contentFilter.getCreatedBy() != null) {
            params.put("createdBy", contentFilter.getCreatedBy());
            sqlParams.put("createdBy", "created_by = :createdBy");
        }

        if(contentFilter.getCreateDateBegin() != null) {
            params.put("createDateBegin", contentFilter.getCreateDateBegin());
            sqlParams.put("createDateBegin", "create_date >= :createDateBegin");
        }

        if(contentFilter.getCreateDateEnd() != null) {
            params.put("createDateEnd", contentFilter.getCreateDateEnd());
            sqlParams.put("createDateEnd", "create_date <= :createDateEnd");
        }

        if(contentFilter.getChangedBy() != null) {
            params.put("changedBy", contentFilter.getChangedBy());
            sqlParams.put("changedBy", "changed_by = :changedBy");
        }

        if(contentFilter.getChangeDateBegin() != null) {
            params.put("changeDateBegin", contentFilter.getChangeDateBegin());
            sqlParams.put("changeDateBegin", "change_date >= :changeDateBegin");
        }

        if(contentFilter.getChangeDateEnd() != null) {
            params.put("changeDateEnd", contentFilter.getChangeDateEnd());
            sqlParams.put("changeDateEnd", "change_date <= :changeDateEnd");
        }

        if(contentFilter.getDeleted() != null) {
            params.put("deleted", contentFilter.getDeleted());
            sqlParams.put("deleted", "deleted = :deleted");
        }

        if(!sqlParams.isEmpty()) {
            sql.append(" WHERE ");

            for(Map.Entry<String, String> entry : sqlParams.entrySet()) {
                sql.append(entry.getValue());

                if(!entry.equals(sqlParams.entrySet().toArray()[sqlParams.size() - 1]))
                    sql.append(" AND ");
            }
        }

        if(contentFilter.getLimit() != null && contentFilter.getStart() != null) {
            params.put("limit", contentFilter.getLimit());
            params.put("start", contentFilter.getStart());
            sql.append(" LIMIT :start, :limit");
        } else if(contentFilter.getLimit() != null) {
            params.put("limit", contentFilter.getLimit());
            sql.append(" LIMIT :limit");
        } else if(contentFilter.getStart() != null) {
            params.put("start", contentFilter.getStart());
            sql.append(" LIMIT :start, 18446744073709551615");
        }

        // execute query
        List<Content> contents = jdbcClient.sql(sql.toString())
                .params(params)
                .query(
                        (rs, rowNum) ->
                                Content.builder()
                                .contentId(new ContentId(rs.getInt("contentId")))
                                .parent(rs.getInt("parent"))
                                .name(rs.getString("name"))
                                .content(rs.getString("content"))
                                .type(rs.getString("type"))
                                .sorting(rs.getInt("sorting"))
                                .timeStart(rs.getTimestamp("time_start").toLocalDateTime())
                                .timeEnd(rs.getTimestamp("time_end").toLocalDateTime())
                                .active(rs.getBoolean("active"))
                                .createdBy(rs.getInt("created_by"))
                                .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                                .changedBy(rs.getInt("changed_by"))
                                .changeDate(rs.getTimestamp("change_date").toLocalDateTime())
                                .deleted(rs.getBoolean("deleted"))
                                .files(null)
                                .build()
                )
                .list();

        // add files to contents
        contents = addFilesToContents(contents);

        return contents;
    }

    /**
     * add files to contents
     * @param contents
     * @return
     */
    private List<Content> addFilesToContents(List<Content> contents) {
        for(Content content : contents) {
            content.setFiles(
                    fileImpl.listFiles(
                            FileFilter.builder()
                                    .contentId(content.getContentId())
                                    .build()
                    )
            );
        }

        return contents;
    }

    /**
     * find content by given id
     *
     * @param contentId
     * @return
     */
    @Override
    public Content findById(ContentId contentId) {
        return jdbcClient.sql("""
                SELECT 
                    id as contentId,
                    parent,
                    name,
                    content,
                    type,
                    sorting,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    deleted
                FROM 
                    content 
                WHERE 
                    id = :id
                """)
                .param("id", contentId.value())
                .query(
                        (rs, rowNum) ->
                                Content.builder()
                                .contentId(new ContentId(rs.getInt("contentId")))
                                .parent(rs.getInt("parent"))
                                .name(rs.getString("name"))
                                .content(rs.getString("content"))
                                .type(rs.getString("type"))
                                .sorting(rs.getInt("sorting"))
                                .timeStart(rs.getTimestamp("time_start").toLocalDateTime())
                                .timeEnd(rs.getTimestamp("time_end").toLocalDateTime())
                                .active(rs.getBoolean("active"))
                                .createdBy(rs.getInt("created_by"))
                                .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                                .changedBy(rs.getInt("changed_by"))
                                .changeDate(rs.getTimestamp("change_date").toLocalDateTime())
                                .deleted(rs.getBoolean("deleted"))
                                .files(null)
                                .build()
                )
                .single();
    }

    /**
     * create content from domain object
     *
     * @param content
     */
    @Override
    public void createContent(Content content) {
        int update = jdbcClient.sql("""
                        INSERT INTO 
                            content(
                                parent,
                                name,
                                content,
                                type,
                                sorting,
                                time_start,
                                time_end,
                                created_by,
                                create_date,
                                changed_by,
                                change_date,
                                active,
                                deleted
                            ) 
                            VALUES (
                                ?,?,?,?,?,?,?,?,?,?,?,?,?
                            )
                        """)
                .params(
                        List.of(
                                content.getParent(),
                                content.getName(),
                                content.getContent(),
                                content.getType(),
                                content.getSorting(),
                                content.getTimeStart(),
                                content.getTimeEnd(),
                                content.getCreatedBy(),
                                content.getCreateDate(),
                                content.getChangedBy(),
                                content.getChangeDate(),
                                content.getActive(),
                                content.getDeleted()
                                )
                )
                .update();

        Assert.state(update == 1, "Failed to create content: " + content.getName());
    }

    /**
     * update existing content
     *
     * @param content
     */
    @Override
    public void updateContent(Content content) {
        int update = jdbcClient.sql("""
                        update 
                            content 
                        set 
                            parent = ?,
                            name = ?,
                            content = ?,
                            type = ?,
                            sorting = ?,
                            time_start = ?,
                            time_end = ?,
                            changed_by = ?,
                            change_date = ?,
                            active = ?,
                            deleted = ? 
                        where id = ?
                    """)
                .params(
                        List.of(
                                content.getParent(),
                                content.getName(),
                                content.getContent(),
                                content.getType(),
                                content.getSorting(),
                                content.getTimeStart(),
                                content.getTimeEnd(),
                                content.getChangedBy(),
                                content.getChangeDate(),
                                content.getActive(),
                                content.getDeleted(),
                                content.getContentId().value()
                        )
                )
                .update();

        Assert.state(
                update == 1,
                "Failed to update content with id: " + content.getContentId().value()
        );
    }

    /**
     * delete all contents
     */
    @Override
    public void deleteAllContents() {
        jdbcClient.sql("delete from content").update();
    }

    /**
     * delete content by given id
     *
     * @param contentId
     */
    @Override
    public void deleteContentById(ContentId contentId) {
        jdbcClient.sql("delete from content where id = :id")
                .param("id", contentId.value())
                .update();
    }

    @Override
    public List<Content> searchContent(String searchQuery) {
        return jdbcClient.sql("""
                SELECT 
                    id as contentId,
                    parent,
                    name,
                    content,
                    type,
                    sorting,
                    time_start,
                    time_end,
                    active,
                    created_by,
                    create_date,
                    changed_by,
                    change_date,
                    deleted
                FROM 
                    content 
                WHERE 
                    name like :searchQuery OR content like :searchQuery
                """)
                .param("searchQuery", "%" + searchQuery + "%")
                .query(Content.class)
                .list();
    }
}
