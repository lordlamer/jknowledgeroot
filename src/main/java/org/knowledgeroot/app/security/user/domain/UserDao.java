package org.knowledgeroot.app.security.user.domain;

import org.knowledgeroot.app.security.user.api.filter.UserFilter;

import java.util.List;

/**
 * User data access object
 */
public interface UserDao {
    /**
     * find all users by given filter
     * @return list of users
     */
    List<User> listUsers(UserFilter userFilter);

    /**
     * find user by given id
     * @param userId user id
     * @return user
     */
    User findById(UserId userId);

    /**
     * check if user exists
     * @param user user object
     * @return true if user exists
     */
    boolean isUserExist(User user);

    /**
     * create user from domain object
     * @param user user object
     */
    void createUser(User user);

    /**
     * update existing user
     * @param user user object
     */
    void updateUser(User user);

    /**
     * delete all users
     */
    void deleteAllUsers();

    /**
     * delete user by given id
     * @param userId user id
     */
    void deleteUserById(UserId userId);
}
