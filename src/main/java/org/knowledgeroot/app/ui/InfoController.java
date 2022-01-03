package org.knowledgeroot.app.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class InfoController {
    @RequestMapping("/help")
    public String showHelp(Model model) {
        return "info/help";
    }

    @RequestMapping("/about")
    public String showAbout(Model model) {
        return "info/about";
    }
}
