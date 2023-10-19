package org.knowledgeroot.persistence.Content;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.knowledgeroot.domain.ContentDao;
import org.knowledgeroot.app.api.filter.ContentFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class ContentImpl implements ContentDao {
    private final EntityManager entityManager;

    /**
     * find all contents
     *
     * @param contentFilter
     * @return
     */
    @Override
    public List<Content> listContents(ContentFilter contentFilter) {
        // get criteria builder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Content> cq = cb.createQuery(Content.class);

        Root<Content> from = cq.from(Content.class);

        List<Predicate> predicates = new ArrayList<>();

        // build restrictions
        if(contentFilter.getId() != null) {
            Predicate id = cb.equal(from.get("id"), contentFilter.getId());
            predicates.add(id);
        }

        if(contentFilter.getParent() != null) {
            Predicate parent = cb.equal(from.get("parent"), contentFilter.getParent());
            predicates.add(parent);
        }

        if(contentFilter.getName() != null) {
            Predicate name = cb.like(from.get("name"), "%" + contentFilter.getName() + "%");
            predicates.add(name);
        }

        if(contentFilter.getContent() != null) {
            Predicate content = cb.like(from.get("content"), "%" + contentFilter.getContent() + "%");
            predicates.add(content);
        }

        if(contentFilter.getType() != null) {
            Predicate type = cb.like(from.get("type"), "%" + contentFilter.getType() + "%");
            predicates.add(type);
        }

        if(contentFilter.getSorting() != null) {
            Predicate sorting = cb.equal(from.get("sorting"), contentFilter.getSorting());
            predicates.add(sorting);
        }

        if(contentFilter.getDeleted() != null) {
            Predicate deleted = cb.equal(from.get("deleted"), contentFilter.getDeleted());
            predicates.add(deleted);
        }

        if(contentFilter.getActive() != null) {
            Predicate active = cb.equal(from.get("active"), contentFilter.getActive());
            predicates.add(active);
        }

        if(contentFilter.getCreatedBy() != null) {
            Predicate createdBy = cb.equal(from.get("createdBy"), contentFilter.getCreatedBy());
            predicates.add(createdBy);
        }

        if(contentFilter.getChangedBy() != null) {
            Predicate changedBy = cb.equal(from.get("changedBy"), contentFilter.getChangedBy());
            predicates.add(changedBy);
        }

        if(contentFilter.getTimeStartBegin() != null) {
            Predicate timeStartBegin = cb.greaterThan(from.get("timeStart"), contentFilter.getTimeStartBegin());
            predicates.add(timeStartBegin);
        }

        if(contentFilter.getTimeStartEnd() != null) {
            Predicate timeStartEnd = cb.lessThan(from.get("timeStart"), contentFilter.getTimeStartEnd());
            predicates.add(timeStartEnd);
        }

        if(contentFilter.getTimeEndBegin() != null) {
            Predicate timeEndBegin = cb.greaterThan(from.get("timeEnd"), contentFilter.getTimeEndBegin());
            predicates.add(timeEndBegin);
        }

        if(contentFilter.getTimeEndEnd() != null) {
            Predicate timeEndEnd = cb.lessThan(from.get("timeEnd"), contentFilter.getTimeEndEnd());
            predicates.add(timeEndEnd);
        }

        if(contentFilter.getCreateDateBegin() != null) {
            Predicate createDateBegin = cb.greaterThan(from.get("createDate"), contentFilter.getCreateDateBegin());
            predicates.add(createDateBegin);
        }

        if(contentFilter.getCreateDateEnd() != null) {
            Predicate createDateEnd = cb.lessThan(from.get("createDate"), contentFilter.getCreateDateEnd());
            predicates.add(createDateEnd);
        }

        if(contentFilter.getChangeDateBegin() != null) {
            Predicate changeDateBegin = cb.greaterThan(from.get("changeDate"), contentFilter.getChangeDateBegin());
            predicates.add(changeDateBegin);
        }

        if(contentFilter.getChangeDateEnd() != null) {
            Predicate changeDateEnd = cb.lessThan(from.get("changeDate"), contentFilter.getChangeDateEnd());
            predicates.add(changeDateEnd);
        }

        cq.select(from).where(cb.and(predicates.toArray(Predicate[]::new)));
        TypedQuery<Content> q = entityManager.createQuery(cq);

        // set limit
        if(contentFilter.getLimit() != null)
            q.setMaxResults(contentFilter.getLimit());

        // set start position
        if(contentFilter.getStart() != null)
            q.setFirstResult(contentFilter.getStart());

        // get result
        return q.getResultList();
    }

    /**
     * find content by given id
     *
     * @param id
     * @return
     */
    @Override
    public Content findById(Integer id) {
        Content content = findEntityById(id);

        return content;
    }

    /**
     * find content entity by given id
     * @param id
     * @return
     */
    private Content findEntityById(Integer id) {
        return entityManager.find(Content.class, id);
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

        List<Content> contentEntities = entityManager.createQuery(
                "SELECT c FROM Content c WHERE c.id = :id",
                Content.class)
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
        entityManager.persist(content);
    }

    /**
     * update existing content
     *
     * @param content
     */
    @Override
    public void updateContent(Content content) {
        // save to database
        entityManager.merge(content);
    }

    /**
     * delete all contents
     */
    @Override
    public void deleteAllContents() {
        entityManager.createQuery("DELETE FROM Content").executeUpdate();
    }

    /**
     * delete content by given id
     *
     * @param id
     */
    @Override
    public void deleteContentById(Integer id) {
        entityManager.createQuery("DELETE FROM Content c WHERE c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
