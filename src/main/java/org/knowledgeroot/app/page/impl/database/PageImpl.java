package org.knowledgeroot.app.page.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.page.Page;
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
    private final OrikaMapper mapper;

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

        Criteria pageCriteria = session.createCriteria(PageEntity.class, "p");

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
        List<PageEntity> pageEntities = Collections.checkedList(pageCriteria.list(), PageEntity.class);

        return mapper.mapAsList(pageEntities, Page.class);
    }

    /**
     * find page by given id
     *
     * @param id
     * @return
     */
    @Override
    public Page findById(long id) {
        PageEntity pageEntity = findEntityById(id);

        return mapper.map(pageEntity, Page.class);
    }

    /**
     * find page entity by given id
     * @param id
     * @return
     */
    private PageEntity findEntityById(long id) {
        return entityManager.find(PageEntity.class, id);
    }

    /**
     * check if page exists
     *
     * @param page
     * @return
     */
    @Override
    public boolean isPageExist(Page page) {
        boolean found = false;

        List<PageEntity> pageEntities = entityManager.createQuery(
                "SELECT p FROM PageEntity p WHERE p.id = :id",
                PageEntity.class)
                .setParameter("id", page.getId())
                .getResultList();

        if(pageEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create page from domain object
     *
     * @param page
     */
    @Override
    public void createPage(Page page) {
        PageEntity pageEntity = mapper.map(page, PageEntity.class);

        entityManager.persist(pageEntity);
    }

    /**
     * update existing page
     *
     * @param page
     */
    @Override
    public void updatePage(Page page) {
        // create page entity
        PageEntity pageEntity = mapper.map(page, PageEntity.class);

        // save to database
        entityManager.merge(pageEntity);
    }

    /**
     * delete all pages
     */
    @Override
    public void deleteAllPages() {
        entityManager.createQuery("DELETE FROM PageEntity").executeUpdate();
    }

    /**
     * delete page by given id
     *
     * @param id
     */
    @Override
    public void deletePageById(long id) {
        entityManager.createQuery("DELETE FROM PageEntity p WHERE p.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
