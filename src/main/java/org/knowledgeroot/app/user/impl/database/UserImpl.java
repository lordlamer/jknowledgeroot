package org.knowledgeroot.app.user.impl.database;

import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.util.List;

@Service
public class UserImpl implements UserDao {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrikaMapper mapper;

    @Override
    public List<User> findAllUsers() {
        List<UserEntity> userEntities = entityManager.createQuery("SELECT u FROM UserEntity u", UserEntity.class)
                .getResultList();

        return mapper.mapAsList(userEntities, User.class);
    }

    @Override
    public User findById(long id) {
        UserEntity userEntity = entityManager.find(UserEntity.class, id);

        return mapper.map(userEntity, User.class);
    }

    @Override
    public boolean isUserExist(User user) {
        return false;
    }

    @Override
    public void saveUser(User user) {

    }

    @Override
    public void updateUser(User user) {

    }

    @Override
    public void deleteAllUsers() {

    }

    @Override
    public void deleteUserById(long id) {

    }
}
