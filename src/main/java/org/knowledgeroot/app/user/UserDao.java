package org.knowledgeroot.app.user;

import org.knowledgeroot.app.user.impl.database.User;

import java.util.List;

public interface UserDao {
    /**
     * find all users
     * @return
     */
    List<User> listUsers(UserFilter userFilter);

    /**
     * find user by given id
     * @param id
     * @return
     */
    User findById(long id);

    /**
     * check if user exists
     * @param user
     * @return
     */
    boolean isUserExist(User user);

    /**
     * create user from domain object
     * @param user
     */
    void createUser(User user);

    /**
     * update existing user
     * @param user
     */
    void updateUser(User user);

    /**
     * delete all users
     */
    void deleteAllUsers();

    /**
     * delete user by given id
     * @param id
     */
    void deleteUserById(long id);
}
