package org.knowledgeroot.app.user.impl.database;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.UserDao;
import org.knowledgeroot.app.user.UserFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class UserImpl implements UserDao {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrikaMapper mapper;

    /**
     * find all users
     * @return
     */
    @Override
    public List<User> listUsers(UserFilter userFilter) {
        // get current session
        Session session = entityManager.unwrap(org.hibernate.Session.class);

        Criteria userCriteria = session.createCriteria(UserEntity.class, "u");

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
        List<UserEntity> userEntities = Collections.checkedList(userCriteria.list(), UserEntity.class);

        return mapper.mapAsList(userEntities, User.class);
    }

    /**
     * find user by given id
     * @param id
     * @return
     */
    @Override
    public User findById(long id) {
        UserEntity userEntity = findEntityById(id);

        return mapper.map(userEntity, User.class);
    }

    /**
     * find user entity by given id
     * @param id
     * @return
     */
    private UserEntity findEntityById(long id) {
        return entityManager.find(UserEntity.class, id);
    }

    /**
     * check if user exists
     * @param user
     * @return
     */
    @Override
    public boolean isUserExist(User user) {
        boolean found = false;

        List<UserEntity> userEntities = entityManager.createQuery(
                "SELECT u FROM UserEntity u WHERE u.login = :login",
                UserEntity.class)
                .setParameter("login", user.getLogin())
                .getResultList();

        if(userEntities.size() == 1)
            found = true;

        return found;
    }

    /**
     * create user from domain object
     * @param user
     */
    @Override
    public void createUser(User user) {
        UserEntity userEntity = mapper.map(user, UserEntity.class);

        entityManager.persist(userEntity);
    }

    /**
     * update existing user
     * @param user
     */
    @Override
    public void updateUser(User user) {
        // get existing entity
        UserEntity userEntity = findEntityById(user.getId());

        // map fields
        userEntity.setLogin(user.getLogin());
        userEntity.setPassword(user.getPassword());
        userEntity.setFirstName(user.getFirstName());
        userEntity.setLastName(user.getLastName());
        userEntity.setEmail(user.getEmail());
        userEntity.setActive(user.getActive());
        userEntity.setDeleted(user.getDeleted());
        userEntity.setCreatedBy(user.getCreatedBy());
        userEntity.setCreateDate(user.getCreateDate());
        userEntity.setChangedBy(user.getChangedBy());
        userEntity.setChangeDate(user.getChangeDate());
        userEntity.setLanguage(user.getLanguage());
        userEntity.setTimezone(user.getTimezone());
        userEntity.setTimeStart(user.getTimeStart());
        userEntity.setTimeEnd(user.getTimeEnd());

        // save to database
        entityManager.persist(userEntity);
    }

    /**
     * delete all users
     */
    @Override
    public void deleteAllUsers() {
        entityManager.createQuery("DELETE FROM UserEntity").executeUpdate();
    }

    /**
     * delete user by id
     * @param id
     */
    @Override
    public void deleteUserById(long id) {
        entityManager.createQuery("DELETE FROM UserEntity u WHERE u.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
