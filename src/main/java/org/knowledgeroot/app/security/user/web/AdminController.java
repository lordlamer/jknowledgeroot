package org.knowledgeroot.app.security.user.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.auth.PasswordHasher;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {
    private final UserDao userImpl;
    private final GroupDao groupImpl;

    /**
     * get all users
     */
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

    /**
     * create new user form
     */
    @GetMapping("/admin/users/create")
    public String createUser(Model model) {
        return "admin/user/new";
    }

    @PostMapping("/admin/users")
    public String saveUser(UserDto user) {
        log.debug("Create new user: {}", user);

        User newUser = User.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .login(user.getLogin())
                .email(user.getEmail())
                .password(
                        PasswordHasher.hash(
                                user.getPassword(),
                                PasswordHasher.HASH_METHOD.SHA256,
                                1000
                        )
                )
                .language("en_US")
                .timezone("UTC")
                .timeStart(LocalDateTime.now())
                .timeEnd(LocalDateTime.now())
                .active(true)
                .createdBy(1)
                .createDate(LocalDateTime.now())
                .changedBy(1)
                .changeDate(LocalDateTime.now())
                .deleted(false)
                .build();

        userImpl.createUser(newUser);

        return "redirect:/admin/users";
    }

    /**
     * show user edit form
     * @param id user id
     */
    @GetMapping("/admin/users/{id}")
    public String editUser(@PathVariable Integer id, Model model) {
        model.addAttribute(
                "user",
                userImpl.findById(new UserId(id))
        );

        return "admin/user/edit";
    }

    /**
     * delete user
     * @param id user id
     */
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer id, Model model) {
        log.info("Delete user with id: {}", id);

        // delete user
        userImpl.deleteUserById(new UserId(id));

        return ResponseEntity.ok().body("");
    }

    /**
     * show all groups
     */
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

    /**
     * create new group form
     */
    @GetMapping("/admin/groups/create")
    public String createGroup(Model model) {
        return "admin/group/new";
    }

    /**
     * save new group and redirect to group list
     * @param group group object
     */
    @PostMapping("/admin/groups")
    public String saveGroup(GroupDto group) {
        log.debug("Create new group: {}", group);

        Group newGroup = Group.builder()
                .name(group.getName())
                .description(group.getDescription())
                .active(true)
                .createdBy(1)
                .createDate(LocalDateTime.now())
                .changedBy(1)
                .changeDate(LocalDateTime.now())
                .deleted(false)
                .build();

        groupImpl.createGroup(newGroup);

        return "redirect:/admin/groups";
    }

    @PostMapping("/admin/groups/{id}")
    public String updateGroup(@PathVariable Integer id, GroupDto groupDto) {
        // load group
        Group group = groupImpl.findById(new GroupId(id));

        // update group
        group.setName(groupDto.getName());
        group.setDescription(groupDto.getDescription());

        groupImpl.updateGroup(group);

        log.debug("Group updated: {}", group);

        return "redirect:/admin/groups";
    }

    /**
     * show group edit form
     * @param id group id
     */
    @GetMapping("/admin/groups/{id}")
    public String editGroup(@PathVariable Integer id, Model model) {
        model.addAttribute(
                "group",
                groupImpl.findById(new GroupId(id))
        );

        return "admin/group/edit";
    }

    /**
     * delete group
     * @param id group id
     */
    @DeleteMapping("/admin/groups/{id}")
    public ResponseEntity<String> deleteGroup(@PathVariable Integer id, Model model) {
        log.info("Delete group with id: {}", id);

        // delete group
        groupImpl.deleteGroupById(new GroupId(id));

        return ResponseEntity.ok().body("");
    }

    /**
     * show permissions
     */
    @GetMapping("/admin/permissions")
    public String showPermissions(Model model) {
        return "admin/permissions";
    }
}
