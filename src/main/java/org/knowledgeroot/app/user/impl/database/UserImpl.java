package org.knowledgeroot.app.user.impl.database;

import lombok.AllArgsConstructor;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.user.UserDao;
import org.knowledgeroot.app.user.UserFilter;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class UserImpl implements UserDao {
    private final EntityManager entityManager;

    /**
     * find all users
     * @return
     */
    @Override
    public List<User> listUsers(UserFilter userFilter) {
        // get current session
        Session session = entityManager.unwrap(org.hibernate.Session.class);

        Criteria userCriteria = session.createCriteria(User.class, "u");

        // build restrictions
        if(userFilter.getId() != null)
            userCriteria.add(Restrictions.eq("u.id", userFilter.getId()));

        if(userFilter.getFirstName() != null)
            userCriteria.add(Restrictions.like("u.firstName", "%" + userFilter.getFirstName() + "%"));

        if(userFilter.getLastName() != null)
            userCriteria.add(Restrictions.like("u.lastName", "%" + userFilter.getLastName() + "%"));

        if(userFilter.getLogin() != null)
            userCriteria.add(Restrictions.like("u.login", "%" + userFilter.getLogin() + "%"));

        if(userFilter.getEmail() != null)
            userCriteria.add(Restrictions.like("u.email", "%" + userFilter.getEmail() + "%"));

        if(userFilter.getLanguage() != null)
            userCriteria.add(Restrictions.like("u.language", "%" + userFilter.getLanguage() + "%"));

        if(userFilter.getTimezone() != null)
            userCriteria.add(Restrictions.like("u.timezone", "%" + userFilter.getTimezone() + "%"));

        if(userFilter.getDeleted() != null)
            userCriteria.add(Restrictions.eq("u.deleted", userFilter.getDeleted()));

        if(userFilter.getActive() != null)
            userCriteria.add(Restrictions.eq("u.active", userFilter.getActive()));

        if(userFilter.getCreatedBy() != null)
            userCriteria.add(Restrictions.eq("u.createdBy", userFilter.getCreatedBy()));

        if(userFilter.getChangedBy() != null)
            userCriteria.add(Restrictions.eq("u.changedBy", userFilter.getChangedBy()));

        if(userFilter.getTimeStartBegin() != null)
            userCriteria.add(Restrictions.ge("u.timeStart", userFilter.getTimeStartBegin()));

        if(userFilter.getTimeStartEnd() != null)
            userCriteria.add(Restrictions.le("u.timeStart", userFilter.getTimeStartEnd()));

        if(userFilter.getTimeEndBegin() != null)
            userCriteria.add(Restrictions.ge("u.timeEnd", userFilter.getTimeEndBegin()));

        if(userFilter.getTimeEndEnd() != null)
            userCriteria.add(Restrictions.le("u.timeEnd", userFilter.getTimeEndEnd()));

        if(userFilter.getCreateDateBegin() != null)
            userCriteria.add(Restrictions.ge("u.createDate", userFilter.getCreateDateBegin()));

        if(userFilter.getCreateDateEnd() != null)
            userCriteria.add(Restrictions.le("u.createDate", userFilter.getCreateDateEnd()));

        if(userFilter.getChangeDateBegin() != null)
            userCriteria.add(Restrictions.ge("u.changeDate", userFilter.getChangeDateBegin()));

        if(userFilter.getChangeDateEnd() != null)
            userCriteria.add(Restrictions.le("u.changeDate", userFilter.getChangeDateEnd()));

        // set limit
        if(userFilter.getLimit() != null)
            userCriteria.setMaxResults(userFilter.getLimit());

        // set start position
        if(userFilter.getStart() != null)
            userCriteria.setFirstResult(userFilter.getStart());

        // get result
        return Collections.checkedList(userCriteria.list(), User.class);
    }

    /**
     * find user by given id
     * @param id user id
     */
    @Override
    public User findById(long id) {
        return findEntityById(id);
    }

    /**
     * find user entity by given id
     * @param id user id
     */
    private User findEntityById(long id) {
        return entityManager.find(User.class, id);
    }

    /**
     * check if user exists
     * @param user user to check
     */
    @Override
    public boolean isUserExist(User user) {
        boolean found = false;

        List<User> userEntities = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.login = :login",
                User.class)
                .setParameter("login", user.getLogin())
                .getResultList();

        if(userEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create user from domain object
     * @param user user to create
     */
    @Override
    public void createUser(User user) {
        entityManager.persist(user);
    }

    /**
     * update existing user
     * @param user user object to update
     */
    @Override
    public void updateUser(User user) {
        // save to database
        entityManager.merge(user);
    }

    /**
     * delete all users
     */
    @Override
    public void deleteAllUsers() {
        entityManager.createQuery("DELETE FROM User").executeUpdate();
    }

    /**
     * delete user by id
     * @param id user id
     */
    @Override
    public void deleteUserById(long id) {
        entityManager.createQuery("DELETE FROM User u WHERE u.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
