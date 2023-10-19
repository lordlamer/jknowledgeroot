package org.knowledgeroot.app.page.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageFilter;
import org.knowledgeroot.app.page.domain.PageDao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PageImpl implements PageDao {
    private final EntityManager entityManager;

    /**
     * find pages
     *
     * @param pageFilter
     * @return
     */
    @Override
    public List<Page> listPages(PageFilter pageFilter) {
        // get criteria builder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Page> cq = cb.createQuery(Page.class);

        Root<Page> from = cq.from(Page.class);

        List<Predicate> predicates = new ArrayList<>();

        // build restrictions
        if(pageFilter.getId() != null) {
            Predicate id = cb.equal(from.get("id"), pageFilter.getId());
            predicates.add(id);
        }

        if(pageFilter.getParent() != null) {
            Predicate parent = cb.equal(from.get("parent"), pageFilter.getParent());
            predicates.add(parent);
        }

        if(pageFilter.getName() != null) {
            Predicate name = cb.like(from.get("name"), "%" + pageFilter.getName() + "%");
            predicates.add(name);
        }

        if(pageFilter.getSubtitle() != null) {
            Predicate subtitle = cb.like(from.get("subtitle"), "%" + pageFilter.getSubtitle() + "%");
            predicates.add(subtitle);
        }

        if(pageFilter.getDescription() != null) {
            Predicate description = cb.like(from.get("description"), "%" + pageFilter.getDescription() + "%");
            predicates.add(description);
        }

        if(pageFilter.getTooltip() != null) {
            Predicate tooltip = cb.like(from.get("tooltip"), "%" + pageFilter.getTooltip() + "%");
            predicates.add(tooltip);
        }

        if(pageFilter.getIcon() != null) {
            Predicate icon = cb.like(from.get("icon"), "%" + pageFilter.getIcon() + "%");
            predicates.add(icon);
        }

        if(pageFilter.getAlias() != null) {
            Predicate alias = cb.like(from.get("alias"), "%" + pageFilter.getAlias() + "%");
            predicates.add(alias);
        }

        if(pageFilter.getContentCollapse() != null) {
            Predicate contentCollapse = cb.equal(from.get("contentCollapse"), pageFilter.getContentCollapse());
            predicates.add(contentCollapse);
        }

        if(pageFilter.getContentPosition() != null) {
            Predicate contentPosition = cb.like(from.get("contentPosition"), "%" + pageFilter.getContentPosition() + "%");
            predicates.add(contentPosition);
        }

        if(pageFilter.getShowContentDescription() != null) {
            Predicate showContentDescription = cb.equal(from.get("showContentDescription"), pageFilter.getShowContentDescription());
            predicates.add(showContentDescription);
        }

        if(pageFilter.getShowTableOfContent() != null) {
            Predicate showTableOfContent = cb.equal(from.get("showTableOfContent"), pageFilter.getShowTableOfContent());
            predicates.add(showTableOfContent);
        }

        if(pageFilter.getSorting() != null) {
            Predicate sorting = cb.equal(from.get("sorting"), pageFilter.getSorting());
            predicates.add(sorting);
        }

        if(pageFilter.getDeleted() != null) {
            Predicate deleted = cb.equal(from.get("deleted"), pageFilter.getDeleted());
            predicates.add(deleted);
        }

        if(pageFilter.getActive() != null) {
            Predicate active = cb.equal(from.get("active"), pageFilter.getActive());
            predicates.add(active);
        }

        if(pageFilter.getCreatedBy() != null) {
            Predicate createdBy = cb.equal(from.get("createdBy"), pageFilter.getCreatedBy());
            predicates.add(createdBy);
        }

        if(pageFilter.getChangedBy() != null) {
            Predicate changedBy = cb.equal(from.get("changedBy"), pageFilter.getChangedBy());
            predicates.add(changedBy);
        }

        if(pageFilter.getTimeStartBegin() != null) {
            Predicate timeStartBegin = cb.greaterThan(from.get("timeStart"), pageFilter.getTimeStartBegin());
            predicates.add(timeStartBegin);
        }

        if(pageFilter.getTimeStartEnd() != null) {
            Predicate timeStartEnd = cb.lessThan(from.get("timeStart"), pageFilter.getTimeStartEnd());
            predicates.add(timeStartEnd);
        }

        if(pageFilter.getTimeEndBegin() != null) {
            Predicate timeEndBegin = cb.greaterThan(from.get("timeEnd"), pageFilter.getTimeEndBegin());
            predicates.add(timeEndBegin);
        }

        if(pageFilter.getTimeEndEnd() != null) {
            Predicate timeEndEnd = cb.lessThan(from.get("timeEnd"), pageFilter.getTimeEndEnd());
            predicates.add(timeEndEnd);
        }

        if(pageFilter.getCreateDateBegin() != null) {
            Predicate createDateBegin = cb.greaterThan(from.get("createDate"), pageFilter.getCreateDateBegin());
            predicates.add(createDateBegin);
        }

        if(pageFilter.getCreateDateEnd() != null) {
            Predicate createDateEnd = cb.lessThan(from.get("createDate"), pageFilter.getCreateDateEnd());
            predicates.add(createDateEnd);
        }

        if(pageFilter.getChangeDateBegin() != null) {
            Predicate changeDateBegin = cb.greaterThan(from.get("changeDate"), pageFilter.getChangeDateBegin());
            predicates.add(changeDateBegin);
        }

        if(pageFilter.getChangeDateEnd() != null) {
            Predicate changeDateEnd = cb.lessThan(from.get("changeDate"), pageFilter.getChangeDateEnd());
            predicates.add(changeDateEnd);
        }

        cq.select(from).where(cb.and(predicates.toArray(Predicate[]::new)));
        TypedQuery<Page> q = entityManager.createQuery(cq);

        // set limit
        if(pageFilter.getLimit() != null)
            q.setMaxResults(pageFilter.getLimit());

        // set start position
        if(pageFilter.getStart() != null)
            q.setFirstResult(pageFilter.getStart());

        // get result
        return q.getResultList();
    }

    /**
     * find page by given id
     *
     * @param id
     * @return
     */
    @Override
    public Page findById(Integer id) {
        return findEntityById(id);
    }

    /**
     * find page entity by given id
     * @param id page id
     */
    private Page findEntityById(Integer id) {
        return entityManager.find(Page.class, id);
    }

    /**
     * check if page exists
     *
     * @param page page to check
     */
    @Override
    public boolean isPageExist(Page page) {
        boolean found = false;

        List<Page> pageEntities = entityManager.createQuery(
                "SELECT p FROM Page p WHERE p.id = :id",
                Page.class)
                .setParameter("id", page.getId())
                .getResultList();

        if(pageEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create page from domain object
     *
     * @param page page to create
     */
    @Override
    public void createPage(Page page) {
        entityManager.persist(page);
    }

    /**
     * update existing page
     *
     * @param page page to update
     */
    @Override
    public void updatePage(Page page) {
        // save to database
        entityManager.merge(page);
    }

    /**
     * delete all pages
     */
    @Override
    public void deleteAllPages() {
        entityManager.createQuery("DELETE FROM Page").executeUpdate();
    }

    /**
     * delete page by given id
     *
     * @param id page id
     */
    @Override
    public void deletePageById(Integer id) {
        entityManager.createQuery("DELETE FROM Page p WHERE p.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
