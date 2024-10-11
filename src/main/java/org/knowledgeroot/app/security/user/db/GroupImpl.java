package org.knowledgeroot.app.security.user.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.domain.Group;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.GroupId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class GroupImpl implements GroupDao {
    private final EntityManager entityManager;
    private final GroupEntityConverter groupEntityConverter = new GroupEntityConverter();

    /**
     * find groups
     *
     * @param groupFilter
     * @return
     */
    @Override
    public List<Group> listGroups(GroupFilter groupFilter) {
        // get criteria builder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<GroupEntity> cq = cb.createQuery(GroupEntity.class);

        Root<GroupEntity> from = cq.from(GroupEntity.class);

        List<Predicate> predicates = new ArrayList<>();

        // build restrictions
        if(groupFilter.getId() != null) {
            Predicate id = cb.equal(from.get("id"), groupFilter.getId());
            predicates.add(id);
        }

        if(groupFilter.getName() != null) {
            Predicate name = cb.like(from.get("name"), "%" + groupFilter.getName() + "%");
            predicates.add(name);
        }

        if(groupFilter.getDescription() != null) {
            Predicate description = cb.like(from.get("description"), "%" + groupFilter.getDescription() + "%");
            predicates.add(description);
        }

        if(groupFilter.getDeleted() != null) {
            Predicate deleted = cb.equal(from.get("deleted"), groupFilter.getDeleted());
            predicates.add(deleted);
        }

        if(groupFilter.getActive() != null) {
            Predicate active = cb.equal(from.get("active"), groupFilter.getActive());
            predicates.add(active);
        }

        if(groupFilter.getCreatedBy() != null) {
            Predicate createdBy = cb.equal(from.get("createdBy"), groupFilter.getCreatedBy());
            predicates.add(createdBy);
        }

        if(groupFilter.getChangedBy() != null) {
            Predicate changedBy = cb.equal(from.get("changedBy"), groupFilter.getChangedBy());
            predicates.add(changedBy);
        }

        if(groupFilter.getTimeStartBegin() != null) {
            Predicate timeStartBegin = cb.greaterThan(from.get("timeStart"), groupFilter.getTimeStartBegin());
            predicates.add(timeStartBegin);
        }

        if(groupFilter.getTimeStartEnd() != null) {
            Predicate timeStartEnd = cb.lessThan(from.get("timeStart"), groupFilter.getTimeStartEnd());
            predicates.add(timeStartEnd);
        }

        if(groupFilter.getTimeEndBegin() != null) {
            Predicate timeEndBegin = cb.greaterThan(from.get("timeEnd"), groupFilter.getTimeEndBegin());
            predicates.add(timeEndBegin);
        }

        if(groupFilter.getTimeEndEnd() != null) {
            Predicate timeEndEnd = cb.lessThan(from.get("timeEnd"), groupFilter.getTimeEndEnd());
            predicates.add(timeEndEnd);
        }

        if(groupFilter.getCreateDateBegin() != null) {
            Predicate createDateBegin = cb.greaterThan(from.get("createDate"), groupFilter.getCreateDateBegin());
            predicates.add(createDateBegin);
        }

        if(groupFilter.getCreateDateEnd() != null) {
            Predicate createDateEnd = cb.lessThan(from.get("createDate"), groupFilter.getCreateDateEnd());
            predicates.add(createDateEnd);
        }

        if(groupFilter.getChangeDateBegin() != null) {
            Predicate changeDateBegin = cb.greaterThan(from.get("changeDate"), groupFilter.getChangeDateBegin());
            predicates.add(changeDateBegin);
        }

        if(groupFilter.getChangeDateEnd() != null) {
            Predicate changeDateEnd = cb.lessThan(from.get("changeDate"), groupFilter.getChangeDateEnd());
            predicates.add(changeDateEnd);
        }

        cq.select(from).where(cb.and(predicates.toArray(Predicate[]::new)));
        TypedQuery<GroupEntity> q = entityManager.createQuery(cq);

        // set limit
        if(groupFilter.getLimit() != null)
            q.setMaxResults(groupFilter.getLimit());

        // set start position
        if(groupFilter.getStart() != null)
            q.setFirstResult(groupFilter.getStart());

        // get result
        return groupEntityConverter.convertBtoA(
            q.getResultList()
        );
    }

    /**
     * find group by given id
     *
     * @param groupId group id
     * @return group
     */
    @Override
    public Group findById(GroupId groupId) {
        return groupEntityConverter.convertBtoA(
            findEntityById(groupId.value())
        );
    }

    /**
     * find group entity by given id
     * @param id group id
     */
    private GroupEntity findEntityById(Integer id) {
        return entityManager.find(GroupEntity.class, id);
    }

    /**
     * check if group exists by its name
     *
     * @param group group to check
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
     * @param group group to create
     */
    @Override
    public void createGroup(Group group) {
        entityManager.persist(
            groupEntityConverter.convertAtoB(group)
        );
    }

    /**
     * update existing group
     *
     * @param group group to update
     */
    @Override
    public void updateGroup(Group group) {
        // save to database
        entityManager.merge(
            groupEntityConverter.convertAtoB(group)
        );
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
     * @param groupId group id
     */
    @Override
    public void deleteGroupById(GroupId groupId) {
        entityManager.createQuery("DELETE FROM GroupEntity g WHERE g.id = :id")
                .setParameter("id", groupId.value())
                .executeUpdate();
    }
}
