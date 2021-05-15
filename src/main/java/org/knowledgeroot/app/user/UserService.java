package org.knowledgeroot.app.user;

import lombok.AllArgsConstructor;
import org.knowledgeroot.app.user.impl.database.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserService {
    private final UserDao userImpl;

    /**
     * find all users
     */
    public List<User> listUsers(UserFilter userFilter) {
        return userImpl.listUsers(userFilter);
    }

    /**
     * find user by given id
     * @param id user id
     */
    public User findById(long id) {
        return userImpl.findById(id);
    }

    /**
     * check if user exists
     * @param user user to check
     */
    public boolean isUserExist(User user) {
        return userImpl.isUserExist(user);
    }

    /**
     * create user
     * @param user user to create
     */
    public void createUser(User user) {
        userImpl.createUser(user);
    }

    /**
     * update existing user
     * @param user user to update
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
     * @param id user id
     */
    public void deleteUserById(long id) {
        userImpl.deleteUserById(id);
    }
}
