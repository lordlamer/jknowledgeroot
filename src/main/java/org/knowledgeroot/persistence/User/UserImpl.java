package org.knowledgeroot.persistence.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.knowledgeroot.domain.UserDao;
import org.knowledgeroot.app.api.filter.UserFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        // get criteria builder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> cq = cb.createQuery(User.class);

        Root<User> from = cq.from(User.class);

        List<Predicate> predicates = new ArrayList<>();

        // build restrictions
        if(userFilter.getId() != null) {
            Predicate id = cb.equal(from.get("id"), userFilter.getId());
            predicates.add(id);
        }

        if(userFilter.getFirstName() != null) {
            Predicate firstName = cb.like(from.get("firstName"), "%" + userFilter.getFirstName() + "%");
            predicates.add(firstName);
        }

        if(userFilter.getLastName() != null) {
            Predicate lastName = cb.like(from.get("lastName"), "%" + userFilter.getLastName() + "%");
            predicates.add(lastName);
        }

        if(userFilter.getLogin() != null) {
            Predicate login = cb.like(from.get("login"), "%" + userFilter.getLogin() + "%");
            predicates.add(login);
        }

        if(userFilter.getEmail() != null) {
            Predicate email = cb.like(from.get("email"), "%" + userFilter.getEmail() + "%");
            predicates.add(email);
        }

        if(userFilter.getLanguage() != null) {
            Predicate language = cb.like(from.get("language"), "%" + userFilter.getLanguage() + "%");
            predicates.add(language);
        }

        if(userFilter.getTimezone() != null) {
            Predicate timezone = cb.like(from.get("timezone"), "%" + userFilter.getTimezone() + "%");
            predicates.add(timezone);
        }

        if(userFilter.getDeleted() != null) {
            Predicate deleted = cb.equal(from.get("deleted"), userFilter.getDeleted());
            predicates.add(deleted);
        }

        if(userFilter.getActive() != null) {
            Predicate active = cb.equal(from.get("active"), userFilter.getActive());
            predicates.add(active);
        }

        if(userFilter.getCreatedBy() != null) {
            Predicate createdBy = cb.equal(from.get("createdBy"), userFilter.getCreatedBy());
            predicates.add(createdBy);
        }

        if(userFilter.getChangedBy() != null) {
            Predicate changedBy = cb.equal(from.get("changedBy"), userFilter.getChangedBy());
            predicates.add(changedBy);
        }

        if(userFilter.getTimeStartBegin() != null) {
            Predicate timeStartBegin = cb.greaterThan(from.get("timeStart"), userFilter.getTimeStartBegin());
            predicates.add(timeStartBegin);
        }

        if(userFilter.getTimeStartEnd() != null) {
            Predicate timeStartEnd = cb.lessThan(from.get("timeStart"), userFilter.getTimeStartEnd());
            predicates.add(timeStartEnd);
        }

        if(userFilter.getTimeEndBegin() != null) {
            Predicate timeEndBegin = cb.greaterThan(from.get("timeEnd"), userFilter.getTimeEndBegin());
            predicates.add(timeEndBegin);
        }

        if(userFilter.getTimeEndEnd() != null) {
            Predicate timeEndEnd = cb.lessThan(from.get("timeEnd"), userFilter.getTimeEndEnd());
            predicates.add(timeEndEnd);
        }

        if(userFilter.getCreateDateBegin() != null) {
            Predicate createDateBegin = cb.greaterThan(from.get("createDate"), userFilter.getCreateDateBegin());
            predicates.add(createDateBegin);
        }

        if(userFilter.getCreateDateEnd() != null) {
            Predicate createDateEnd = cb.lessThan(from.get("createDate"), userFilter.getCreateDateEnd());
            predicates.add(createDateEnd);
        }

        if(userFilter.getChangeDateBegin() != null) {
            Predicate changeDateBegin = cb.greaterThan(from.get("changeDate"), userFilter.getChangeDateBegin());
            predicates.add(changeDateBegin);
        }

        if(userFilter.getChangeDateEnd() != null) {
            Predicate changeDateEnd = cb.lessThan(from.get("changeDate"), userFilter.getChangeDateEnd());
            predicates.add(changeDateEnd);
        }

        cq.select(from).where(cb.and(predicates.toArray(Predicate[]::new)));
        TypedQuery<User> q = entityManager.createQuery(cq);

        // set limit
        if(userFilter.getLimit() != null)
            q.setMaxResults(userFilter.getLimit());

        // set start position
        if(userFilter.getStart() != null)
            q.setFirstResult(userFilter.getStart());

        // get result
        return q.getResultList();
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
