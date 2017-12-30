package org.knowledgeroot.app.user.impl.database;

import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
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
    public List<User> findAllUsers() {
        List<UserEntity> userEntities = entityManager.createQuery("SELECT u FROM UserEntity u", UserEntity.class)
                .getResultList();

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
