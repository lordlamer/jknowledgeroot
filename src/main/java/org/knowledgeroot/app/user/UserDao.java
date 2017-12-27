package org.knowledgeroot.app.user;

import java.util.List;

public interface UserDao {
    List<User> findAllUsers();
    User findById(long id);
    boolean isUserExist(User user);
    void saveUser(User user);
    void updateUser(User user);
    void deleteAllUsers();
    void deleteUserById(long id);
}
