package org.knowledgeroot.app.group.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.group.GroupDao;
import org.knowledgeroot.app.group.GroupFilter;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class GroupImpl implements GroupDao {
    private final EntityManager entityManager;

    /**
     * find groups
     *
     * @param groupFilter
     * @return
     */
    @Override
    public List<Group> listGroups(GroupFilter groupFilter) {
        // get current session
        Session session = entityManager.unwrap(org.hibernate.Session.class);

        Criteria groupCriteria = session.createCriteria(Group.class, "g");

        // build restrictions
        if(groupFilter.getId() != null)
            groupCriteria.add(Restrictions.eq("g.id", groupFilter.getId()));

        if(groupFilter.getName() != null)
            groupCriteria.add(Restrictions.like("g.name", "%" + groupFilter.getName() + "%"));

        if(groupFilter.getDescription() != null)
            groupCriteria.add(Restrictions.like("g.description", "%" + groupFilter.getDescription() + "%"));

        if(groupFilter.getDeleted() != null)
            groupCriteria.add(Restrictions.eq("g.deleted", groupFilter.getDeleted()));

        if(groupFilter.getActive() != null)
            groupCriteria.add(Restrictions.eq("g.active", groupFilter.getActive()));

        if(groupFilter.getCreatedBy() != null)
            groupCriteria.add(Restrictions.eq("g.createdBy", groupFilter.getCreatedBy()));

        if(groupFilter.getChangedBy() != null)
            groupCriteria.add(Restrictions.eq("g.changedBy", groupFilter.getChangedBy()));

        if(groupFilter.getTimeStartBegin() != null)
            groupCriteria.add(Restrictions.ge("g.timeStart", groupFilter.getTimeStartBegin()));

        if(groupFilter.getTimeStartEnd() != null)
            groupCriteria.add(Restrictions.le("g.timeStart", groupFilter.getTimeStartEnd()));

        if(groupFilter.getTimeEndBegin() != null)
            groupCriteria.add(Restrictions.ge("g.timeEnd", groupFilter.getTimeEndBegin()));

        if(groupFilter.getTimeEndEnd() != null)
            groupCriteria.add(Restrictions.le("g.timeEnd", groupFilter.getTimeEndEnd()));

        if(groupFilter.getCreateDateBegin() != null)
            groupCriteria.add(Restrictions.ge("g.createDate", groupFilter.getCreateDateBegin()));

        if(groupFilter.getCreateDateEnd() != null)
            groupCriteria.add(Restrictions.le("g.createDate", groupFilter.getCreateDateEnd()));

        if(groupFilter.getChangeDateBegin() != null)
            groupCriteria.add(Restrictions.ge("g.changeDate", groupFilter.getChangeDateBegin()));

        if(groupFilter.getChangeDateEnd() != null)
            groupCriteria.add(Restrictions.le("g.changeDate", groupFilter.getChangeDateEnd()));

        // set limit
        if(groupFilter.getLimit() != null)
            groupCriteria.setMaxResults(groupFilter.getLimit());

        // set start position
        if(groupFilter.getStart() != null)
            groupCriteria.setFirstResult(groupFilter.getStart());

        // get result
        return Collections.checkedList(groupCriteria.list(), Group.class);
    }

    /**
     * find group by given id
     *
     * @param id
     * @return
     */
    @Override
    public Group findById(long id) {

        return findEntityById(id);
    }

    /**
     * find group entity by given id
     * @param id group id
     */
    private Group findEntityById(long id) {
        return entityManager.find(Group.class, id);
    }

    /**
     * check if group exists
     *
     * @param group group to check
     */
    @Override
    public boolean isGroupExist(Group group) {
        boolean found = false;

        List<Group> groupEntities = entityManager.createQuery(
                "SELECT u FROM Group g WHERE g.name = :name",
                Group.class)
                .setParameter("name", group.getName())
                .getResultList();

        if(groupEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create group from domain object
     *
     * @param group group to create
     */
    @Override
    public void createGroup(Group group) {
        entityManager.persist(group);
    }

    /**
     * update existing group
     *
     * @param group group to update
     */
    @Override
    public void updateGroup(Group group) {
        // save to database
        entityManager.merge(group);
    }

    /**
     * delete all groups
     */
    @Override
    public void deleteAllGroups() {
        entityManager.createQuery("DELETE FROM Group").executeUpdate();
    }

    /**
     * delete group by given id
     *
     * @param id group id
     */
    @Override
    public void deleteGroupById(long id) {
        entityManager.createQuery("DELETE FROM Group g WHERE g.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
