package org.knowledgeroot.app.security.user.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {
    private final UserDao userImpl;
    private final GroupDao groupImpl;

    @RequestMapping("/admin/users")
    public String showUsers(Model model) {
        model.addAttribute(
                "users",
                userImpl.listUsers(
                        new UserFilter()
                )
        );

        return "admin/users";
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer id, Model model) {
        log.info("Delete user with id: {}", id);

        // delete user
        userImpl.deleteUserById(id);

        return ResponseEntity.ok().body("");
    }

    @RequestMapping("/admin/groups")
    public String showGroups(Model model) {

        model.addAttribute(
                "groups",
                groupImpl.listGroups(
                        new GroupFilter()
                )
        );

        return "admin/groups";
    }

    @DeleteMapping("/admin/groups/{id}")
    public ResponseEntity<String> deleteGroup(@PathVariable Integer id, Model model) {
        log.info("Delete group with id: {}", id);

        // delete group
        groupImpl.deleteGroupById(id);

        return ResponseEntity.ok().body("");
    }

    @RequestMapping("/admin/permissions")
    public String showPermissions(Model model) {
        return "admin/permissions";
    }
}
