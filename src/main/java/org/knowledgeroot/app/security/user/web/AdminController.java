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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {
    private final UserDao userImpl;
    private final GroupDao groupImpl;

    @GetMapping("/admin/users")
    public String showUsers(Model model) {
        model.addAttribute(
                "users",
                userImpl.listUsers(
                        new UserFilter()
                )
        );

        return "admin/users";
    }

    @GetMapping("/admin/users/create")
    public String createUser(Model model) {
        return "admin/user/new";
    }

    @GetMapping("/admin/users/{id}")
    public String editUser(@PathVariable Integer id, Model model) {
        model.addAttribute(
                "user",
                userImpl.findById(id)
        );

        return "admin/user/edit";
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer id, Model model) {
        log.info("Delete user with id: {}", id);

        // delete user
        userImpl.deleteUserById(id);

        return ResponseEntity.ok().body("");
    }

    @GetMapping("/admin/groups")
    public String showGroups(Model model) {

        model.addAttribute(
                "groups",
                groupImpl.listGroups(
                        new GroupFilter()
                )
        );

        return "admin/groups";
    }

    @GetMapping("/admin/groups/create")
    public String createGroup(Model model) {
        return "admin/group/new";
    }

    @GetMapping("/admin/groups/{id}")
    public String editGroup(@PathVariable Integer id, Model model) {
        model.addAttribute(
                "group",
                groupImpl.findById(id)
        );

        return "admin/group/edit";
    }

    @DeleteMapping("/admin/groups/{id}")
    public ResponseEntity<String> deleteGroup(@PathVariable Integer id, Model model) {
        log.info("Delete group with id: {}", id);

        // delete group
        groupImpl.deleteGroupById(id);

        return ResponseEntity.ok().body("");
    }

    @GetMapping("/admin/permissions")
    public String showPermissions(Model model) {
        return "admin/permissions";
    }
}
