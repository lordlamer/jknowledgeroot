package org.knowledgeroot.app.security.user.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {
    private final UserDao userImpl;

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

    @RequestMapping("/admin/groups")
    public String showGroups(Model model) {
        return "admin/groups";
    }

    @RequestMapping("/admin/permissions")
    public String showPermissions(Model model) {
        return "admin/permissions";
    }
}
