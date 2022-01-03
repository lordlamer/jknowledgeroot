package org.knowledgeroot.app.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SidebarController {
    @RequestMapping("/ui/sidebar")
    public String index(Model model) {
        return "sidebar";
    }
}
