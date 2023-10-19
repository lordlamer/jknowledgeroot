package org.knowledgeroot.app.security.user.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AdminController {
    @RequestMapping("/admin/users")
    public String showUsers(Model model) {
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
