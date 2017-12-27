package org.knowledgeroot.app.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserDao userImpl;

    public List<User> findAllUsers() {
        return userImpl.findAllUsers();
    }

    public User findById(long id) {
        return userImpl.findById(id);
    }

    public boolean isUserExist(User user) {
        return userImpl.isUserExist(user);
    }

    public void saveUser(User user) {
        userImpl.saveUser(user);
    }

    public void updateUser(User user) {
        userImpl.updateUser(user);
    }

    public void deleteAllUsers() {
        userImpl.deleteAllUsers();
    }

    public void deleteUserById(long id) {
        userImpl.deleteUserById(id);
    }
}
