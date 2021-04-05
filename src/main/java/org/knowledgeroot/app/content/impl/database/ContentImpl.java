package org.knowledgeroot.app.content.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.content.Content;
import org.knowledgeroot.app.content.ContentDao;
import org.knowledgeroot.app.content.ContentFilter;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class ContentImpl implements ContentDao {
    private final EntityManager entityManager;
    private final OrikaMapper mapper;

    /**
     * find all contents
     *
     * @param contentFilter
     * @return
     */
    @Override
    public List<Content> listContents(ContentFilter contentFilter) {
        // get current session
        Session session = entityManager.unwrap(org.hibernate.Session.class);

        Criteria contentCriteria = session.createCriteria(ContentEntity.class, "c");

        // build restrictions
        if(contentFilter.getId() != null)
            contentCriteria.add(Restrictions.eq("c.id", contentFilter.getId()));

        if(contentFilter.getParent() != null)
            contentCriteria.add(Restrictions.eq("c.parent", contentFilter.getParent()));

        if(contentFilter.getName() != null)
            contentCriteria.add(Restrictions.like("c.name", "%" + contentFilter.getName() + "%"));

        if(contentFilter.getContent() != null)
            contentCriteria.add(Restrictions.like("c.content", "%" + contentFilter.getContent() + "%"));

        if(contentFilter.getType() != null)
            contentCriteria.add(Restrictions.like("c.type", "%" + contentFilter.getType() + "%"));

        if(contentFilter.getSorting() != null)
            contentCriteria.add(Restrictions.eq("c.sorting", contentFilter.getSorting()));

        if(contentFilter.getDeleted() != null)
            contentCriteria.add(Restrictions.eq("c.deleted", contentFilter.getDeleted()));

        if(contentFilter.getActive() != null)
            contentCriteria.add(Restrictions.eq("c.active", contentFilter.getActive()));

        if(contentFilter.getCreatedBy() != null)
            contentCriteria.add(Restrictions.eq("c.createdBy", contentFilter.getCreatedBy()));

        if(contentFilter.getChangedBy() != null)
            contentCriteria.add(Restrictions.eq("c.changedBy", contentFilter.getChangedBy()));

        if(contentFilter.getTimeStartBegin() != null)
            contentCriteria.add(Restrictions.ge("c.timeStart", contentFilter.getTimeStartBegin()));

        if(contentFilter.getTimeStartEnd() != null)
            contentCriteria.add(Restrictions.le("c.timeStart", contentFilter.getTimeStartEnd()));

        if(contentFilter.getTimeEndBegin() != null)
            contentCriteria.add(Restrictions.ge("c.timeEnd", contentFilter.getTimeEndBegin()));

        if(contentFilter.getTimeEndEnd() != null)
            contentCriteria.add(Restrictions.le("c.timeEnd", contentFilter.getTimeEndEnd()));

        if(contentFilter.getCreateDateBegin() != null)
            contentCriteria.add(Restrictions.ge("c.createDate", contentFilter.getCreateDateBegin()));

        if(contentFilter.getCreateDateEnd() != null)
            contentCriteria.add(Restrictions.le("c.createDate", contentFilter.getCreateDateEnd()));

        if(contentFilter.getChangeDateBegin() != null)
            contentCriteria.add(Restrictions.ge("c.changeDate", contentFilter.getChangeDateBegin()));

        if(contentFilter.getChangeDateEnd() != null)
            contentCriteria.add(Restrictions.le("c.changeDate", contentFilter.getChangeDateEnd()));

        // set limit
        if(contentFilter.getLimit() != null)
            contentCriteria.setMaxResults(contentFilter.getLimit());

        // set start position
        if(contentFilter.getStart() != null)
            contentCriteria.setFirstResult(contentFilter.getStart());

        // get result
        List<ContentEntity> contentEntities = Collections.checkedList(contentCriteria.list(), ContentEntity.class);

        return mapper.mapAsList(contentEntities, Content.class);
    }

    /**
     * find content by given id
     *
     * @param id
     * @return
     */
    @Override
    public Content findById(long id) {
        ContentEntity contentEntity = findEntityById(id);

        return mapper.map(contentEntity, Content.class);
    }

    /**
     * find content entity by given id
     * @param id
     * @return
     */
    private ContentEntity findEntityById(long id) {
        return entityManager.find(ContentEntity.class, id);
    }

    /**
     * check if content exists
     *
     * @param content
     * @return
     */
    @Override
    public boolean isContentExist(Content content) {
        boolean found = false;

        List<ContentEntity> contentEntities = entityManager.createQuery(
                "SELECT c FROM ContentEntity c WHERE c.id = :id",
                ContentEntity.class)
                .setParameter("id", content.getId())
                .getResultList();

        if(contentEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create content from domain object
     *
     * @param content
     */
    @Override
    public void createContent(Content content) {
        ContentEntity contentEntity = mapper.map(content, ContentEntity.class);

        entityManager.persist(contentEntity);
    }

    /**
     * update existing content
     *
     * @param content
     */
    @Override
    public void updateContent(Content content) {
        // create content entity
        ContentEntity contentEntity = mapper.map(content, ContentEntity.class);

        // save to database
        entityManager.merge(contentEntity);
    }

    /**
     * delete all contents
     */
    @Override
    public void deleteAllContents() {
        entityManager.createQuery("DELETE FROM ContentEntity").executeUpdate();
    }

    /**
     * delete content by given id
     *
     * @param id
     */
    @Override
    public void deleteContentById(long id) {
        entityManager.createQuery("DELETE FROM ContentEntity c WHERE c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
