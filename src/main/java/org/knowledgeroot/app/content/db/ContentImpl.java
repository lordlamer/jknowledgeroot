package org.knowledgeroot.app.content.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.content.domain.Content;
import org.knowledgeroot.app.content.domain.ContentDao;
import org.knowledgeroot.app.content.domain.ContentFilter;
import org.knowledgeroot.app.content.domain.ContentId;
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

    /**
     * find all contents
     *
     * @param contentFilter
     * @return
     */
    @Override
    public List<Content> listContents(ContentFilter contentFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM content");

        Map<String, Object> params = new HashMap<>();
        Map<String, String> sqlParams = new HashMap<>();

        if(contentFilter.getParent() != null) {
            params.put("parent", contentFilter.getParent());
            sqlParams.put("parent", "parent = :parent");
        }

        if(contentFilter.getName() != null) {
            params.put("name", contentFilter.getName());
        }

        if(contentFilter.getContent() != null) {
            params.put("content", contentFilter.getContent());
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
        }

        if(contentFilter.getTimeEndEnd() != null) {
            params.put("timeEndEnd", contentFilter.getTimeEndEnd());
        }

        if(contentFilter.getActive() != null) {
            params.put("active", contentFilter.getActive());
        }

        if(contentFilter.getCreatedBy() != null) {
            params.put("createdBy", contentFilter.getCreatedBy());
        }

        if(contentFilter.getCreateDateBegin() != null) {
            params.put("createDateBegin", contentFilter.getCreateDateBegin());
        }

        if(contentFilter.getCreateDateEnd() != null) {
            params.put("createDateEnd", contentFilter.getCreateDateEnd());
        }

        if(contentFilter.getChangedBy() != null) {
            params.put("changedBy", contentFilter.getChangedBy());
        }

        if(contentFilter.getChangeDateBegin() != null) {
            params.put("changeDateBegin", contentFilter.getChangeDateBegin());
        }

        if(contentFilter.getChangeDateEnd() != null) {
            params.put("changeDateEnd", contentFilter.getChangeDateEnd());
        }

        if(contentFilter.getDeleted() != null) {
            params.put("deleted", contentFilter.getDeleted());
        }

        if(contentFilter.getStart() != null) {
            params.put("start", contentFilter.getStart());
        }



        if(!sqlParams.isEmpty()) {
            sql.append(" WHERE ");

            for(Map.Entry<String, String> entry : sqlParams.entrySet()) {
                sql.append(entry.getValue());

                if(!entry.equals(sqlParams.entrySet().toArray()[sqlParams.size() - 1]))
                    sql.append(" AND ");
            }
        }

        if(contentFilter.getLimit() != null) {
            params.put("limit", contentFilter.getLimit());
            sql.append(" LIMIT :limit");
        }

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(Content.class)
                .list();
    }

    /**
     * find content by given id
     *
     * @param contentId
     * @return
     */
    @Override
    public Content findById(ContentId contentId) {
        return jdbcClient.sql("SELECT * FROM content WHERE id = :id")
                .param("id", contentId.value())
                .query(Content.class)
                .single();
    }

    /**
     * create content from domain object
     *
     * @param content
     */
    @Override
    public void createContent(Content content) {
        int update = jdbcClient.sql("INSERT INTO content(parent,name,content,type,sorting,time_start,time_end,created_by,create_date,changed_by,change_date,active,deleted) values(?,?,?,?,?,?,?,?,?,?,?,?,?)")
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
        int update = jdbcClient.sql("update content set parent = ?, name = ?, content = ?, type = ?, sorting = ?, time_start = ?, time_end = ?, created_by = ?, create_date = ?, changed_by = ?, change_date = ?, active = ?, deleted = ? where id = ?")
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
                                content.getDeleted(),
                                content.getId()
                        )
                )
                .update();

        Assert.state(update == 1, "Failed to update content with id: " + content.getId());
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
}
