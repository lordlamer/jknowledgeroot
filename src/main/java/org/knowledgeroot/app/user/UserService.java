package org.knowledgeroot.app.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserDao userImpl;

    /**
     * find all users
     * @return
     */
    public List<User> listUsers(UserFilter userFilter) {
        return userImpl.listUsers(userFilter);
    }

    /**
     * find user by given id
     * @param id
     * @return
     */
    public User findById(long id) {
        return userImpl.findById(id);
    }

    /**
     * check if user exists
     * @param user
     * @return
     */
    public boolean isUserExist(User user) {
        return userImpl.isUserExist(user);
    }

    /**
     * create user
     * @param user
     */
    public void createUser(User user) {
        userImpl.createUser(user);
    }

    /**
     * update existing user
     * @param user
     */
    public void updateUser(User user) {
        userImpl.updateUser(user);
    }

    /**
     * delete all users
     */
    public void deleteAllUsers() {
        userImpl.deleteAllUsers();
    }

    /**
     * delete user by id
     * @param id
     */
    public void deleteUserById(long id) {
        userImpl.deleteUserById(id);
    }
}
