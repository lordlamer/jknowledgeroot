package org.knowledgeroot.app.group.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.group.Group;
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
    private final OrikaMapper mapper;

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

        Criteria groupCriteria = session.createCriteria(GroupEntity.class, "g");

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
        List<GroupEntity> groupEntities = Collections.checkedList(groupCriteria.list(), GroupEntity.class);

        return mapper.mapAsList(groupEntities, Group.class);
    }

    /**
     * find group by given id
     *
     * @param id
     * @return
     */
    @Override
    public Group findById(long id) {
        GroupEntity groupEntity = findEntityById(id);

        return mapper.map(groupEntity, Group.class);
    }

    /**
     * find group entity by given id
     * @param id
     * @return
     */
    private GroupEntity findEntityById(long id) {
        return entityManager.find(GroupEntity.class, id);
    }

    /**
     * check if group exists
     *
     * @param group
     * @return
     */
    @Override
    public boolean isGroupExist(Group group) {
        boolean found = false;

        List<GroupEntity> groupEntities = entityManager.createQuery(
                "SELECT u FROM GroupEntity g WHERE g.name = :name",
                GroupEntity.class)
                .setParameter("name", group.getName())
                .getResultList();

        if(groupEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create group from domain object
     *
     * @param group
     */
    @Override
    public void createGroup(Group group) {
        GroupEntity groupEntity = mapper.map(group, GroupEntity.class);

        entityManager.persist(groupEntity);
    }

    /**
     * update existing group
     *
     * @param group
     */
    @Override
    public void updateGroup(Group group) {
        // create group entity
        GroupEntity groupEntity = mapper.map(group, GroupEntity.class);

        // save to database
        entityManager.merge(groupEntity);
    }

    /**
     * delete all groups
     */
    @Override
    public void deleteAllGroups() {
        entityManager.createQuery("DELETE FROM GroupEntity").executeUpdate();
    }

    /**
     * delete group by given id
     *
     * @param id
     */
    @Override
    public void deleteGroupById(long id) {
        entityManager.createQuery("DELETE FROM GroupEntity g WHERE g.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
