package org.knowledgeroot.app.page.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.page.PageDao;
import org.knowledgeroot.app.page.PageFilter;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
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
        // get current session
        Session session = entityManager.unwrap(org.hibernate.Session.class);

        Criteria pageCriteria = session.createCriteria(Page.class, "p");

        // build restrictions
        if(pageFilter.getId() != null)
            pageCriteria.add(Restrictions.eq("p.id", pageFilter.getId()));

        if(pageFilter.getParent() != null)
            pageCriteria.add(Restrictions.eq("p.parent", pageFilter.getParent()));

        if(pageFilter.getName() != null)
            pageCriteria.add(Restrictions.like("p.name", "%" + pageFilter.getName() + "%"));

        if(pageFilter.getSubtitle() != null)
            pageCriteria.add(Restrictions.like("p.subtitle", "%" + pageFilter.getSubtitle() + "%"));

        if(pageFilter.getDescription() != null)
            pageCriteria.add(Restrictions.like("p.description", "%" + pageFilter.getDescription() + "%"));

        if(pageFilter.getTooltip() != null)
            pageCriteria.add(Restrictions.like("p.tooltip", "%" + pageFilter.getTooltip() + "%"));

        if(pageFilter.getIcon() != null)
            pageCriteria.add(Restrictions.like("p.icon", "%" + pageFilter.getIcon() + "%"));

        if(pageFilter.getAlias() != null)
            pageCriteria.add(Restrictions.like("p.alias", "%" + pageFilter.getAlias() + "%"));

        if(pageFilter.getContentCollapse() != null)
            pageCriteria.add(Restrictions.eq("p.contentCollapse", pageFilter.getContentCollapse()));

        if(pageFilter.getContentPosition() != null)
            pageCriteria.add(Restrictions.like("p.contentPosition", "%" + pageFilter.getContentPosition() + "%"));

        if(pageFilter.getShowContentDescription() != null)
            pageCriteria.add(Restrictions.eq("p.showContentDescription", pageFilter.getShowContentDescription()));

        if(pageFilter.getShowTableOfContent() != null)
            pageCriteria.add(Restrictions.eq("p.showTableOfContent", pageFilter.getShowTableOfContent()));

        if(pageFilter.getSorting() != null)
            pageCriteria.add(Restrictions.eq("p.sorting", pageFilter.getSorting()));

        if(pageFilter.getDeleted() != null)
            pageCriteria.add(Restrictions.eq("p.deleted", pageFilter.getDeleted()));

        if(pageFilter.getActive() != null)
            pageCriteria.add(Restrictions.eq("p.active", pageFilter.getActive()));

        if(pageFilter.getCreatedBy() != null)
            pageCriteria.add(Restrictions.eq("p.createdBy", pageFilter.getCreatedBy()));

        if(pageFilter.getChangedBy() != null)
            pageCriteria.add(Restrictions.eq("p.changedBy", pageFilter.getChangedBy()));

        if(pageFilter.getTimeStartBegin() != null)
            pageCriteria.add(Restrictions.ge("p.timeStart", pageFilter.getTimeStartBegin()));

        if(pageFilter.getTimeStartEnd() != null)
            pageCriteria.add(Restrictions.le("p.timeStart", pageFilter.getTimeStartEnd()));

        if(pageFilter.getTimeEndBegin() != null)
            pageCriteria.add(Restrictions.ge("p.timeEnd", pageFilter.getTimeEndBegin()));

        if(pageFilter.getTimeEndEnd() != null)
            pageCriteria.add(Restrictions.le("p.timeEnd", pageFilter.getTimeEndEnd()));

        if(pageFilter.getCreateDateBegin() != null)
            pageCriteria.add(Restrictions.ge("p.createDate", pageFilter.getCreateDateBegin()));

        if(pageFilter.getCreateDateEnd() != null)
            pageCriteria.add(Restrictions.le("p.createDate", pageFilter.getCreateDateEnd()));

        if(pageFilter.getChangeDateBegin() != null)
            pageCriteria.add(Restrictions.ge("p.changeDate", pageFilter.getChangeDateBegin()));

        if(pageFilter.getChangeDateEnd() != null)
            pageCriteria.add(Restrictions.le("p.changeDate", pageFilter.getChangeDateEnd()));

        // set limit
        if(pageFilter.getLimit() != null)
            pageCriteria.setMaxResults(pageFilter.getLimit());

        // set start position
        if(pageFilter.getStart() != null)
            pageCriteria.setFirstResult(pageFilter.getStart());

        // get result
        return Collections.checkedList(pageCriteria.list(), Page.class);
    }

    /**
     * find page by given id
     *
     * @param id
     * @return
     */
    @Override
    public Page findById(long id) {
        return findEntityById(id);
    }

    /**
     * find page entity by given id
     * @param id page id
     */
    private Page findEntityById(long id) {
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
    public void deletePageById(long id) {
        entityManager.createQuery("DELETE FROM Page p WHERE p.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
